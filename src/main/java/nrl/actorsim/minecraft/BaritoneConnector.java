package nrl.actorsim.minecraft;

import baritone.api.BaritoneAPI;
import org.slf4j.LoggerFactory;

public class BaritoneConnector implements BaritoneAdapter {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(BaritoneConnector.class);

    @Override
    public Command.Result sendCommand(Command command) {
        String commandString = command.toBaritoneCommand();
        logger.info("Running Command string '{}' from command {}", commandString, command);
        boolean commandResult = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(commandString);
        if (commandResult) {
            return Command.Result.SENT_TO_BARITONE;
        }
        return Command.Result.UNKNOWN;
    }

    @Override
    public Command.Result getStatus(Command command) {
        //do some magic to check on Baritone
        return Command.Result.UNKNOWN;
    }

}
