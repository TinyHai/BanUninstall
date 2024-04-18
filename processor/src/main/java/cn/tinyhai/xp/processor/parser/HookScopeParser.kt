package cn.tinyhai.xp.processor.parser

import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.logger.XPLogger
import cn.tinyhai.xp.processor.codegen.HookScopeInfo
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ksp.toClassName
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookScopeParser(private val resolver: Resolver, private val logger: KSPLogger) :
    Parser<HookScopeInfo> {
    override fun parse(): List<HookScopeInfo> {
        return getAllScopeInfo().toList()
    }

    private fun getAllScopeInfo(): Sequence<HookScopeInfo> {
        return getFileScopeInfo() + getClassScopeInfo()
    }

    @OptIn(KspExperimental::class)
    private fun getFileScopeInfo(): Sequence<HookScopeInfo> {
        return getFileWithHookScope().map {
            val hookScope = it.getAnnotationsByType(HookScope::class).single()
            val scopeName = hookScope.scopeName.ifBlank { it.fileName }
            var hookerGate: MemberName? = null
            var initiate: MemberName? = null
            val packageName = it.packageName.getQualifier()
            val allFunctions = it.declarations.filterIsInstance<KSFunctionDeclaration>().onEach {
                if (isHookerGate(it)) {
                    hookerGate = MemberName(packageName, it.simpleName.getShortName())
                }
                if (isInitiate(it)) {
                    initiate = MemberName(packageName, it.simpleName.getShortName())
                }
            }
            val injectHookerProperty =
                it.declarations.filterIsInstance<KSPropertyDeclaration>().filter { it.isPublic() }
                    .filter { it.isAnnotationPresent(InjectHooker::class) }
                    .firstOrNull()
            val injectHookerMemberName = injectHookerProperty?.let {
                MemberName(it.packageName.getQualifier(), it.simpleName.getShortName())
            }
            val hookerInfos =
                HookerInfoParser(hookScope.isUnhookable, allFunctions, resolver).parse()
            HookScopeInfo(
                scopeName = scopeName,
                isUnhookable = hookScope.isUnhookable,
                hasLogger = false,
                targetPackageName = hookScope.targetPackageName,
                targetProcessName = hookScope.targetProcessName,
                autoRegister = hookScope.autoRegister,
                hookerGate = hookerGate,
                initiate = initiate,
                scopePackageName = packageName,
                scopeClassName = null,
                injectMemberName = injectHookerMemberName,
                hookerInfos = hookerInfos
            )
        }
    }

    @OptIn(KspExperimental::class)
    private fun getClassScopeInfo(): Sequence<HookScopeInfo> {
        return resolver.getClassWithHookScope().map {
            val hookScope = it.getAnnotationsByType(HookScope::class).single()
            val scopeName = hookScope.scopeName.ifBlank { it.simpleName.getShortName() }
            var hookerGate: MemberName? = null
            var initiate: MemberName? = null
            val scopeClassName = it.toClassName()
            val allFunctions = it.getDeclaredFunctions().onEach {
                if (isHookerGate(it)) {
                    hookerGate = scopeClassName.member(it.simpleName.getShortName())
                }
                if (isInitiate(it)) {
                    initiate = scopeClassName.member(it.simpleName.getShortName())
                }
            }
            val hookerInfos =
                HookerInfoParser(hookScope.isUnhookable, allFunctions, resolver).parse()
            val hasLogger = it.primaryConstructor?.let { hasLoggerParam(it) } ?: false

            val injectHookerProperty = it.getDeclaredProperties().filter { it.isPublic() }
                .filter { it.isAnnotationPresent(InjectHooker::class) }
                .firstOrNull()
            val injectHookerMemberName = injectHookerProperty?.let {
                scopeClassName.member(it.simpleName.getShortName())
            }

            HookScopeInfo(
                scopeName = scopeName,
                isUnhookable = hookScope.isUnhookable,
                hasLogger = hasLogger,
                targetPackageName = hookScope.targetPackageName,
                targetProcessName = hookScope.targetProcessName,
                autoRegister = hookScope.autoRegister,
                hookerGate = hookerGate,
                initiate = initiate,
                scopePackageName = scopeClassName.packageName,
                scopeClassName = scopeClassName,
                injectMemberName = injectHookerMemberName,
                hookerInfos = hookerInfos
            )
        }
    }

    @OptIn(KspExperimental::class)
    private fun isHookerGate(function: KSFunctionDeclaration): Boolean {
        if (!function.isAnnotationPresent(HookerGate::class)) {
            return false
        }
        val returnType = function.returnType?.resolve()
        if (returnType?.isAssignableFrom<Boolean>(resolver) != true) {
            return false
        }
        val parameterSize = function.parameters.size
        if (parameterSize != 1) {
            return false
        }
        val parameterType = function.parameters[0].type.resolve()
        if (!parameterType.isAssignableFrom<String>(resolver)) {
            return false
        }
        return true
    }

    @OptIn(KspExperimental::class)
    private fun isInitiate(function: KSFunctionDeclaration): Boolean {
        if (!function.isAnnotationPresent(Initiate::class)) {
            return false
        }
        val parameterSize = function.parameters.size
        if (parameterSize != 1) {
            return false
        }
        val parameterType = function.parameters[0].type.resolve()
        if (!parameterType.isAssignableFrom<XC_LoadPackage.LoadPackageParam>(resolver)) {
            return false
        }
        return true
    }

    private fun hasLoggerParam(function: KSFunctionDeclaration): Boolean {
        if (!function.isPublic()) {
            return false
        }
        if (function.parameters.size != 1) {
            return false
        }
        val param = function.parameters[0].type.resolve()
        return param.isAssignableFrom<XPLogger>(resolver)
    }

    private inline fun <reified T> KSType.isAssignableFrom(resolver: Resolver): Boolean {
        val classDeclaration = requireNotNull(resolver.getClassDeclarationByName<T>()) {
            "Unable to resolve ${KSClassDeclaration::class.simpleName} for type ${T::class.simpleName}"
        }
        return isAssignableFrom(classDeclaration.asStarProjectedType())
    }

    private fun Resolver.getClassWithHookScope(): Sequence<KSClassDeclaration> {
        return getSymbolsWithAnnotation(HookScope::class.qualifiedName.orEmpty()).filterIsInstance<KSClassDeclaration>()
            .filter {
                it.classKind == ClassKind.CLASS
            }
            .filter {
                !it.modifiers.contains(Modifier.ABSTRACT)
            }
    }

    private fun getFileWithHookScope() =
        resolver.getSymbolsWithAnnotation(MethodHooker::class.qualifiedName.orEmpty())
            .filterIsInstance<KSFile>()
}