package org.jetbrains.kotlin.android.robowrapper

data class NameValue(
        val name: String = "",
        val value: String = "")

data class Attr(
        val name: String = "",
        val format: List<String> = listOf(),
        val flags: List<NameValue>? = null,
        val enum: List<NameValue>? = null)

data class Styleable(
        val name: String = "",
        val attrs: List<Attr> = listOf())

data class Attrs(
        val free: List<Attr> = listOf(),
        val styleables: Map<String, Styleable> = mapOf())