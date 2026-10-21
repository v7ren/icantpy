package net.icantpy.slayer.blaze

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure timing math for Quazii's beam in the Inferno Demonlord fight. No Minecraft types here.
 *
 * Bearings use Minecraft's yaw convention seen from above with north up: 0 = +Z (south), 90 = -X (west),
 * 180 = -Z (north), 270 = +X (east). Increasing bearing is clockwise, so a clockwise sweep has a positive rate.
 * Angles are in degrees and time is in client ticks.
 *
 * Mechanic facts this follows (measured over many recorded fights by a public research project): four co-located
 * guardians orbit Quazii's body about 1 block out at feet height, mostly clockwise at a median 2.87 deg/tick. The
 * beam is the ray from the body out through that group. (The research also tried treating it as a line with two
 * ends; in play that jumped while the beam was on the far side, so only the guardian end is timed.) Damage lands about [IMPACT_LEAD_TICKS] ticks before the line reaches the
 * player's centre (the beam's edge meets the hitbox first) and reaches out to about [REACH] blocks. The beam is
 * 0.85 high, so a vanilla jump started [JUMP_LEAD_TICKS]..[JUMP_MIN_TICKS] ticks before impact clears it.
 */
object QuaziiBeam {
    /** Beam reach in blocks; hits were measured out to 12.6. */
    const val REACH = 12.5

    /** Damage lands this many ticks before the beam line reaches the player's centre. */
    const val IMPACT_LEAD_TICKS = 4.0

    /** Press jump on the first tick the predicted impact is this close... */
    const val JUMP_LEAD_TICKS = 6.0

    /**
     * ...and still until it is this close. The feet only clear 0.85 from 3 ticks after take-off, so a jump this late
     * may not clear it, but a prediction that skips past the ideal window (the arm speeds up) still gets a try.
     */
    const val JUMP_MIN_TICKS = 1.0

    /** Guardians closer than this to the body are a partial spawn on the centre and carry no bearing. */
    const val ARM_MIN_RADIUS = 0.35

    /** Guardians further than this belong to another Quazii (measured p95 orbit radius is 1.34). */
    const val ARM_MAX_RADIUS = 2.5

    /** Below this angular speed (deg/tick) the arm is stalled and no crossing is predicted. */
    const val MIN_RATE_DEG_PER_TICK = 0.8

    /** A single step bigger than this (deg) is a body swap or teleport, never the orbit (p90 is 7.6). Dropped. */
    const val MAX_STEP_DEG = 20.0

    /** Fewest bearings that may carry a rate: four steps is about 12 degrees of sweep. */
    const val MIN_SAMPLES = 5

    /**
     * Rate assumed until [MIN_SAMPLES] bearings exist: the measured median orbit (clockwise, 75% of orbits). The
     * first beam lands a median 7 ticks after the guardians appear, before any rate can be measured.
     */
    const val PRIOR_RATE_DEG_PER_TICK = 2.87

    /** A median faster than this (deg/tick) is noise, not the orbit (measured p90 step is 7.6). */
    const val MAX_RATE_DEG_PER_TICK = 8.0

    /** Share of moving steps that must turn the same way as the rate before it is trusted. */
    const val MIN_AGREEMENT = 0.7

    /** Steps smaller than this (deg) are a missed or quantised update (1.4 deg units), not a direction. */
    const val STILL_STEP_DEG = 1.0

