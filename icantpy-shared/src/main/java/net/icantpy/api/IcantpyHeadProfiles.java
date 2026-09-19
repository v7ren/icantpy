package net.icantpy.api;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.world.item.component.ResolvableProfile;

/** Builds resolved player-head profiles from a Skyblocker-style base64 texture value. */
public final class IcantpyHeadProfiles {
    private IcantpyHeadProfiles() {}

    public static ResolvableProfile fromTexture(String texture) {
        ArrayListMultimap<String, Property> properties = ArrayListMultimap.create();
        properties.put("textures", new Property("textures", texture));
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)),
                "custom",
                new PropertyMap(properties)
        );
        return ResolvableProfile.createResolved(profile);
    }
}
