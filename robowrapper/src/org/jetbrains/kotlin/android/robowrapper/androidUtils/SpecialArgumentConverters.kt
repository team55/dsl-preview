package org.jetbrains.kotlin.android.robowrapper

import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.text.TextUtils.TruncateAt
import android.view.View

fun convertScaleType(value: ImageView.ScaleType) = when (value) {
    ScaleType.CENTER -> "center"
    ScaleType.CENTER_CROP -> "centerCrop"
    ScaleType.CENTER_INSIDE -> "fitInside"
    ScaleType.FIT_CENTER -> "fitCenter"
    ScaleType.FIT_END -> "fitEnd"
    ScaleType.FIT_START -> "fitStart"
    ScaleType.FIT_XY -> "fitXY"
    ScaleType.MATRIX -> "matrix"
    else -> null
}

fun convertEllipsize(value: TruncateAt) = when (value) {
    TruncateAt.END -> "end"
    TruncateAt.MARQUEE -> "marquee"
    TruncateAt.MIDDLE -> "middle"
    TruncateAt.START -> "start"
    TruncateAt.END_SMALL -> "end"
    else -> "none"
}

/*
    Value is a raw px value got from property getter.
    Convert it to sp if a text-related parameter (following the Android developer guidelines), all other to dp
 */
fun resolveDimension(view: View, key: String, value: String): String {
    val intValue = value.toDouble().toInt() //0.0

    val useSp = (key == "textSize")
    if (useSp) {
        return "${intValue}sp"
    } else {
        val dpValue = view.getContext().px2dip(intValue).prettifyNumber()
        return "${dpValue}dp"
    }
}