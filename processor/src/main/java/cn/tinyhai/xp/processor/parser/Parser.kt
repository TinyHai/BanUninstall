package cn.tinyhai.xp.processor.parser

interface Parser<T> {
    fun parse(): List<T>
}