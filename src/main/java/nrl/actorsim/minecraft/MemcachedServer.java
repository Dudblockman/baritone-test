/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package nrl.actorsim.minecraft;

import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedNode;
import nrl.actorsim.utils.WorkerThread;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

import static nrl.actorsim.minecraft.Command.Result.EXECUTING;
import static nrl.actorsim.minecraft.Command.Result.SUCCESS;


public class MemcachedServer extends WorkerThread {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MemcachedServer.class);

    public static final String COMMAND_KEY = "minecraft_command";
    public static final String RESULT_KEY = "minecraft_result";
    public static Duration WAKE_UP_TIMEOUT = Duration.ofSeconds(2); //Duration.ofMillis(250);
    public static Duration INIT_TIMEOUT = Duration.ofSeconds(1);
    public static Duration TEST_BARITONE_TRANSITION_TIME = Duration.ofSeconds(5);

    MemcachedClient memcache;
    boolean needsInit = true;
    boolean printCommandWaitStart = true;
    boolean printBaritoneWaitStart = true;


    Instant currentCommandStart;
    Command currentCommand;
    Command lastCommand;
    boolean isBaritoneTestMode = false;

    MinecraftConnector minecraftConnector;

    public MemcachedServer() {
        super(Options.builder().shortName("MemCacheClient").build());
    }

    public void initAndStart(MinecraftConnector minecraftConnector) throws IOException, InterruptedException {
        this.minecraftConnector = minecraftConnector;
        memcache = new MemcachedClient(new InetSocketAddress("127.0.0.1", 11211));
        start();
        setWakeUpCall(INIT_TIMEOUT);
    }

    public void testInitAndStart() throws IOException, InterruptedException {
        this.minecraftConnector = null;
        memcache = new MemcachedClient(new InetSocketAddress("127.0.0.1", 11211));
        start();
        setWakeUpCall(INIT_TIMEOUT);
    }

    public void init() {
        checkMemcacheServer();
        memcache.delete(COMMAND_KEY);
        memcache.delete(RESULT_KEY);
        needsInit = false;
    }

    public boolean checkMemcacheServer() {
        boolean activeNodeFound = false;
        for (MemcachedNode node : memcache.getNodeLocator().getAll()) {
            if (node.isActive()) {
                activeNodeFound = true;
            }
        }
        if (! activeNodeFound) {
            String msg = "Failed to connect to Memcached server.  Did you forget to start memcached?";
            logger.error(msg);
            memcache.shutdown();
            throw new UnsupportedOperationException(msg);
        }
        return activeNodeFound;
    }

    @Override
    public WorkResult performWork() {
        if (needsInit) {
            init();
        }
        if (isWaitingOnBaritone()) {
            checkBaritoneProgressAndUpdateResult();
        } else {
            checkForNewCommandAndExecute();
        }
        setWakeUpCall(WAKE_UP_TIMEOUT);
        return WorkResult.CONTINUE;
    }

    // ====================================================
    // region<Baritone Commands>

    private boolean isWaitingOnBaritone() {
        return currentCommand != null;
    }

    private void checkBaritoneProgressAndUpdateResult() {
        if (printBaritoneWaitStart) {
            logger.debug("Waiting for baritone to finish {}", currentCommand);
            printBaritoneWaitStart = false;
        }
        //check on baritone progress
        updateBaritioneCommandStatus();
        sendResult(currentCommand);
        if (currentCommand.isFinishedExecuting()) {
            lastCommand = currentCommand;
            currentCommand = null;
            currentCommandStart = null;
            printBaritoneWaitStart = true;
        }
    }

    private void updateBaritioneCommandStatus() {
        if (isBaritoneTestMode) {
            if (currentCommand.hasElapsed(currentCommandStart, TEST_BARITONE_TRANSITION_TIME)
                    && currentCommand.isSentToBaritone()) {
                    currentCommand.setResult(EXECUTING);
            } else if (currentCommand.hasElapsed(currentCommandStart, TEST_BARITONE_TRANSITION_TIME.multipliedBy(2))
                    && currentCommand.isExecuting()) {
                currentCommand.setResult(SUCCESS);
            }
        } else {
            //do some magic checking on the baritone status
        }
    }

    // endregion
    // ====================================================

    private void checkForNewCommandAndExecute() {
        if (printCommandWaitStart) {
            logger.debug("Waiting for new command...");
            printCommandWaitStart = false;
        }
        Command command = readCommand();
        if (command != null) {
            logger.info("Processing command {}", command);
            runCommand(command);
            sendResult(command);
            printCommandWaitStart = true;
        }
    }

    private Command readCommand() {
        checkMemcacheServer();
        Object resultRaw = memcache.get(COMMAND_KEY);
        Command command = null;
        if (resultRaw != null) {
            logger.debug("Recieved command json: {}", resultRaw);
            memcache.delete(COMMAND_KEY);
            command =  Command.fromJSON(resultRaw.toString());
        }
        return command;
    }

    private void runCommand(Command command) {
        minecraftConnector.run(command);
        if (isBaritoneTestMode) {
            currentCommandStart = Instant.now();
        }
    }

    private void sendResult(Command command) {
        logger.info("Sending result {}", command);
        String jsonResult = command.toJSON();
        int neverExpires = 0;
        memcache.set(RESULT_KEY, neverExpires, jsonResult);
    }
}
