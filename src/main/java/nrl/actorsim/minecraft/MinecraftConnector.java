package nrl.actorsim.minecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

public class MinecraftConnector {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MinecraftConnector.class);

    private static MinecraftConnector instance;
    private static BaritoneAdapter baritoneAdapter;
    private static MemcachedServer memcachedServer;

    private ConnectorState state;

    private MinecraftServer minecraftServer;
    private MinecraftClient minecraftClient;
    private ServerWorld serverWorld;



    public static void reset() {
        initInstance();
        instance.state = ConnectorState.NOT_LOADED;
    }

    private static void initInstance() {
        if (instance == null) {
            instance = new MinecraftConnector();
            instance.registerClientEvents();
            instance.registerServerAndWorldEvents();
        }
        if (baritoneAdapter == null) {
            baritoneAdapter = new BaritoneConnector();
        }

        if (memcachedServer == null) {
            try {
                memcachedServer = new MemcachedServer();
                memcachedServer.initAndStart(instance);
                System.out.print("MemcachedServer started and running");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public static void addServer(MinecraftServer server) {
        if (instance.minecraftServer != null) {
            instance.minecraftServer = null;
        }
        instance.minecraftServer = server;
    }

    public static void removeServer(MinecraftServer minecraftServer) {
        //TODO do some checking here?
        instance.minecraftServer = null;
    }

    private static void initClient(MinecraftClient client) {
        if (instance.minecraftClient != null) {
            instance.minecraftClient = null;
        }
        instance.minecraftClient = client;
    }

    private MinecraftConnector() {

    }

    public static void addWorld(MinecraftServer minecraftServer, ServerWorld serverWorld) {
        if (serverWorld != null) {
            instance.serverWorld = null;
        }
        instance.serverWorld = serverWorld;
    }

    public static void addClient(MinecraftClient minecraftClient) {
        instance.minecraftClient = minecraftClient;
        instance.minecraftServer = minecraftClient.getServer();
    }

    public static void removeClient(MinecraftClient minecraftClient) {
        //TODO do some checking here to ensure the client matches
        instance.minecraftClient = null;
    }

    public boolean isServerSideOnly() {
        EnvType envType = FabricLoader.getInstance().getEnvironmentType();
        if(minecraftServer.isDedicated()) {
            if(minecraftClient == null) {
                return true;
            } else {
                logger.error("There's some kind of error because we shouldn't have a client on a dedicated server.");
            }
        }
        return false;
    }

    // ====================================================
    // region<Registered methods>

    private void registerClientEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(MinecraftConnector::addClient);
        ClientLifecycleEvents.CLIENT_STOPPING.register(MinecraftConnector::removeClient);
    }


    private void registerServerAndWorldEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(MinecraftConnector::addServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(MinecraftConnector::removeServer);
        ServerWorldEvents.LOAD.register(MinecraftConnector::addWorld);

        KeyBinding keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.examplemod.stoptest", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_K, // The keycode of the key
                "category.examplemod.test" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                returnToMainScreen(client);
            }
        });
    }

    private void returnToMainScreen(MinecraftClient client) {
        //adapted from GameMenuScreen.class in the menu.returnToMenu button details
        client.world.disconnect();
        client.disconnect();
        client.openScreen(new TitleScreen());
        client.startIntegratedServer("Test2");
    }

    // endregion
    // ====================================================


    public Command.Result loadWorld(Command command) {
        this.load(command.world_name);
        return Command.Result.EXECUTING;
    }

    private void killServer() {
        if (minecraftClient != null) {
            minecraftClient.getServer().stop(true);
        }
    }

    /**
     * Instantly kills the client GUI.
     */
    private void killClient() {
        if (minecraftClient != null) {
            minecraftClient.stop();
        }
    }

    void load(String worldName) {
        if (minecraftClient != null) {
            MinecraftClient.getInstance().execute(() -> {
                MinecraftConnector.this.unload_impl();
                minecraftClient.startIntegratedServer(worldName);
                state = ConnectorState.LOADING;
            });
        }
    }

    void unload() {
        if (minecraftClient != null) {
            MinecraftClient.getInstance().execute(() -> {
                MinecraftConnector.this.unload_impl();
                state = ConnectorState.NOT_LOADED;
            });
        }
    }

    private void unload_impl() {
        if (minecraftClient != null) {
            if (minecraftClient.world != null) {
                minecraftClient.world.disconnect();
            }
            if (minecraftClient.isIntegratedServerRunning()) {
                minecraftClient.disconnect();
                minecraftClient.openScreen(new TitleScreen());
            }
        }
        state = ConnectorState.NOT_LOADED;
    }

    public BaritoneAdapter getBaritoneAdapter() {
        return baritoneAdapter;
    }

    public void run(Command command) {
    }

    public enum ConnectorState {
        NOT_LOADED,
        LOADING,
        LOADED,
        MISSION_BUILDING,
        MISSION_STARTED,
        MISSION_STOPPING,
        MISSION_STOPPED
    }

}
