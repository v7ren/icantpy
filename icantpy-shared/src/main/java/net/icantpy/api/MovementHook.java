package net.icantpy.api;

/**
 * Plain-Java switch read by the movement guard mixin, in the same style as [CullHook] and [VisualHook]. While a
 * provider is installed it may shorten the local player's own movement vector for this tick, exactly where vanilla
 * shortens it for sneaking at a ledge (Player.maybeBackOffFromEdge). Nothing is sent, cancelled or rewritten on the
 * network: the client simply moves less, and the normal movement updates report where the player really is.
 *
 * Objects are passed untyped so this class references no game classes; the payload casts them.
 */
public final class MovementHook {
    public interface Provider {
        /**
         * Returns a replacement for [movement] (a net.minecraft.world.phys.Vec3) for [player] (the local
         * net.minecraft.client.player.LocalPlayer), or null to keep it unchanged.
         */
        Object adjust(Object player, Object movement);
    }

    /** True only while a provider is installed. Checked first by the mixin. */
    public static volatile boolean enabled = false;

    public static volatile Provider provider = null;

    private MovementHook() {
    }

    /** Any provider failure keeps vanilla movement, so a payload bug can never freeze the player. */
    public static Object adjust(Object player, Object movement) {
        if (!enabled) return null;
        Provider p = provider;
        if (p == null) return null;
        try {
            return p.adjust(player, movement);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Installs [next] and enables it, or disables and clears the hook when [next] is null. */
    public static void install(Provider next) {
        if (next == null) {
            enabled = false;
            provider = null;
        } else {
            provider = next;
            enabled = true;
        }
    }
}
