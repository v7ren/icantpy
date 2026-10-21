package net.icantpy.api;

/**
 * Plain-Java switch for the render.appearance.owner begin/end pair, read by IcantpyBridge before it allocates
 * any event. Kept in its own class (not a field on IcantpyBridge) so a payload on an older loader loads its own
 * copy instead of hitting NoSuchFieldError.
 *
 * [wanted] defaults to true so payloads that never set it keep today's behaviour. While false, begin only bumps a
 * per-thread skipped depth and end consumes that depth instead of dispatching, so begin/end stay balanced even
 * when [wanted] flips between a begin and its end.
 */
public final class AppearanceOwnerHook {
    public static volatile boolean wanted = true;

    private static final ThreadLocal<int[]> SKIPPED_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private AppearanceOwnerHook() {
    }

    /** Call on begin when [wanted] is false. Returns true when the begin must be dropped. */
    public static boolean skipBegin() {
        if (wanted) return false;
        SKIPPED_DEPTH.get()[0]++;
        return true;
    }

    /** Call on end. Returns true when the matching begin was skipped, so this end must be dropped too. */
    public static boolean consumeSkippedEnd() {
        int[] depth = SKIPPED_DEPTH.get();
        if (depth[0] <= 0) return false;
        depth[0]--;
        return true;
    }
}
