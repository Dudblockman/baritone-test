package nrl.actorsim.minecraft;

import baritone.api.BaritoneAPI;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.LevelInfo;
import nrl.actorsim.utils.WorkerThread;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import static nrl.actorsim.minecraft.Command.ActionName.*;
import static nrl.actorsim.minecraft.Command.Result.*;

public class MinecraftConnector {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MinecraftConnector.class);
    final static String CONNECTOR_NAMESPACE = "nrl.actorsim.minecraft";

    public static Identifier INVENTORY_CHANGE_ID = new Identifier(CONNECTOR_NAMESPACE, "inventory-change");

    private static MinecraftConnector instance;
    private static MemcachedServer memcachedServer;

    private ConnectorState state;

    private MinecraftServer minecraftServer;
    private MinecraftClient minecraftClient;

    final Queue<Command> commandQueue = new LinkedList<>();
    Optional<Command> currentCommand = Optional.empty();
    Optional<Command> pausedCommand = Optional.empty();
    Optional<Command> interruptCommand = Optional.empty();
    ConnectorWorker worker;


    private MinecraftConnector() {
    }

    public static MinecraftConnector getInstance() {
        if (instance == null) {
            instance = new MinecraftConnector();
        }
        return instance;
    }

    public void initServerInstance() {
        instance.initializeServerInventoryListener();
        instance.state = ConnectorState.NOT_LOADED;
    }

    public void initClientInstance() {
        registerClientEvents();
        registerServerAndWorldEvents();
        if (memcachedServer == null) {
            try {
                memcachedServer = new MemcachedServer();
                memcachedServer.initAndStart(instance);
                System.out.print("MemcachedServer started and running");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        WorkerThread.Options options = WorkerThread.Options.builder()
                .shortName("MCC")
                .workerLock(commandQueue)
                .build();
        worker = new ConnectorWorker(options);
        worker.start();

        state = ConnectorState.NOT_LOADED;
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
    // region<CommandQueue and ConnectorWorker >

    void enqueue(Command command) {
        logger.info("Enqueuing command {}", command);
        if (command.isInterrupt()) {
            interruptCommand = Optional.of(command);
        } else {
            synchronized (commandQueue) {
                if (commandQueue.offer(command)) {
                    command.setResult(ENQUEUE_SUCCESS);
                } else {
                    command.setResult(ENQUEUE_FAIL);
                }
            }
        }
        worker.notifyWorkerThereIsWork();
    }

    class ConnectorWorker extends WorkerThread {
        protected ConnectorWorker(Options options) {
            super(options);
        }

        @Override
        public WorkResult performWork() {
            boolean commandWasRun = false;
            if (interruptCommand.isPresent()) {
                MinecraftConnector.this.run(interruptCommand.get());
            } else {
                commandWasRun = setCurrentCommandIfQueued();
            }
            int queueSize = commandQueue.size();
            logger.info("Current queue has {} commands:", queueSize);
            commandQueue.forEach(command -> logger.info("  command:{}", command));
            if (commandWasRun
                    && queueSize > 0) {
                return WorkResult.MORE_WORK;  //cycle to make sure we empty fast running commands
            } else {
                return WorkResult.CONTINUE;  //better wait until we are notified
            }
        }

        private boolean setCurrentCommandIfQueued() {
            if (currentCommand.isPresent()
                    && currentCommand.get().isFinishedExecuting()) {
                currentCommand = Optional.empty();
            }
            boolean commandWasRun = false;
            if (!currentCommand.isPresent()) {
                Command command = getNextCommand();
                if (command != null) {
                    currentCommand = Optional.of(command);
                    MinecraftConnector.this.run(command);
                    commandWasRun = true;
                }
            }
            return commandWasRun;
        }

        @Nullable
        private Command getNextCommand() {
            Command command = null;
            synchronized (commandQueue) {
                if (!commandQueue.isEmpty()) {
                    command = commandQueue.remove();
                }
            }
            return command;
        }
    }

    private void setSuccessAndNotify(Command command) {
        command.setResult(SUCCESS);
        worker.notifyWorkerThereIsWork();
    }

    // endregion
    // ====================================================


    // ====================================================
    // region<Register client methods>

    private void registerClientEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(MinecraftConnector::addClient);
        ClientLifecycleEvents.CLIENT_STOPPING.register(MinecraftConnector::removeClient);
    }


    private void registerServerAndWorldEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(MinecraftConnector::addServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(MinecraftConnector::removeServer);

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
        if (playerLightLevelAtOrAbove(7)) {
            return;
        }
        placeTorchAtPlayerFeet();
    }


    // endregion
    // ====================================================

    // ====================================================
    // region<World State>
    public boolean playerLightLevelAtOrAbove(int value) {
        BlockPos playerStanding = new BlockPos(minecraftClient.player.getPos());
        int currentLightLevel = minecraftClient.world.getLightLevel(LightType.BLOCK, playerStanding);
        logger.debug("The current light level is {}", currentLightLevel);
        return value <= currentLightLevel;
    }

    // endregion
    // ====================================================


    public void run(Command command) {
        if (command.isInterrupt()) {
            switch (command.action) {
                case CANCEL:
                    synchronized (commandQueue) {
                        commandQueue.clear();
                    }
                case STOP:
                    currentCommand.ifPresent(Command::setResultToStopped);
                    stopExecutingCurrentCommand();
                    break;
                case PAUSE:
                    currentCommand.ifPresent(Command::setResultToPaused);
                    pausedCommand = currentCommand;
                    stopExecutingCurrentCommand();
                    break;
                case RESUME:
                    resumeExecutionOfPausedCommand();
                    break;
            }
            interruptCommand = Optional.empty();
        } else {
            if (command.isWorldCommand()) {
                doWorldCommand(command);
            } else if (command.isBaritoneCommand()) {
                sendBaritoneCommand(command);
            } else if (command.isMissionCommand()) {
                doMissionCommand(command);
            } else if (command.isCustomCommand()) {
                doCustomCommand(command);
            }
        }
    }

    private void stopExecutingCurrentCommand() {
        stopBaritoneIfCurrentTask();
        currentCommand = Optional.empty();
    }

    private void stopBaritoneIfCurrentTask() {
        if (currentCommand.isPresent()) {
            if (currentCommand.get().isBaritoneCommand()) {
                sendBaritoneCommand(new Command(STOP));
            }
        }
    }

    private void resumeExecutionOfPausedCommand() {
        currentCommand = pausedCommand;
        pausedCommand = Optional.empty();
        if (currentCommand.isPresent()) {
            currentCommand.get().setResultToExecuting();
            if (currentCommand.get().isBaritoneCommand()) {
                sendBaritoneCommand(currentCommand.get());
            }
        }
    }

    public void sendBaritoneCommand(Command command) {
        String commandString = command.toBaritoneCommand();
        logger.info("Running Command string '{}' from command {}", commandString, command);
        minecraftClient.execute(() -> {
            boolean commandResult = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(commandString);
            if (commandResult) {
                command.setResult(SENT_TO_BARITONE);
            }
        });
    }


    // ====================================================
    // region<World Commands>

    private void doWorldCommand(Command command) {
        switch (command.action) {
            case CREATE:
                logger.info("loading world {}", command.world_name);
                create(command.world_name, command.world_seed);
                setSuccessAndNotify(command);
                break;
            case LOAD:
                logger.info("loading world {}", command.world_name);
                load(command.world_name);
                setSuccessAndNotify(command);
                break;
            case UNLOAD:
                logger.info("unloading world");
                unload();
                setSuccessAndNotify(command);
                break;
            case RELOAD:
                SaveProperties properties = minecraftServer.getSaveProperties();
                LevelInfo levelInfo = properties.getLevelInfo();
                String worldName = levelInfo.getLevelName();
                logger.info("Reloading current world {}", worldName);
                load(worldName);
                setSuccessAndNotify(command);
                break;
        }
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
            MinecraftClient.getInstance().execute(MinecraftConnector.this::unload_impl);
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
                sendInventoryMessageGive(item, command.quantity, command.inventory_slot_start);
                setSuccessAndNotify(command);
                break;
            case CLEAR:
                sendInventoryMessageClear();
                setSuccessAndNotify(command);
                break;
        }
    }


    // endregion
    // ====================================================

    // ====================================================
    // region<Custom Commands>
    private void doCustomCommand(Command command) {
        switch (command.action) {
            case CRAFT:
                //TODO Gardone: here's where you link to your crafting code; for now it just gives the item without checking
                Item item = MinecraftHelpers.findBestItemMatch(command);
                sendInventoryMessageGive(item, command.quantity, command.inventory_slot_start);
                setSuccessAndNotify(command);
                break;
            case SMELT:
                //TODO Gardone: here's where you link to your smelting code; for now it just gives the item without checking
                item = MinecraftHelpers.findBestItemMatch(command);
                sendInventoryMessageGive(item, command.quantity, command.inventory_slot_start);
                setSuccessAndNotify(command);
                break;
            case TICK:
                int tickRate = command.quantity;
                //TODO Logan: here's where you link our tick rate change
                break;
        }
    }

    // endregion
    // ====================================================


    // ====================================================
    // region<Torch Helpers>

    private void placeTorchAtPlayerFeet() {
        BlockPos playerStanding = new BlockPos(minecraftClient.player.getPos());
        if (canPlaceTorchAtBottomOf(playerStanding)) {
            attemptPlaceTorchAt(playerStanding);
        }
    }

    public boolean canPlaceTorchAtBottomOf(BlockPos blockPos) {
        return (minecraftClient.world.getBlockState(blockPos).getFluidState().isEmpty() &&
                Block.sideCoversSmallSquare(minecraftClient.world, blockPos.down(), Direction.UP));
    }

    private void attemptPlaceTorchAt(BlockPos blockPos) {
        ClientPlayerEntity player = minecraftClient.player;
        ClientWorld world = minecraftClient.world;
        //cheat by sending a "look" to the server without updating the client state
        PlayerMoveC2SPacket.LookOnly packet = new PlayerMoveC2SPacket.LookOnly(player.getYaw(0), 90.0F, true);
        player.networkHandler.sendPacket(packet);

        Vec3d bottomFace = Vec3d.ofBottomCenter(blockPos);
        BlockHitResult blockHitResult = new BlockHitResult(bottomFace, Direction.DOWN, blockPos, false);
        if (moveToOffHand(Items.TORCH)) {
            Hand hand = Hand.OFF_HAND;
            ActionResult one = minecraftClient.interactionManager.interactBlock(player, world, hand, blockHitResult);
            ActionResult two = minecraftClient.interactionManager.interactItem(player, world, hand);
            if (one.isAccepted() && two.isAccepted()) {
                logger.info("placed a torch");
            }
        }
    }

    // endregion
    // ====================================================


    // ====================================================
    // region<Inventory Control>

    private boolean moveToOffHand(Item item) {
        ClientPlayerEntity player = minecraftClient.player;
        PlayerInventory inventory =  player.inventory;
        boolean torchInOffHand = false;
        if (player.getOffHandStack().getItem().equals(item)) {
            torchInOffHand = true;
        } else if (player.getOffHandStack().isEmpty()) {
            ItemStack stack = new ItemStack(item);
            int index = inventory.getSlotWithStack(stack);
            if (index >= 0) {
                sendInventoryMessageSwapToHotbar(index);
                torchInOffHand = true;
            }
        }
        return torchInOffHand;
    }

    @Environment(EnvType.CLIENT)
    private void sendInventoryMessageGive(Item item, Integer quantity, Integer inventory_position_start) {
        ItemStack stack = new ItemStack(item, quantity);

        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        buffer.writeEnumConstant(Command.ActionName.GIVE);
        buffer.writeItemStack(stack);
        buffer.writeInt(inventory_position_start);
        ClientPlayNetworking.send(INVENTORY_CHANGE_ID, buffer);
    }

    @Environment(EnvType.CLIENT)
    private void sendInventoryMessageClear() {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        buffer.writeEnumConstant(CLEAR);
        ClientPlayNetworking.send(INVENTORY_CHANGE_ID, buffer);
    }

    @Environment(EnvType.CLIENT)
    private void sendInventoryMessageSwapToHotbar(int index) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        buffer.writeEnumConstant(Command.ActionName.SWAP_TO_OFFHAND);
        buffer.writeInt(index);
        ClientPlayNetworking.send(INVENTORY_CHANGE_ID, buffer);
    }



    void initializeServerInventoryListener() {
        ServerPlayNetworking.registerGlobalReceiver(INVENTORY_CHANGE_ID, (server, player, handler, buf, responseSender) -> {
            Command.ActionName action = buf.readEnumConstant(Command.ActionName.class);
            PlayerInventory inventory = player.inventory;
            if (action == CLEAR) {
                server.execute(inventory::clear);
            } else if (action == GIVE) {
                ItemStack stack = buf.readItemStack();
                int inventory_position_start = buf.readInt();
                server.execute(() -> {
                    inventory.insertStack(inventory_position_start, stack);
                });
            } else if (action == SWAP_TO_OFFHAND) {
                int index = buf.readInt();
                server.execute(() -> {
                    ItemStack toPlace = inventory.main.get(index);
                    ItemStack inOffHand = inventory.offHand.isEmpty() ? ItemStack.EMPTY : inventory.offHand.get(0);
                    player.inventory.offHand.set(0, toPlace);
                    player.inventory.setStack(index, inOffHand);
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
