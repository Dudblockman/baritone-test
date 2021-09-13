package nrl.actorsim.minecraft;

import baritone.api.BaritoneAPI;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static nrl.actorsim.minecraft.Command.Result.SENT_TO_BARITONE;

public class MinecraftConnector {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MinecraftConnector.class);
    final static String CONNECTOR_NAMESPACE = "nrl.actorsim.minecraft";

    public static Identifier INVENTORY_CHANGE_ID = new Identifier(CONNECTOR_NAMESPACE, "inventory-change");

    private static MinecraftConnector instance;
    private static MemcachedServer memcachedServer;

    private ConnectorState state;

    private MinecraftServer minecraftServer;
    private MinecraftClient minecraftClient;
    private ServerWorld serverWorld;

    public static void initInstanceIfNeeded() {
        if (instance == null) {
            instance = new MinecraftConnector();
        }
    }

    public static void initServerInstance() {
        initInstanceIfNeeded();
        instance.initializeServerInventoryListener();
        instance.state = ConnectorState.NOT_LOADED;
    }

    public static void initClientInstance() {
        initInstanceIfNeeded();
        instance.registerClientEvents();
        instance.registerServerAndWorldEvents();
        if (memcachedServer == null) {
            try {
                memcachedServer = new MemcachedServer();
                memcachedServer.initAndStart(instance);
                System.out.print("MemcachedServer started and running");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        instance.state = ConnectorState.NOT_LOADED;
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
    // region<Register client methods>

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
                testFunction();
            }
        });
    }

    /**
     * A test method that is bound to the "k" key while in a world.
     *
     * This empty method that is easy to fill with code to test
     * in a debug session without reloading minecraft.
     *
     */
    private void testFunction() {
        Item item = MinecraftHelpers.findBestItemMatch("iron", Command.ActionName.MINE);
        sendGivePlayerInventoryMessage(item, 1, -1);
    }

    // endregion
    // ====================================================


    public void run(Command command) {
        if (command.isWorldCommand()) {
            doWorldCommand(command);
        } else if (command.isBaritoneCommand()) {
            sendBaritoneCommand(command);
        } else if (command.isMissionCommand()) {
            doMissionCommand(command);
        }
    }

    public void sendBaritoneCommand(Command command) {
        String commandString = command.toBaritoneCommand();
        logger.info("Running Command string '{}' from command {}", commandString, command);
        boolean commandResult = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(commandString);
        if (commandResult) {
            command.setResult(SENT_TO_BARITONE);
        }
    }

    // ====================================================
    // region<World Commands>

    private void doWorldCommand(Command command) {
        switch (command.action) {
            case CREATE:
                logger.info("loading world {}", command.world_name);
                create(command.world_name, command.world_seed);
                break;
            case LOAD:
                logger.info("loading world {}", command.world_name);
                load(command.world_name);
                break;
            case UNLOAD:
                logger.info("unloading world");
                unload();
                break;
            case RELOAD:
                SaveProperties properties = minecraftServer.getSaveProperties();
                LevelInfo levelInfo = properties.getLevelInfo();
                String worldName = levelInfo.getLevelName();
                logger.info("Reloading current world {}", worldName);
                load(worldName);
                break;
        }
    }

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

    public void create(String worldName, String seed) {
        unload();
        if (seed.compareToIgnoreCase("village") == 0) {
            seed = "-6479230070218025543";  // plains biome with a nearby village!
        }
        MinecraftWorldCreator minecraftWorldCreator = new MinecraftWorldCreator(worldName, seed);
        minecraftWorldCreator.createLevel(minecraftClient);
    }
    // endregion
    // ====================================================

    // ====================================================
    // region<Mission Commands>
    private void doMissionCommand(Command command) {
        switch (command.action) {
            case GIVE:
                Item item = MinecraftHelpers.findBestItemMatch(command);
                sendGivePlayerInventoryMessage(item, command.quantity, command.inventory_slot_start);
                break;
            case CLEAR:
                sendClearPlayerInventoryMessage();
        }
    }

    @Environment(EnvType.CLIENT)
    private void sendGivePlayerInventoryMessage(Item item, Integer quantity, Integer inventory_position_start) {
        ItemStack stack = new ItemStack(item, quantity);

        PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
        passedData.writeEnumConstant(Command.ActionName.GIVE);
        passedData.writeItemStack(stack);
        passedData.writeInt(inventory_position_start);
        ClientSidePacketRegistry.INSTANCE.sendToServer(INVENTORY_CHANGE_ID, passedData);
    }

    @Environment(EnvType.CLIENT)
    private void sendClearPlayerInventoryMessage() {
        PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
        passedData.writeEnumConstant(Command.ActionName.CLEAR);
        ClientSidePacketRegistry.INSTANCE.sendToServer(INVENTORY_CHANGE_ID, passedData);
    }

    void initializeServerInventoryListener() {
        ServerSidePacketRegistry.INSTANCE.register(INVENTORY_CHANGE_ID, (packetContext, attachedData) -> {
            Command.ActionName action = attachedData.readEnumConstant(Command.ActionName.class);
            PlayerEntity player = packetContext.getPlayer();
            PlayerInventory inventory = player.inventory;
            if (action == Command.ActionName.CLEAR) {
                packetContext.getTaskQueue().execute(inventory::clear);
            } else {  // action is "GIVE"
                ItemStack stack = attachedData.readItemStack();
                int inventory_position_start = attachedData.readInt();
                packetContext.getTaskQueue().execute(() -> {
                    inventory.insertStack(inventory_position_start, stack);
                });
            }
        });
    }


    // endregion
    // ====================================================

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
