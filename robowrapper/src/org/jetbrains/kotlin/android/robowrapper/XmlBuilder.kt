package org.jetbrains.kotlin.android.robowrapper

import android.view.View
import android.view.ViewGroup
import java.io.File
import java.nio.charset.Charset
import android.view.Gravity
import android.widget.*
import android.view.ViewGroup.MarginLayoutParams
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.content.Context
import android.util.TypedValue
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowDrawable
import android.support.v4.widget.SlidingPaneLayout
import android.widget.ImageView.ScaleType
import android.text.SpannedString
import android.text.SpannableStringBuilder
import android.text.TextUtils.TruncateAt
import java.util.regex.Pattern

// Serialize a ViewNode to xml
fun toXml(v: ViewNode): String {
    val sb = Buffer().append(XML_HEADER)

    fun ViewNode.invoke(indent: Int) {
        val name = getXmlName()
        // Insert schema if top-level node
        val schema = if (indent == 0) XML_SCHEMA else ""
        val props = schema + genAttributes(view, name, attrs)
        val delim = if (props.isEmpty()) "" else " "
        val I = "\t".repeat(indent)
        sb.append("$I<$name$delim$props")
        if (children.isEmpty()) {
            sb.append("/>\n")
        } else {
            sb.append(">\n")
            children.forEach { it(indent + 1) }
            sb.append("$I</$name>\n")
        }
    }

    v(0)
    return sb.toString()
}

// Render attributes
// @param v used for resolving dimensions (and some Robolectric-related work)
// @param attrs set of attrs that should be parsed
fun genAttributes(v: View, className: String, attrs: Set<Pair<String, Pair<Attr?, Any>>>): String {
    val present = hashSetOf<String>()
    var layoutParamsPresent = false
    val sb = Buffer()

    for (attr in attrs) {
        val key = attr.first
        if (key in present) continue
        val value = renderAttribute(v, attr.second.first, attr.first, attr.second.second)
        if (value == null || ignoreAttribute(className, key, value)) continue
        if (attr.first == "layoutParams") {
            // LayoutParams is already rendered
            sb.append(value)
            layoutParamsPresent = true
        } else
            sb.append("android:").append(attr.first).append("=\"").append(value).append("\" ")
        present.add(attr.first)
    }
    if (!layoutParamsPresent) {
        sb.append(""" android:layout_width="wrap_content" android:layout_height="wrap_content" """)
    }

    return sb.toString()
}

private fun ignoreAttribute(className: String, key: String, value: String): Boolean {
    val def = defaultValues.get(key)
    val classDef = defaultValues.get(className + "#" + key)
    fun match(s: String, pattern: Any) = when(pattern) {
        is Pattern -> pattern.matcher(s).matches()
        is String -> pattern == s
        else -> false
    }
    return (classDef?.any { match(value, it) } ?: false) || (def?.any { match(value, it) } ?: false)
}