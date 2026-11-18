package ch.treasurykompass.treasury.rules

/**
 * R-HED-07 (regelwerk § 5, Szenario): EBIT effect of a spot move `S1 = S × (1 + s)`. Simplification of the
 * prototype: the move applies immediately to all maturities.
 */
internal object ScenarioCalculator {
    val SHIFT_RANGE_PCT: IntRange = -15..15

    fun curve(spotRate: Double, netPosition: Double, tranches: List<Tranche>, collarShare: Double): List<ScenarioPoint> =
        SHIFT_RANGE_PCT.map { shiftPct -> point(spotRate, netPosition, tranches, collarShare, shiftPct) }

    /**
     * Per tranche: forward part locks `F − S`, collar part moves within its band, the open rest moves with the spot.
     * Without hedge (or when blocked) the result equals `netPosition × (S1 − S)`.
     */
    fun point(spotRate: Double, netPosition: Double, tranches: List<Tranche>, collarShare: Double, shiftPct: Int): ScenarioPoint {
        val shifted = spotRate * (1 + shiftPct / 100.0)
        var hedged = 0.0
        tranches.forEach { tranche ->
            val forwardPart = tranche.hedgedAmountFx * (1 - collarShare)
            val collarPart = tranche.hedgedAmountFx * collarShare
            val openPart = tranche.amountFx - tranche.hedgedAmountFx
            val collarRate = clamp(shifted, tranche.collarFloor, tranche.collarCap)
            hedged += forwardPart * (tranche.forwardRate - spotRate) + collarPart * (collarRate - spotRate) + openPart * (shifted - spotRate)
        }
        return ScenarioPoint(
            shiftPct = shiftPct,
            spotRate = shifted,
            unhedgedChf = netPosition * (shifted - spotRate),
            hedgedChf = hedged,
        )
    }
}
