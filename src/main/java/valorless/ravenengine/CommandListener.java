package valorless.ravenengine;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import valorless.valorlessutils.logging.Log;

/**
 * Handles command execution for all commands registered by {@link Engine}.
 *
 * <p>Currently supports the following sub-commands for {@code /ravenengine}:</p>
 * <ul>
 *   <li>{@code reload} — reloads the plugin configuration. Requires the
 *       {@code ravencrest.reload} permission.</li>
 * </ul>
 */
public class CommandListener implements CommandExecutor {

    /**
     * Processes an incoming command sent to the plugin.
     *
     * <p>Logs the sender, command, label, and all arguments at debug level,
     * then dispatches to the appropriate sub-command handler.</p>
     *
     * @param sender  the source of the command (player, console, etc.)
     * @param command the command that was executed
     * @param label   the alias used to invoke the command
     * @param args    the arguments passed after the command label
     * @return {@code true} if the command was handled successfully;
     *         {@code false} to show the usage message defined in {@code plugin.yml}
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    	Log.debug(Engine.getPlugin(), "Sender: " + sender.getName());
    	Log.debug(Engine.getPlugin(), "Command: " + command.toString());
    	Log.debug(Engine.getPlugin(), "Label: " + label);
    	for(String a : args) {
    		Log.debug(Engine.getPlugin(), "Argument: " + a);
    	}
		if (args.length == 1){
			if(args[0].equalsIgnoreCase("reload") && sender.hasPermission("ravencrest.reload")) {
				Engine.getInstance().reload();
				sender.sendMessage("§7[§aEngine§7] §aReloaded.");
				return true;
			}
		}
        return false;
    }

}
