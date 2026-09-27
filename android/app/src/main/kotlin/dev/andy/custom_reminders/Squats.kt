package dev.andy.custom_reminders

/**
 * Turns recent step activity into how many squats a reminder should ask for.
 *
 * The ask scales linearly: 10 squats at 0-79 steps, 9 at 80-159, 5 at 400, 0 at 800+.
 */
object Squats {
    /** Squats asked for when there's been minimal activity since the last reminder. */
    const val MAX = 10

    /** Steps since the last reminder at which the ask drops to zero squats. */
    const val STEP_THRESHOLD = 800L

    /** Steps that have to accumulate to knock one squat off the ask. */
    const val STEPS_PER_SQUAT = STEP_THRESHOLD / MAX

    /** Floors, so a squat is only forgiven once its full step cost has been paid. */
    fun forSteps(steps: Long): Int {
        if (steps <= 0L) return MAX
        if (steps >= STEP_THRESHOLD) return 0
        return MAX - (steps / STEPS_PER_SQUAT).toInt()
    }
}
