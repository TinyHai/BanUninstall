package cn.tinyhai.xp.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

data class HookScopeInfo(
    val scopeName: String,
    val isUnhookable: Boolean,
    val hasLogger: Boolean,
    val targetPackageName: String,
    val targetProcessName: String,
    val autoRegister: Boolean,
    val hookerGate: MemberName?,
    val initiate: MemberName?,
    val scopePackageName: String,
    val scopeClassName: ClassName?,
    val injectMemberName: MemberName?,
    val hookerInfos: List<HookerInfo>
)