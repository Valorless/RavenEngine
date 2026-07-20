package valorless.ravenengine;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides tab-completion suggestions for the {@code /ravenengine} command.
 *
 * <p>Suggestions are filtered by partial input so that only matching entries
 * are returned, consistent with Bukkit's tab-completion conventions.</p>
 */
public class TabCompletion implements TabCompleter {

    /**
     * Returns a list of possible completions for the current argument being typed.
     *
     * <p>For the first argument position, the available sub-commands are offered:
     * <ul>
     *   <li>{@code reload}</li>
     * </ul>
     * Only entries that start with the characters already typed are included.</p>
     *
     * @param sender  the source of the tab-completion request
     * @param command the command being tab-completed
     * @param alias   the alias used to invoke the command
     * @param args    the arguments typed so far, where the last element is the
     *                one currently being completed
     * @return a mutable list of matching completions; never {@code null}
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if(args.length == 1){
            List<String> subCommands = new ArrayList<>();
            subCommands.add("reload");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        }
        return completions;
    }
}