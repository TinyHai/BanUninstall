package cn.tinyhai.xp.processor.parser

import cn.tinyhai.xp.annotation.HookType
import cn.tinyhai.xp.annotation.HookerId
import cn.tinyhai.xp.annotation.MethodHooker
import cn.tinyhai.xp.annotation.Oneshot
import cn.tinyhai.xp.processor.codegen.HookerInfo
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import de.robv.android.xposed.XC_MethodHook

class HookerInfoParser(
    private val isUnhookable: Boolean,
    private val functions: Sequence<KSFunctionDeclaration>,
    private val resolver: Resolver,
) : Parser<HookerInfo> {
    @OptIn(KspExperimental::class)
    override fun parse(): List<HookerInfo> {
        return functions.filterWithMethodHooker().map {
            val isOneshot = !isUnhookable || it.isAnnotationPresent(Oneshot::class)
            val unhookable =
                it.getAnnotationsByType(Oneshot::class).firstOrNull()?.unhookable ?: false
            it.ensureValid(unhookable)
            val hookerId = it.getAnnotationsByType(HookerId::class).firstOrNull()?.id
            val methodHooker = it.getAnnotationsByType(MethodHooker::class).single()
            HookerInfo(
                hookerId = hookerId,
                targetClassName = methodHooker.className,
                targetMethod = methodHooker.methodName,
                hookerMethod = it.simpleName.getShortName(),
                isOneshot = isOneshot,
                unhookable = unhookable,
                hookType = methodHooker.hookType,
                minSdk = methodHooker.minSdkInclusive,
                maxSdk = methodHooker.maxSdkExclusive
            )
        }.toList()
    }

    @OptIn(KspExperimental::class)
    private fun Sequence<KSFunctionDeclaration>.filterWithMethodHooker() = filter {
        it.isAnnotationPresent(MethodHooker::class)
    }

    private inline fun <reified T> KSType.isAssignableFrom(resolver: Resolver): Boolean {
        val classDeclaration = requireNotNull(resolver.getClassDeclarationByName<T>()) {
            "Unable to resolve ${KSClassDeclaration::class.simpleName} for type ${T::class.simpleName}"
        }
        return isAssignableFrom(classDeclaration.asStarProjectedType())
    }

    private fun KSFunctionDeclaration.ensureValid(unhookable: Boolean) {
        val parameterSize = parameters.size
        when {
            unhookable && parameterSize == 2 -> {
                val paramType = parameters[0].type.resolve()
                val unhookType = parameters[1].type.resolve()
                if (paramType.isAssignableFrom<XC_MethodHook.MethodHookParam>(resolver)
                    && unhookType.isAssignableFrom<() -> Unit>(resolver)
                ) {
                    return
                }
            }

            parameterSize == 1 -> {
                val paramType = parameters[0].type.resolve()
                if (paramType.isAssignableFrom<XC_MethodHook.MethodHookParam>(resolver)) {
                    return
                }
            }

            else -> {
                throw RuntimeException("${qualifiedName?.getQualifier()} is invalid. unhookable: $unhookable")
            }
        }
    }
}