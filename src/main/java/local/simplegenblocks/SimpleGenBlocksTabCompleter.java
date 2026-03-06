package local.simplegenblocks;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimpleGenBlocksTabCompleter implements TabCompleter {

    private static final List<String> ITEM_SUGGESTIONS = List.of(
            "cobble", "obsidian", "quartz", "sand", "lavagen", "lavaup", "lavadown", "fatlava", "fatwater"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (!hasAdmin(sender)) return List.of();
            return filter(List.of("give"), args[0]);
        }

        if (!hasAdmin(sender)) {
            return List.of();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(onlinePlayers(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(ITEM_SUGGESTIONS, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "2", "5", "10", "16", "32", "64"), args[3]);
        }

        return List.of();
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("simplegenblocks.admin") || sender.isOp();
    }

    private List<String> onlinePlayers() {
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            out.add(player.getName());
        }
        return out;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
