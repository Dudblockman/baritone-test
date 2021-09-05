package nrl.actorsim.minecraft;

public interface BaritoneAdapter {
    public Command.Result sendCommand(Command command);
    public Command.Result getStatus(Command command);
}
