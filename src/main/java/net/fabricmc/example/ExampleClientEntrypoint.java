package net.fabricmc.example;

import baritone.api.BaritoneAPI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import nrl.actorsim.minecraft.MinecraftConnector;
import org.lwjgl.glfw.GLFW;

public class ExampleClientEntrypoint implements ClientModInitializer {
    private static KeyBinding keyBinding;
    @Override
    public void onInitializeClient() {
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.examplemod.spook", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "category.examplemod.test" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto 0 0");
                assert client.player != null;
                client.player.sendMessage(new LiteralText("Key 1 was pressed!"), false);
            }
        });
        MinecraftConnector.reset();
        registerClientEvents();
        registerServerAndWorldEvents();
    }

    private void registerClientEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(MinecraftConnector::addClient);
        ClientLifecycleEvents.CLIENT_STOPPING.register(MinecraftConnector::removeClient);
    }


    private void registerServerAndWorldEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(MinecraftConnector::addServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(MinecraftConnector::removeServer);
        ServerWorldEvents.LOAD.register(MinecraftConnector::addWorld);
    }


}
