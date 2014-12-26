package org.jetbrains.kotlin.android.robowrapper

class Buffer {

    private val builder = StringBuilder()

    fun append(s: String): Buffer {
        builder.append(s: String?)
        return this
    }

    fun append(c: Char): Buffer {
        builder.append(c)
        return this
    }

    override fun toString(): String {
        return builder.toString()
    }

    fun size() = builder.length()
}