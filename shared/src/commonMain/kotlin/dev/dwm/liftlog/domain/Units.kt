package dev.dwm.liftlog.domain

import kotlin.math.roundToInt

// DB stores kg (sync/progression unchanged); all UI shows pounds.
const val KG_PER_LB = 0.45359237

fun Double.kgToLb(): Double = this / KG_PER_LB
fun Double.lbToKg(): Double = this * KG_PER_LB

/** kg → lb rounded to 0.1 for display/entry round-trips. */
fun Double.kgToLbDisplay(): Double = (kgToLb() * 10).roundToInt() / 10.0

fun kgToLbStr(kg: Double): String =
    kg.kgToLbDisplay().let { if (it % 1.0 == 0.0) "${it.toLong()}" else "$it" }
