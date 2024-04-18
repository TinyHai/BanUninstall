package cn.tinyhai.xp.processor.codegen

import cn.tinyhai.xp.annotation.HookType

data class HookerInfo(
    val hookerId: String?,
    val targetClassName: String,
    val targetMethod: String,
    val hookerMethod: String,
    val isOneshot: Boolean,
    val unhookable: Boolean,
    val hookType: HookType,
    val minSdk: Int,
    val maxSdk: Int
)