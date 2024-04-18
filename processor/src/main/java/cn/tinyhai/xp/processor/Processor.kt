package cn.tinyhai.xp.processor

import cn.tinyhai.xp.hook.Hooker
import cn.tinyhai.xp.processor.codegen.GenerateHookScope
import cn.tinyhai.xp.processor.codegen.GenerateHookerManager
import cn.tinyhai.xp.processor.parser.HookScopeParser
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class Processor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("start >>>>>>>>>>>>>>>>>>>>>>")
        val packageName = Hooker::class.asClassName().packageName
        val hookeScopeParser = HookScopeParser(resolver, logger)
        val allAutoRegisterHookScope = hookeScopeParser.parse().map { hookScopeInfo ->
            GenerateHookScope(packageName, hookScopeInfo, logger).start().let { fileSpec ->
                fileSpec.writeTo(codeGenerator, Dependencies(true))
                if (hookScopeInfo.autoRegister) {
                    fileSpec
                } else {
                    null
                }
            }
        }.filterNotNull()
        if (allAutoRegisterHookScope.isNotEmpty()) {
            val hookerManagerFile =
                GenerateHookerManager(allAutoRegisterHookScope, packageName).start()
            hookerManagerFile.also { logger.info(it.toString()) }.writeTo(codeGenerator, Dependencies(true))
        }
        logger.info("end <<<<<<<<<<<<<<<<<<<<<<<<")
        return emptyList()
    }
}