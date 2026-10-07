// AndroidOnly: WP-212 correctly rounded log10 so RF dB math matches Darwin libm instead of the JVM's fdlibm log10.
package com.meshcoreone.android.core.services.diagnostics.rf

import kotlin.math.sqrt

/**
 * Correctly rounded base-10 logarithm.
 *
 * Swift's `log10` resolves to Darwin libm, which is correctly rounded for all but a few inputs.
 * The JVM's `Math.log10`/`StrictMath.log10` (fdlibm) is only faithfully rounded and differs from
 * Darwin on ~2% of inputs (e.g. log10(6000)), which breaks bit-exact path-loss parity. This
 * evaluates ln(x) in double-double arithmetic (~106-bit) via an atanh series, divides by a
 * double-double ln(10) and rounds once, giving the correctly rounded result.
 *
 * Non-positive, NaN and infinite inputs delegate to [kotlin.math.log10] (identical IEEE special
 * cases). Pure arithmetic; no allocation-sensitive state; thread-safe.
 */
internal object RfLog10 {
    /** 2^27 + 1, Dekker/Veltkamp split constant. */
    private const val SPLITTER = 134_217_729.0

    /** Atanh series terms; |s| <= 0.1716 so 22 terms push truncation below 2^-106. */
    private const val SERIES_TERMS = 22

    /** 2^54, used to normalize subnormal inputs before exponent extraction. */
    private const val SUBNORMAL_SCALE = 18_014_398_509_481_984.0
    private const val SUBNORMAL_EXPONENT_SHIFT = 54

    private val LN2 = Dd(Double.fromBits(0x3FE62E42FEFA39EFL), Double.fromBits(0x3C7ABC9E3B39803FL))
    private val LN10 = Dd(Double.fromBits(0x40026BB1BBB55516L), Double.fromBits(-0x4350B752B6B15C17L))
    private val SQRT_TWO = sqrt(2.0)

    /** Unevaluated sum hi + lo with |lo| <= ulp(hi) / 2. */
    private class Dd(val hi: Double, val lo: Double)

    fun log10(x: Double): Double {
        if (!(x > 0.0) || x == Double.POSITIVE_INFINITY) return kotlin.math.log10(x)
        val quotient = div(ln(x), LN10)
        return quotient.hi + quotient.lo
    }

    /** ln(x) = e * ln2 + 2 * atanh((m - 1) / (m + 1)) with x = m * 2^e, m in [sqrt(1/2), sqrt(2)). */
    private fun ln(x: Double): Dd {
        var scaled = x
        var exponent = 0
        if (scaled < java.lang.Double.MIN_NORMAL) {
            scaled *= SUBNORMAL_SCALE
            exponent = -SUBNORMAL_EXPONENT_SHIFT
        }
        val rawExponent = Math.getExponent(scaled)
        var mantissa = Math.scalb(scaled, -rawExponent) // [1, 2), exact
        exponent += rawExponent
        if (mantissa >= SQRT_TWO) {
            mantissa /= 2 // exact
            exponent += 1
        }
        val s = div(Dd(mantissa - 1.0, 0.0), twoSum(mantissa, 1.0)) // mantissa - 1 is exact (Sterbenz)
        val sSquared = mul(s, s)
        var sum = Dd(0.0, 0.0)
        var term = s
        for (n in 0 until SERIES_TERMS) {
            sum = add(sum, div(term, Dd(2.0 * n + 1.0, 0.0)))
            term = mul(term, sSquared)
        }
        return add(mul(LN2, Dd(exponent.toDouble(), 0.0)), mul(sum, Dd(2.0, 0.0)))
    }

    private fun twoSum(a: Double, b: Double): Dd {
        val s = a + b
        val bb = s - a
        return Dd(s, (a - (s - bb)) + (b - bb))
    }

    private fun quickTwoSum(a: Double, b: Double): Dd {
        val s = a + b
        return Dd(s, b - (s - a))
    }

    private fun twoProd(a: Double, b: Double): Dd {
        val p = a * b
        val aT = SPLITTER * a
        val aHi = aT - (aT - a)
        val aLo = a - aHi
        val bT = SPLITTER * b
        val bHi = bT - (bT - b)
        val bLo = b - bHi
        return Dd(p, ((aHi * bHi - p) + aHi * bLo + aLo * bHi) + aLo * bLo)
    }

    private fun add(a: Dd, b: Dd): Dd {
        val s = twoSum(a.hi, b.hi)
        val t = twoSum(a.lo, b.lo)
        val first = quickTwoSum(s.hi, s.lo + t.hi)
        return quickTwoSum(first.hi, first.lo + t.lo)
    }

    private fun mul(a: Dd, b: Dd): Dd {
        val p = twoProd(a.hi, b.hi)
        return quickTwoSum(p.hi, p.lo + (a.hi * b.lo + a.lo * b.hi))
    }

    private fun div(a: Dd, b: Dd): Dd {
        val q1 = a.hi / b.hi
        var r = add(a, negate(mul(Dd(q1, 0.0), b)))
        val q2 = r.hi / b.hi
        r = add(r, negate(mul(Dd(q2, 0.0), b)))
        val q3 = r.hi / b.hi
        val q = quickTwoSum(q1, q2)
        return add(q, Dd(q3, 0.0))
    }

    private fun negate(a: Dd): Dd = Dd(-a.hi, -a.lo)
}
