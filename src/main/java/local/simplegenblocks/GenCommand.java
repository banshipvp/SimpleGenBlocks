package local.simplegenblocks;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public class GenCommand implements CommandExecutor {

    private final SimpleGenBlocksPlugin plugin;

    public GenCommand(SimpleGenBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            handleGive(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only. Use /gen give from console.");
            return true;
        }

        plugin.getHud().open(player, plugin.getEconomy().getBalance(player));
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplegenblocks.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /gen give <player> <item> [amount]");
            sender.sendMessage("§7Items: cobble, obsidian, quartz, sand, lavagen, lavaup, lavadown, fatlava, fatwater");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        GenItemType type = parseType(args[2]);
        if (type == null) {
            sender.sendMessage("§cUnknown item type: " + args[2]);
            sender.sendMessage("§7Valid: cobble, obsidian, quartz, sand, lavagen, lavaup, lavadown, fatlava, fatwater");
            return;
        }

        BlockFace lavaDirection = parseLavaDirection(args[2]);

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cAmount must be a number.");
                return;
            }
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive.");
                return;
            }
        }

        int finalAmount = type.isStackable() ? Math.min(64, amount) : 1;
        ItemStack item;
        if (type == GenItemType.LAVA_GEN_BUCKET && lavaDirection != null) {
            item = plugin.getItemFactory().createDirectionalLavaGenBucket(lavaDirection);
        } else {
            item = plugin.getItemFactory().createPurchasedItem(type, finalAmount);
        }

        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            target.sendMessage("§eInventory full: extra items were dropped at your feet.");
        }

        sender.sendMessage("§aGave §f" + finalAmount + "x " + type.getDisplayName() + " §ato §f" + target.getName() + "§a.");
        target.sendMessage("§aYou received §f" + finalAmount + "x " + type.getDisplayName() + "§a.");
    }

    private GenItemType parseType(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return switch (key) {
            case "cobble", "cobblegen", "cobblestone", "cobblestonegen" -> GenItemType.COBBLE_GEN;
            case "obsidian", "obsidiangen" -> GenItemType.OBSIDIAN_GEN;
            case "quartz", "quartzgen" -> GenItemType.QUARTZ_GEN;
            case "sand", "sandgen" -> GenItemType.SAND_GEN;
            case "lavagen", "lavagenbucket", "lgb", "lavaup", "lavadown" -> GenItemType.LAVA_GEN_BUCKET;
            case "fatlava", "fatlavabucket", "flb" -> GenItemType.FAT_LAVA_BUCKET;
            case "fatwater", "fatwaterbucket", "fwb" -> GenItemType.FAT_WATER_BUCKET;
            default -> null;
        };
    }

    private BlockFace parseLavaDirection(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return switch (key) {
            case "lavadown" -> BlockFace.DOWN;
            case "lavaup", "lavagen", "lavagenbucket", "lgb" -> BlockFace.UP;
            default -> null;
        };
    }
}
