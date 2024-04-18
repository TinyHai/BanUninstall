package cn.tinyhai.xp.processor.codegen

import cn.tinyhai.xp.annotation.HookType
import cn.tinyhai.xp.hook.BaseOneshotHooker
import cn.tinyhai.xp.hook.BaseUnhookableHooker
import cn.tinyhai.xp.hook.Hooker
import cn.tinyhai.xp.hook.HookerHelper
import cn.tinyhai.xp.hook.logger.XPLogger
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GenerateHookScope(
    private val packageName: String,
    private val hookScopeInfo: HookScopeInfo,
    private val logger: KSPLogger
) : CodeGen {
    override fun start(): FileSpec {
        return generateHookScope(hookScopeInfo).also { logger.info(it.toString()) }
    }

    private fun generateHookScope(info: HookScopeInfo): FileSpec {
        val fileName = info.scopeName + "Impl"
        val baseClassName: ClassName = if (info.isUnhookable) {
            BaseUnhookableHooker::class.asClassName()
        } else {
            BaseOneshotHooker::class.asClassName()
        }
        val _scope = info.scopeClassName?.let {
            PropertySpec
                .builder("_scope", it)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .build()
        }

        val funSpecs = buildOverrideFunctions(info, _scope, baseClassName, info.initiate)
        val loggerClass = XPLogger::class.asClassName()

        return FileSpec.builder(packageName, fileName)
            .addType(
                TypeSpec.classBuilder(fileName)
                    .superclass(baseClassName)
                    .primaryConstructor(buildPrimaryConstructor(loggerClass))
                    .addSuperclassConstructorParameter("logger")
                    .addProperty(
                        PropertySpec.builder(
                            "name",
                            String::class,
                            KModifier.OVERRIDE,
                            KModifier.FINAL
                        )
                            .initializer("%S", info.scopeName)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "targetPackageName",
                            String::class,
                            KModifier.OVERRIDE,
                            KModifier.FINAL
                        ).initializer("%S", info.targetPackageName).build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "targetProcessName",
                            String::class,
                            KModifier.OVERRIDE,
                            KModifier.FINAL
                        ).initializer("%S", info.targetProcessName).build()
                    )
                    .apply {
                        _scope?.let {
                            addProperty(it)
                        }
                        buildInitBlock(_scope, hookScopeInfo)?.let {
                            addInitializerBlock(it)
                        }
                    }
                    .addFunctions(
                        funSpecs
                    )
                    .build()
            ).build()
    }

    private fun buildPrimaryConstructor(loggerClass: ClassName): FunSpec {
        return FunSpec
            .constructorBuilder()
            .addParameter(
                ParameterSpec
                    .builder("logger", loggerClass.copy())
                    .defaultValue("%T", loggerClass)
                    .build()
            )
            .build()
    }

    private fun buildInitBlock(scopeSpec: PropertySpec?, info: HookScopeInfo): CodeBlock? {
        if (scopeSpec == null && info.injectMemberName == null) {
            return null
        }
        return buildCodeBlock {
            if (scopeSpec != null) {
                addStatement(
                    if (info.hasLogger) "%N = %T(logger)" else "%N = %T()",
                    scopeSpec,
                    info.scopeClassName
                )
            }
            if (info.injectMemberName != null) {
                if (scopeSpec != null) {
                    addStatement("%N.%N = this", scopeSpec, info.injectMemberName)
                } else {
                    addStatement("%N = this", info.injectMemberName)
                }
            }
        }
    }

    private fun buildOverrideFunctions(
        info: HookScopeInfo,
        scopeProperty: PropertySpec?,
        baseClassName: ClassName,
        initiate: MemberName?
    ): List<FunSpec> {
        val functions = mutableListOf<FunSpec>()
        when (baseClassName.simpleName) {
            "BaseOneshotHooker" -> {
                functions.add(
                    buildOneshotOverrideFun(
                        info.hookerGate,
                        scopeProperty,
                        info.scopePackageName,
                        info.scopeClassName,
                        baseClassName.member("createOneshotHook"),
                        info.hookerInfos
                    )
                )
            }

            "BaseUnhookableHooker" -> {
                functions.add(
                    buildOneshotOverrideFun(
                        info.hookerGate,
                        scopeProperty,
                        info.scopePackageName,
                        info.scopeClassName,
                        baseClassName.member("createOneshotHook"),
                        info.hookerInfos
                    )
                )
                functions.add(
                    buildCreateHooks(
                        info.hookerGate,
                        scopeProperty,
                        info.scopePackageName,
                        info.scopeClassName,
                        baseClassName.member("createHooks"),
                        info.hookerInfos
                    )
                )
            }
        }
        initiate?.let {
            functions.add(
                buildInit(scopeProperty, it)
            )
        }
        return functions
    }

    private fun buildOneshotOverrideFun(
        hookerGate: MemberName?,
        scopeSpec: PropertySpec?,
        scopePackageName: String,
        scopeClassName: ClassName?,
        createOneshotHook: MemberName,
        hookInfo: List<HookerInfo>
    ): FunSpec {
        val lpType = XC_LoadPackage.LoadPackageParam::class.asClassName()
        val lpSpec = ParameterSpec.builder("lp", lpType).build()
        val builder = FunSpec
            .builder(createOneshotHook.simpleName)
            .addModifiers(KModifier.OVERRIDE, KModifier.FINAL)
            .addParameter(lpSpec)
        val hookerHelper = HookerHelper::class.asClassName()
        builder.apply {
            for (info in hookInfo) {
                if (!info.isOneshot) {
                    continue
                }
                addCode(
                    CodeBlock.builder()
                        .buildWithHookerGateIfNeeded(scopeSpec, hookerGate, info.hookerId) {
                            if (info.minSdk != 0 || info.maxSdk != Int.MAX_VALUE) {
                                val buildVersionClass =
                                    ClassName("android.os", "Build").nestedClass("VERSION")
                                beginControlFlow(
                                    "if (%T.%N in %L until %L) {",
                                    buildVersionClass,
                                    buildVersionClass.member("SDK_INT"),
                                    info.minSdk,
                                    info.maxSdk
                                )
                            }
                            if (info.unhookable) {
                                buildUnhookable(
                                    info,
                                    scopePackageName,
                                    hookerHelper,
                                    scopeClassName,
                                    scopeSpec,
                                    lpSpec,
                                    lpType
                                )
                            } else {
                                buildCallback(
                                    info,
                                    scopePackageName,
                                    scopeClassName,
                                    scopeSpec
                                )
                                addStatement(
                                    "%N(%S, %N.%N, %S, callback)",
                                    hookerHelper.member("findAndHookFirst"),
                                    info.targetClassName,
                                    lpSpec,
                                    lpType.member("classLoader"),
                                    info.targetMethod,
                                )
                            }
                            if (info.minSdk != 0 || info.maxSdk != Int.MAX_VALUE) {
                                endControlFlow()
                            }
                        }
                )
            }
        }
        return builder.build()
    }

    private fun CodeBlock.Builder.buildUnhookable(
        info: HookerInfo,
        scopePackageName: String,
        hookerHelper: ClassName,
        scopeClassName: ClassName?,
        scopeSpec: PropertySpec?,
        lpSpec: ParameterSpec,
        lpType: ClassName,
    ) {
        val classLoader = lpType.member("classLoader")
        val unhookClassName = Unhook::class.asClassName()
        val unhookMethod = unhookClassName.member("unhook")
        addStatement("val unhookArr = arrayOf<%T>(null)", unhookClassName.copy(nullable = true))
        addStatement(
            "val unhook: () -> Unit = { unhookArr[0]?.%N() }",
            unhookMethod
        )
        buildCallback(
            info,
            scopePackageName,
            scopeClassName,
            scopeSpec
        )
        addStatement(
            "unhookArr[0] = %N(%S, %N.%N, %S, callback)",
            hookerHelper.member("findAndHookFirst"),
            info.targetClassName,
            lpSpec,
            classLoader,
            info.targetMethod,
        )
    }

    private fun CodeBlock.Builder.buildWithHookerGateIfNeeded(
        scopeSpec: PropertySpec?,
        hookerGate: MemberName?,
        hookerId: String?,
        block: CodeBlock.Builder.() -> Unit,
    ): CodeBlock {
        when {
            hookerGate == null -> {
                block()
            }

            hookerId == null -> {
                block()
            }

            else -> {
                if (scopeSpec != null) {
                    beginControlFlow("if (%N.%N(%S)) {", scopeSpec, hookerGate, hookerId)
                } else {
                    beginControlFlow("if (%N(%S))", hookerGate, hookerId)
                }
                block()
                endControlFlow()
            }
        }
        return build()
    }

    private fun buildCreateHooks(
        hookerGate: MemberName?,
        scopeSpec: PropertySpec?,
        scopePackageName: String,
        scopeClassName: ClassName?,
        createHooks: MemberName,
        hookInfo: List<HookerInfo>
    ): FunSpec {
        val paramType = XC_LoadPackage.LoadPackageParam::class.asTypeName()
        val paramSpec = ParameterSpec.builder("lp", paramType).build()
        val mutableListOf = MemberName("kotlin.collections", "mutableListOf")
        val classLoader = paramType.member("classLoader")
        val hookHelper = HookerHelper::class.asClassName()
        val unhook = Unhook::class.asClassName()
        val builder = FunSpec
            .builder(createHooks.simpleName)
            .addModifiers(KModifier.OVERRIDE, KModifier.FINAL)
            .addParameter(paramSpec)
            .returns(List::class.asClassName().plusParameter(unhook))
            .addStatement("val unhooks = %M<%T>()", mutableListOf, unhook)
        builder.apply {
            for (info in hookInfo) {
                if (info.isOneshot) {
                    continue
                }
                addCode(
                    CodeBlock.builder()
                        .buildWithHookerGateIfNeeded(scopeSpec, hookerGate, info.hookerId) {
                            if (info.minSdk != 0 || info.maxSdk != Int.MAX_VALUE) {
                                val buildVersionClass =
                                    ClassName("android.os", "Build").nestedClass("VERSION")
                                beginControlFlow(
                                    "if (%T.%N in %L until %L) {",
                                    buildVersionClass,
                                    buildVersionClass.member("SDK_INT"),
                                    info.minSdk,
                                    info.maxSdk
                                )
                            }
                            buildCallback(
                                info,
                                scopePackageName,
                                scopeClassName,
                                scopeSpec
                            )
                            addStatement(
                                "unhooks.%N(%S, %N.%N, %S, callback)",
                                hookHelper.member("hookAndAddFirst"),
                                info.targetClassName,
                                paramSpec,
                                classLoader,
                                info.targetMethod
                            )
                            if (info.minSdk != 0 || info.maxSdk != Int.MAX_VALUE) {
                                endControlFlow()
                            }
                        }
                )
            }
            addStatement("return unhooks")
        }
        return builder.build()
    }

    private fun buildInit(scopeProperty: PropertySpec?, initiate: MemberName): FunSpec {
        return FunSpec.builder("init")
            .addModifiers(KModifier.OVERRIDE, KModifier.FINAL)
            .addParameter("lp", XC_LoadPackage.LoadPackageParam::class)
            .addCode(
                buildCodeBlock {
                    if (scopeProperty != null) {
                        add("%N.%N(lp)", scopeProperty, initiate)
                    } else {
                        add("%N(lp)", initiate)
                    }
                }
            )
            .build()
    }

    private fun CodeBlock.Builder.buildCallback(
        info: HookerInfo,
        packageName: String,
        scopeClassName: ClassName?,
        scopeSpec: PropertySpec?
    ) {
        val beforeMethod = MemberName("cn.tinyhai.xp.hook.callback", "beforeMethod")
        val afterMethod = MemberName("cn.tinyhai.xp.hook.callback", "afterMethod")
        val hookerMember =
            scopeClassName?.member(info.hookerMethod) ?: MemberName(packageName, info.hookerMethod)

        beginControlFlow(
            "val callback = %M { param ->",
            if (info.hookType != HookType.AfterMethod) beforeMethod else afterMethod
        )

        when (info.hookType) {
            HookType.BeforeMethod -> {
                if (scopeSpec != null) {
                    if (info.unhookable) {
                        addStatement("%N.%N(param, unhook)", scopeSpec, hookerMember)
                    } else {
                        addStatement("%N.%N(param)", scopeSpec, hookerMember)
                    }
                } else {
                    if (info.unhookable) {
                        addStatement("%N(param, unhook)", hookerMember)
                    } else {
                        addStatement("%N(param)", hookerMember)
                    }
                }
            }

            HookType.ReplaceMethod -> {
                beginControlFlow("try {")
                if (scopeSpec != null) {
                    if (info.unhookable) {
                        addStatement("param.result = %N.%N(param, unhook)", scopeSpec, hookerMember)
                    } else {
                        addStatement("param.result = %N.%N(param)", scopeSpec, hookerMember)
                    }
                } else {
                    if (info.unhookable) {
                        addStatement("param.result = %N(param, unhook)", hookerMember)
                    } else {
                        addStatement("param.result = %N(param)", hookerMember)
                    }
                }
                nextControlFlow("catch (th: Throwable)")
                addStatement("param.throwable = th")
                endControlFlow()
            }

            HookType.AfterMethod -> {
                if (scopeSpec != null) {
                    if (info.unhookable) {
                        addStatement("%N.%N(param, unhook)", scopeSpec, hookerMember)
                    } else {
                        addStatement("%N.%N(param)", scopeSpec, hookerMember)
                    }
                } else {
                    if (info.unhookable) {
                        addStatement("%N(param, unhook)", hookerMember)
                    } else {
                        addStatement("%N(param)", hookerMember)
                    }
                }
            }
        }

        endControlFlow()
    }
}