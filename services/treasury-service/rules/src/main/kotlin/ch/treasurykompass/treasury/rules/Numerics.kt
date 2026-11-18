package ch.treasurykompass.treasury.rules

import kotlin.math.max
import kotlin.math.min

// Numerics mirror the JS prototype exactly (architecture.md section 7). Math.round rounds half up like
// JavaScript's Math.round; kotlin.math.round would round half to even and turn a risk capacity of 2.5 into 2.

internal fun roundHalfUp(value: Double): Int = Math.round(value).toInt()

internal fun round1(value: Double): Double = Math.round(value * 10) / 10.0

internal fun round2(value: Double): Double = Math.round(value * 100) / 100.0

internal fun clamp(value: Double, low: Double, high: Double): Double = max(low, min(high, value))

internal fun clamp(value: Int, low: Int, high: Int): Int = max(low, min(high, value))

internal fun average(values: List<Int>): Double = values.sum().toDouble() / values.size.coerceAtLeast(1)

/** Score 1 + number of cut points reached (`value >= cut`), as the prototype's `band()`. */
internal fun band(value: Double, cuts: List<Double>): Int = 1 + cuts.count { value >= it }
