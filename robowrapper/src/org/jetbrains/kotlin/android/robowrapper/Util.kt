package org.jetbrains.kotlin.android.robowrapper

import java.text.DecimalFormat
import android.app.Application

private val DEBUG = false

public class UnsupportedClassException : RuntimeException()

private fun Float.prettifyNumber() = toDouble().prettifyNumber()

private fun Double.prettifyNumber(): String {
    val df = DecimalFormat("#")
    df.setMaximumFractionDigits(8)
    var value = df.format(this)
    if (value.startsWith(".")) value = "0$value"
    return if (value.endsWith(".0")) value.replace(".0", "") else value
}

private fun isNumeric(value: Any): Boolean {
    if (value is Float) {
        return (!java.lang.Float.isInfinite(value) && !value.isNaN())
    }
    if (value is Double) {
        return (!java.lang.Double.isInfinite(value) && !value.isNaN())
    }
    return (value is Int) || (value is Long)
}

private fun decapitalize(s: String): String {
    if (s.isEmpty()) return s;
    return Character.toLowerCase(s.charAt(0)) + s.substring(1)
}

private fun wildcardToRegex(wildcard: String): String {
    val b = Buffer().append('^')
    wildcard.forEach { c -> when (c) {
        '*' -> b.append(".*")
        '?' -> b.append(".")
        '(', ')', '[', ']', '$', '^', '.', '{', '}', '|', '\\' -> b.append('\\').append(c)
        else -> b.append(c)
    }}
    return b.append('$').toString()
}

private fun readResource(filename: String): String {
    return javaClass<Attrs>().getClassLoader().getResourceAsStream(filename).reader("UTF-8").readText()
}