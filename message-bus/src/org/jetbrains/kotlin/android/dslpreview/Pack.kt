package org.jetbrains.kotlin.android.dslpreview

import com.google

public class Pack(
        public val xml: String,
        public val error_code: Int,
        public val error: String,
        public val alive: Boolean,
        public var port: Int = -1
) {

    public fun toJson(): String {
        return gson.toJson(this)
    }

    class object {
        private val gson = google.gson.Gson()
    }
}