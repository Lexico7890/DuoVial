package com.duovial.platform

import kotlin.math.pow
import kotlin.math.roundToLong

fun Double.formatDecimal(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (this * factor).roundToLong()
    val intPart = rounded / factor.toLong()
    val fracPart = (rounded % factor.toLong()).toString().padStart(decimals, '0')
    return if (decimals > 0) "$intPart.$fracPart" else "$intPart"
}
