package cn.tinyhai.xp.processor.codegen

import com.squareup.kotlinpoet.FileSpec

interface CodeGen {
    fun start(): FileSpec
}