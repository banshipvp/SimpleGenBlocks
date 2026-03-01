package local.simplegenblocks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GenHud {

    public static final String TITLE_MAIN = "§6§lGenBlocks";
    public static final String TITLE_DIRECTION_PREFIX = "§6§lGen Direction: ";
    public static final String TITLE_LAVA_DIRECTION = "§6§lLava Gen Direction";

    private final GenItemFactory itemFactory;

    public GenHud(GenItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    public void open(Player player, double balance) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);

        // Generator block types
        inv.setItem(10, itemFactory.createShopDisplay(GenItemType.COBBLE_GEN));
        inv.setItem(11, itemFactory.createShopDisplay(GenItemType.OBSIDIAN_GEN));
        inv.setItem(12, itemFactory.createShopDisplay(GenItemType.QUARTZ_GEN));
        inv.setItem(13, itemFactory.createShopDisplay(GenItemType.SAND_GEN));

        // Special buckets
        inv.setItem(15, itemFactory.createShopDisplay(GenItemType.LAVA_GEN_BUCKET));
        inv.setItem(16, itemFactory.createShopDisplay(GenItemType.FAT_LAVA_BUCKET));
        inv.setItem(17, itemFactory.createShopDisplay(GenItemType.FAT_WATER_BUCKET));

        inv.setItem(22, createBalanceItem(balance));

        player.openInventory(inv);
    }

    public void openLavaDirection(Player player, double balance) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_LAVA_DIRECTION);

        inv.setItem(11, itemFactory.createDirectionalLavaGenBucket(BlockFace.UP));
        inv.setItem(15, itemFactory.createDirectionalLavaGenBucket(BlockFace.DOWN));

        ItemStack info = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§6§lLava Gen Bucket");
        meta.setLore(List.of(
                "§7Choose vertical direction.",
                "§7UP = build to world top",
                "§7DOWN = build to bedrock"
        ));
        info.setItemMeta(meta);
        inv.setItem(13, info);

        inv.setItem(18, createSimpleIcon(Material.ARROW, "§cBack to Block Types"));
        inv.setItem(26, createBalanceItem(balance));

        player.openInventory(inv);
    }

    public void openDirection(Player player, GenItemType type, double balance) {
        String plainName = type.getDisplayName().replaceAll("§.", "");
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_DIRECTION_PREFIX + plainName);

        // + layout for NSEW
        inv.setItem(4, itemFactory.createDirectionOptionDisplay(type, BlockFace.NORTH));
        inv.setItem(12, itemFactory.createDirectionOptionDisplay(type, BlockFace.WEST));
        inv.setItem(14, itemFactory.createDirectionOptionDisplay(type, BlockFace.EAST));
        inv.setItem(22, itemFactory.createDirectionOptionDisplay(type, BlockFace.SOUTH));

        // Side options for Up/Down
        inv.setItem(16, itemFactory.createDirectionOptionDisplay(type, BlockFace.UP));
        inv.setItem(25, itemFactory.createDirectionOptionDisplay(type, BlockFace.DOWN));

        // Center info
        ItemStack info = new ItemStack(type.getIcon());
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(type.getDisplayName());
        meta.setLore(List.of(
                "§7Choose a fixed direction.",
                "§7These are directional gen blocks.",
                "§7Max distance: §f319 blocks"
        ));
        info.setItemMeta(meta);
        inv.setItem(13, info);

        // Back + balance
        inv.setItem(18, createSimpleIcon(Material.ARROW, "§cBack to Block Types"));
        inv.setItem(26, createBalanceItem(balance));

        player.openInventory(inv);
    }

    private ItemStack createSimpleIcon(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBalanceItem(double balance) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§lYour Balance");
        meta.setLore(List.of("§a$" + format(balance)));
        item.setItemMeta(meta);
        return item;
    }

    private String format(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.format("%.2f", value);
    }
}
