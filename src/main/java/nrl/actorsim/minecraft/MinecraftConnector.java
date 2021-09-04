package nrl.actorsim.minecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.LoggerFactory;

public class MinecraftConnector {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MinecraftConnector.class);

    private static MinecraftConnector instance;

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



    public Command.Result loadWorld(Command command) {
        EnvType envType = FabricLoader.getInstance().getEnvironmentType();
        if (envType == EnvType.CLIENT) {
            //if on client side, send message
        } else {
            //else, on server side, start world
            //MinecraftServer server = FabricLoader.getInstance().get
        }
        return Command.Result.UNKNOWN;
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
