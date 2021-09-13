package nrl.actorsim.minecraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.item.Item;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

import static nrl.actorsim.minecraft.Command.ActionName.*;
import static nrl.actorsim.minecraft.Command.CommandType.*;
import static nrl.actorsim.minecraft.Command.Result.*;

public class Command implements Serializable {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(Command.class);

    public enum Result {
        UNKNOWN,
        SENT_TO_BARITONE,
        EXECUTING,
        SUCCESS,
        FAIL
    }

    enum CommandType {
        INVALID,
        BARITONE,
        CUSTOM,
        WORLD,
        MISSION
    }

    enum ActionName {
        UNSPECIFIED(INVALID),
        GOTO(BARITONE),
        MINE(BARITONE),
        FARM(BARITONE),

        CANCEL(CUSTOM),
        //PAUSE(CUSTOM), // pauses the current command
        //RESUME(CUSTOM), // resumes the previous command that was paused
        CRAFT(CUSTOM),
        SMELT(CUSTOM),

        CREATE(WORLD),
        LOAD(WORLD),
        RELOAD(WORLD),
        UNLOAD(WORLD),

        //Proposed mission commands
        START(MISSION),
        STOP(MISSION),
        GIVE(MISSION),
        CLEAR(MISSION);

        public CommandType type;

        ActionName(CommandType type) {
            this.type = type;
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public Integer id;
    public ActionName action;

    //NB: some commands accept [quantity] item; for example "craft 2 sticks" or "mine 3 iron_ore"
    public Integer quantity;
    public String item;
    public Integer inventory_slot_start;
    public Integer inventory_slot_end;

    //NB: goto commands accept coordinates; for example "goto x z [y]"
    public Integer x;
    public Integer z;
    public Integer y;

    public String world_name;
    public String world_seed;

    public Result result = Result.UNKNOWN;

    public boolean __Command__ = true; // a "fake" member to align with the python side
    public int maxsize;  // helps determine unset integer values

    // ====================================================
    // region<Constructors and Initializers>

    public Command(ActionName action) {
        this();
        this.action = action;
    }

    public Command() {
        quantity = 1;
        item = "";
        world_name = "";
    }

    // endregion
    // ====================================================

    // ====================================================
    // region<Object Overrides>

    @Override
    public String toString() {
        String value = id + ":" + action;
        if (x != null) {
            value += " x" + Integer.toString(x);
        }
        if (z != null) {
            value += " z" + Integer.toString(z);
        }
        if (y != null) {
            value += " y" + Integer.toString(y);
        }
        value += " result:" + result;
        return value;
    }

    // endregion
    // ====================================================

    // ====================================================
    // region<Result>

    public void setResult(Result result) {
        this.result = result;
    }

    public boolean isFinishedExecuting() {
        return result == SUCCESS
                || result == Result.FAIL;
    }

    public boolean isSentToBaritone() {
        return result == SENT_TO_BARITONE;
    }

    public boolean isExecuting() {
        return result == EXECUTING;
    }

    // endregion
    // ====================================================

    // ====================================================
    // region<Command type checking>

    public boolean isUnspecified() {
        return action == UNSPECIFIED;
    }

    public boolean isSuccess() {
        return result == SUCCESS;
    }

    // endregion
    // ====================================================

    // ====================================================
    // region<JSON processing>

    public static Command fromJSON(String json) {
        logger.debug("Attempting to parse '{}'", json);
        try {
            return mapper.readValue(json, Command.class);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing '{}'", json, e);
        }
        return null;
    }

    String toJSON() {
        try {
            String json = mapper.writeValueAsString(this);
            return json;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "NOT PARSED";
    }

    // endregion
    // ====================================================

    // ====================================================
    // region<Time related>

    public boolean hasElapsed(Instant start, Duration timeout) {
        Instant now = Instant.now();
        return start.plus(timeout).isBefore(now);
    }


    // endregion
    // ====================================================

    // ====================================================
    // region<Baritone specific>

    public boolean isBaritoneCommand() {
        return this.action.type == BARITONE;
    }

    public boolean isGoto() {
        return action == GOTO;
    }

    public boolean isMine() {
        return action == MINE;
    }

    public boolean isFarm() {
        return action == FARM;
    }

    public String toBaritoneCommand() {
        String command = "";
        if (isGoto()) {
            command += "goto";
            String xString = " " + x.toString();
            String zString = " " + z.toString();
            String yString = "";
            if ((y != null)
                && (y != maxsize)) {
                yString += " " + y.toString();
            }
            command += xString + yString + zString;
        } else if (isFarm()) {
            command += "farm";
        } else if (isMine()) {
            command += "mine";
            String itemName = item;
            Item realItem = MinecraftHelpers.findBestItemMatch(this);
            if (realItem != null) {
                itemName = realItem.getTranslationKey();
            }
            command += " " + quantity + " " + itemName;
        }
        return command;
    }


    // endregion
    // ====================================================

    // ====================================================
    // region<Custom Commands>

    public boolean isCancel() {
        return action == CANCEL;
    }

    public boolean isCraft() {
        return action == CRAFT;
    }

    public boolean isSmelt() {
        return action == SMELT;
    }

    // endregion
    // ====================================================


    // ====================================================
    // region<World commands>

    public boolean isWorldCommand() {
        return this.action.type == WORLD;
    }

    public boolean isCreate() {
        return action == CREATE;
    }

    public boolean isLoad() {
        return action == LOAD;
    }

    public boolean isReload() {
        return action == RELOAD;
    }

    public boolean isUnload() {
        return action == UNLOAD;
    }

    // endregion
    // ====================================================

    // ====================================================
    // region<Mission Commands>

    public boolean isMissionCommand() {
        return this.action.type == MISSION;
    }

    public boolean isStart() {
        return action == START;
    }

    public boolean isStop() {
        return action == STOP;
    }


    // endregion
    // ====================================================


}
