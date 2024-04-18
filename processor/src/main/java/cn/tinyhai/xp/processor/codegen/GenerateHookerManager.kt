package cn.tinyhai.xp.processor.codegen

import cn.tinyhai.xp.hook.Hooker
import cn.tinyhai.xp.hook.logger.XPLogger
import cn.tinyhai.xp.hook.logger.XPLoggerWrapper
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GenerateHookerManager(
    private val fileSpecs: List<FileSpec>,
    private val packageName: String,
) : CodeGen {
    override fun start(): FileSpec {
        val mutableListOf = MemberName("kotlin.collections", "mutableListOf")
        val hookerClassName = Hooker::class.asClassName()
        val mutableListWithTypeParam =
            ClassName("kotlin.collections", "MutableList").plusParameter(hookerClassName)
        val allHookers =
            PropertySpec.builder("allHookers", mutableListWithTypeParam, KModifier.PRIVATE)
                .initializer("%N<%T>()", mutableListOf, hookerClassName)
                .build()
        val loggerClassName = XPLogger::class.asClassName()
        val loggerParam = ParameterSpec.builder("logger", loggerClassName)
            .defaultValue("%T", loggerClassName)
            .build()
        val loggerWrapperProperty = buildLoggerWrapperProperty(loggerParam)
        val loggerProperty = buildLoggerProperty(loggerWrapperProperty)
        return FileSpec.builder(packageName, "HookerManager")
            .addType(
                TypeSpec.classBuilder("HookerManager")
                    .primaryConstructor(
                        FunSpec.constructorBuilder().addParameter(loggerParam).build()
                    )
                    .addProperty(
                        allHookers
                    )
                    .addProperty(loggerWrapperProperty)
                    .addProperty(loggerProperty)
                    .addInitializerBlock(
                        buildInitBlock(allHookers, loggerWrapperProperty, fileSpecs)
                    )
                    .addFunction(
                        buildStartHook(allHookers, hookerClassName)
                    )
                    .build()
            )
            .build()
    }

    private fun buildInitBlock(
        allHookers: PropertySpec,
        loggerWrapper: PropertySpec,
        fileSpec: List<FileSpec>
    ): CodeBlock {
        return buildCodeBlock {
            fileSpec.forEach {
                val implClass = ClassName(it.packageName, it.name)
                add("%N.add(%T(%N))\n", allHookers, implClass, loggerWrapper)
            }
        }
    }

    private fun buildStartHook(allHookers: PropertySpec, hookerClassName: ClassName): FunSpec {
        val isInterest = hookerClassName.member("isInterest")
        val init = hookerClassName.member("init")
        val startHook = hookerClassName.member("startHook")
        val oneshotHooker = ClassName(hookerClassName.packageName, "OneshotHooker")
        val startOneshotHook = oneshotHooker.member("startOneshotHook")
        return FunSpec.builder("startHook")
            .addParameter("lp", XC_LoadPackage.LoadPackageParam::class)
            .addCode(
                buildCodeBlock {
                    beginControlFlow("for (hooker in %N) {", allHookers)
                    beginControlFlow("if (!hooker.%N(lp)) {", isInterest)
                    add("continue\n")
                    endControlFlow()
                    add("hooker.%N(lp)\n", init)
                    beginControlFlow("if (hooker is %T) {", oneshotHooker)
                    add("hooker.%N(lp)\n", startOneshotHook)
                    endControlFlow()
                    add("hooker.%N(lp)\n", startHook)
                    endControlFlow()
                }
            )
            .build()
    }

    private fun buildLoggerWrapperProperty(loggerParam: ParameterSpec): PropertySpec {
        val loggerWrapperClass = XPLoggerWrapper::class.asClassName()
        return PropertySpec.builder("loggerWrapper", loggerWrapperClass, KModifier.PRIVATE)
            .initializer("%T(%N)", loggerWrapperClass, loggerParam)
            .build()
    }

    private fun buildLoggerProperty(xpLoggerWrapper: PropertySpec): PropertySpec {
        val loogerWrapperClass = XPLoggerWrapper::class.asClassName()
        val loggerClass = XPLogger::class.asClassName()
        val setterParam = ParameterSpec.builder("value", loggerClass).build()
        return PropertySpec.builder("logger", loggerClass)
            .mutable(true)
            .setter(
                FunSpec
                    .setterBuilder()
                    .addParameter(setterParam)
                    .addStatement(
                        "%N.%N = %N",
                        xpLoggerWrapper,
                        loogerWrapperClass.member("realLogger"),
                        setterParam
                    )
                    .build()
            )
            .getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %N", xpLoggerWrapper)
                    .build()
            )
            .build()
    }
}