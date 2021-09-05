import org.slf4j.LoggerFactory;

public class MockBaritoneConnector implements BaritoneAdapter {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MockBaritoneConnector.class);

    @Override
    public Command.Result sendCommand(Command command) {
        String commandString = command.toBaritioneCommand();
        logger.info("Running Command string '{}' from command {}", commandString, command);
        //ignore the command during testing!
        return Command.Result.SENT_TO_BARITONE;
    }

    @Override
    public nrl.actorsim.minecraft.Command.Result getStatus(Command command) {
        return null;
    }
}
