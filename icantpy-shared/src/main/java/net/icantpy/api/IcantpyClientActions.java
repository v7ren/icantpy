package net.icantpy.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Send chat/commands from a loader-owned class. Payload classes are not on the
 * Knot classpath; other mods that scan the stack with {@code Class.forName}
 * fail if the send happens directly from reloadable code.
 */
public final class IcantpyClientActions {
    private IcantpyClientActions() {}

    public static void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.schedule(() -> {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                connection.sendCommand(command);
            }
        });
    }

    public static void sendChat(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.schedule(() -> {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                connection.sendChat(message);
            }
        });
    }
}
