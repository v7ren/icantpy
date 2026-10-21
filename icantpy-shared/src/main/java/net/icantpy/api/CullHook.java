package net.icantpy.api;

/**
 * Plain-Java switch read by the render culling mixins, in the same style as [GlowHook]: one volatile read
 * while inactive, no payload classes touched from the mixins. Objects are passed untyped so this class
 * references no game classes; the payload casts them (Entity, BlockEntity).
 *
 * Every method answers "skip this?" and must be cheap: it runs per entity, block entity and particle per frame.
 * Any provider failure means "draw it" so a payload bug can never hide the world.
 */
public final class CullHook {
    public interface Provider {
        /** True to skip rendering [entity] (a net.minecraft.world.entity.Entity) this frame. */
        boolean skipEntity(Object entity);

        /** True to skip rendering [blockEntity] (a net.minecraft.world.level.block.entity.BlockEntity) this frame. */
        boolean skipBlockEntity(Object blockEntity);

        /** True to skip rendering a particle at world position x, y, z this frame. */
        boolean skipParticle(double x, double y, double z);

        /** True to hide the name tag of [entity] this frame while still drawing the entity. */
        boolean hideNameTag(Object entity);
    }

    /** True only while a provider is installed. Checked first by the mixins. */
    public static volatile boolean enabled = false;

    public static volatile Provider provider = null;

    private CullHook() {
    }

    public static boolean skipEntity(Object entity) {
        if (!enabled) return false;
        Provider p = provider;
        if (p == null) return false;
        try {
            return p.skipEntity(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean skipBlockEntity(Object blockEntity) {
        if (!enabled) return false;
        Provider p = provider;
        if (p == null) return false;
        try {
            return p.skipBlockEntity(blockEntity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean skipParticle(double x, double y, double z) {
        if (!enabled) return false;
        Provider p = provider;
        if (p == null) return false;
        try {
            return p.skipParticle(x, y, z);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hideNameTag(Object entity) {
        if (!enabled) return false;
        Provider p = provider;
        if (p == null) return false;
        try {
            return p.hideNameTag(entity);
        } catch (Throwable ignored) {
            return false;
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
