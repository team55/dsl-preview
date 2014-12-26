package org.jetbrains.kotlin.android.robowrapper

import android.view.Gravity
import java.util.regex.Pattern
import com.google.gson.Gson

private val XML_HEADER = """<?xml version="1.0" encoding="utf-8"?>"""+"\n\n"
private val XML_SCHEMA = """ xmlns:android="http://schemas.android.com/apk/res/android" """

/*
    Key-value pairs that won't be present in result xml. Usually default values for attrs (or convert fixes).
    Line format: [<CLASSNAME>#]<PROPNAME>:<VALUE1>:<VALUE2>:...:<VALUEn>
 */
private val defaultValues = readResource("default_values.txt")
        .split('\n').filter { it.length() > 2 }.fold(hashMapOf<String, List<Any>>()) { hmap, value ->
    val delimiter = value.indexOf(' ')
    val key = value.substring(0, delimiter)
    val values = value.substring(delimiter + 1)
    hmap.put(key, values.split(' ').map {
        if (it.contains("*") || it.contains("?")) {
            Pattern.compile(wildcardToRegex(it))
        } else it
    })
    hmap
}

private val attrs = Gson().fromJson(readResource("attrs.json"), javaClass<Attrs>())

private val ignoredMethods = setOf(
        "getX", "getY", "getInputType", "getTextScaleX", "getTextScaleY",
        "isImportantForAccessibility", "getImportantForAccessibility",
        "getImeOptions", "getPersistentDrawingCache", "getDescendantFocusability"
)

private val ignoreChildrenOf = setOf(
        "android.widget.CalendarView", "android.widget.TimePicker", "android.widget.DatePicker",
        "android.support.v4.view.PagerTabStrip", "android.widget.NumberPicker", "android.widget.SearchView"
)

private val androidViewPaths = setOf(
        "android.view.",
        "android.widget.",
        "android.appwidget.",
        "android.support.v4.app.",
        "android.support.v4.view.",
        "android.support.v4.widget.",
        "android.support.v7.",
        "android.support.v13.app."
)

val dimensionProperties = setOf(
        "maxWidth",
        "maxHeight",
        "minWidth",
        "minHeight",
        "dropDownVerticalOffset",
        "dropDownHorizontalOffset"
)