    /** Bearing from ([bodyX], [bodyZ]) to ([x], [z]) in [0, 360), Minecraft yaw convention (see class doc). */
    fun bearing(bodyX: Double, bodyZ: Double, x: Double, z: Double): Double {
        val dx = x - bodyX
        val dz = z - bodyZ
        val degrees = Math.toDegrees(atan2(-dx, dz))
        val normalized = degrees % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    /** Wraps an angle in degrees into (-180, 180]. */
    fun wrap(deg: Double): Double {
        var d = deg % 360.0
        if (d <= -180.0) d += 360.0
        if (d > 180.0) d -= 360.0
        return d
    }

    /**
     * Angular rate in deg/tick from the last [count] arm bearings in [history], ordered oldest first: the mean of the
     * steps, i.e. the whole sweep over the window. Positions arrive unevenly (often a 0 step, then a double one) and
     * quantised to 1.4 degrees, so single steps are useless but the sweep is accurate. Steps are wrapped so the
     * 0/360 seam is harmless, and a step bigger than [MAX_STEP_DEG] (a body swap or teleport) is dropped. Null with
     * fewer than [MIN_SAMPLES] bearings or fewer than [MIN_SAMPLES] - 1 usable steps, when the rate is slower than
     * [MIN_RATE_DEG_PER_TICK] or faster than [MAX_RATE_DEG_PER_TICK], or when fewer than [MIN_AGREEMENT] of the
     * steps that moved at all (more than [STILL_STEP_DEG]) turn the rate's way (the arm is reversing).
     * [scratch] must hold at least [count] values.
     */
    fun rate(history: DoubleArray, count: Int, scratch: DoubleArray = DoubleArray(count)): Double? {
        if (count < MIN_SAMPLES || count > history.size) return null
        var kept = 0
        var sum = 0.0
        for (i in 0 until count - 1) {
            val step = wrap(history[i + 1] - history[i])
            if (abs(step) > MAX_STEP_DEG) continue
            scratch[kept++] = step
            sum += step
        }
        if (kept < MIN_SAMPLES - 1) return null
        val mean = sum / kept
        if (abs(mean) < MIN_RATE_DEG_PER_TICK || abs(mean) > MAX_RATE_DEG_PER_TICK) return null
        var moving = 0
        var agree = 0
        for (i in 0 until kept) {
            if (abs(scratch[i]) <= STILL_STEP_DEG) continue
            moving++
            if (scratch[i] * mean > 0.0) agree++
        }
        if (agree < MIN_AGREEMENT * moving) return null
        return mean
    }

    /**
     * Ticks until the beam sweeps the player's bearing, turning the way [rate] says. Null when the rate is too small
     * to time, the player is out of [REACH], or the beam has just passed the player (it is then more than half a
     * turn away, and is predicted again as it comes round).
     */
    fun ticksUntilCrossing(armBearing: Double, rate: Double, playerBearing: Double, playerDistance: Double): Double? {
        if (!playerDistance.isFinite() || playerDistance < 0.0 || playerDistance > REACH) return null
        if (!rate.isFinite() || abs(rate) < MIN_RATE_DEG_PER_TICK) return null
        val ahead = if (rate > 0.0) wrap(playerBearing - armBearing) else wrap(armBearing - playerBearing)
        if (ahead < 0.0) return null
        return ahead / abs(rate)
    }

    /** Ticks until the damage tick: the crossing time minus [IMPACT_LEAD_TICKS]. May be negative once it has passed. */
    fun impactTicks(crossingTicks: Double): Double = crossingTicks - IMPACT_LEAD_TICKS

    /**
     * True when a jump should go out for an impact [impactTicks] away: from [JUMP_LEAD_TICKS] (plus [earlyTicks])
     * down to [JUMP_MIN_TICKS]. Only the start moves earlier, so a prediction that jumps past the start still jumps.
     */
    fun jumpWindow(impactTicks: Double, earlyTicks: Double = 0.0): Boolean =
        impactTicks in JUMP_MIN_TICKS..(JUMP_LEAD_TICKS + earlyTicks)

    /**
     * Keeps the player's centre at least [radius] from the body centre, like a round wall around Quazii, because the
     * beam starts inside the body and hits over and over there. Moving by ([dx], [dz]) from ([px], [pz]):
     *  - from outside, a move that would end inside slides to the circle's edge instead;
     *  - from inside (the body walked into the player), the part of the move toward the body is dropped and an
     *    outward step of up to [push] (never past the edge) is added, much like vanilla entity pushing.
     * Writes the allowed move into [out] and returns true when it differs from the input. Pure.
     */
    fun keepOut(
        px: Double, pz: Double, dx: Double, dz: Double,
        bx: Double, bz: Double, radius: Double, push: Double,
        out: DoubleArray,
    ): Boolean {
        val rx = px - bx
        val rz = pz - bz
        val d = sqrt(rx * rx + rz * rz)
        if (d >= radius) {
            val nx = rx + dx
            val nz = rz + dz
            val nd = sqrt(nx * nx + nz * nz)
            if (nd >= radius) return false
            if (nd < 1e-9) {
                out[0] = 0.0
                out[1] = 0.0
            } else {
                out[0] = nx * (radius / nd) - rx
                out[1] = nz * (radius / nd) - rz
            }
            return true
        }
        // Exactly on the centre has no outward direction; any will do.
        val ux = if (d < 1e-9) 1.0 else rx / d
        val uz = if (d < 1e-9) 0.0 else rz / d
        val radial = dx * ux + dz * uz
        val outward = max(radial, min(push, radius - d))
        out[0] = dx - radial * ux + outward * ux
        out[1] = dz - radial * uz + outward * uz
        return true
    }

    /** Horizontal distance in blocks between two points, for the reach check. */
    fun horizontalDistance(ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = bx - ax
        val dz = bz - az
        return sqrt(dx * dx + dz * dz)
    }
}
