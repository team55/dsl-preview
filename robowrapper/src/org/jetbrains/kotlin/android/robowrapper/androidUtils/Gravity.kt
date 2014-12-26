package org.jetbrains.kotlin.android.robowrapper

import android.view.Gravity

private val gravity = listOf(
        Gravity.START to "start",
        Gravity.END to "end",
        Gravity.LEFT to "left",
        Gravity.TOP to "top",
        Gravity.RIGHT to "right",
        Gravity.BOTTOM to "bottom",
        Gravity.CENTER_VERTICAL to "center_vertical",
        Gravity.CENTER_HORIZONTAL to "center_horizontal",
        Gravity.CLIP_HORIZONTAL to "clip_horizontal",
        Gravity.CLIP_VERTICAL to "clip_vertical"
)

//convert Int gravity to a string representation
fun resolveGravity(g: Int): String? {
    if (g == -1 || g == 0) return null
    val ret = arrayListOf<String>()
    val present = hashSetOf<String>()

    for (it in gravity) {
        if ((it.first and g) == it.first) {
            if (it.second == "left" && "start" in present) continue
            if (it.second == "right" && "end" in present) continue
            if (it.second == "center_horizontal" && "start" in present) continue
            if (it.second == "center_horizontal" && "end" in present) continue
            if (it.second == "center_horizontal" && "left" in present) continue
            if (it.second == "center_horizontal" && "right" in present) continue
            if (it.second == "center_vertical" && "top" in present) continue
            if (it.second == "center_vertical" && "bottom" in present) continue
            present.add(it.second)
            ret.add(it.second)
        }
    }

    return if (present.isEmpty())
        null
    else ret.joinToString("|")
}