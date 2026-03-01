package local.simplegenblocks;

import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GenItemFactory {

    public static final int FAT_BUCKET_CAPACITY = 320;
    public static final int FAT_BUCKET_FILL_UNITS = FAT_BUCKET_CAPACITY / 10;

    private final NamespacedKey typeKey;
    private final NamespacedKey unitsKey;
    private final NamespacedKey shopTypeKey;
    private final NamespacedKey directionKey;

    public GenItemFactory(NamespacedKey typeKey, NamespacedKey unitsKey, NamespacedKey shopTypeKey, NamespacedKey directionKey) {
        this.typeKey = typeKey;
        this.unitsKey = unitsKey;
        this.shopTypeKey = shopTypeKey;
        this.directionKey = directionKey;
    }

    public ItemStack createShopDisplay(GenItemType type) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7Price: §a$" + format(type.getPrice()));
        lore.add("§7");

        if (type.isGeneratorBlock()) {
            lore.add("§7Click to choose direction:");
            lore.add("§7N/E/S/W/Up/Down");
            lore.add("§7Max distance: §f319 blocks");
            lore.add("§eClick: Open direction menu");
        } else if (type.isLavaGenBucket()) {
            lore.add("§7Place low: builds lava sources up.");
            lore.add("§7Place high: builds lava sources down.");
            lore.add("§eLeft Click: Buy 1");
        } else {
            lore.add("§7Stores lots of liquid units.");
            lore.add("§7Capacity: §f" + FAT_BUCKET_CAPACITY + " units");
            lore.add("§7Fill per right-click: §f+" + FAT_BUCKET_FILL_UNITS + " units");
            lore.add("§eLeft Click: Buy 1");
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(shopTypeKey, PersistentDataType.STRING, type.getId());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPurchasedItem(GenItemType type, int amount) {
        int finalAmount = type.isStackable() ? Math.max(1, Math.min(64, amount)) : 1;
        ItemStack item = new ItemStack(type.getIcon(), finalAmount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName());

        List<String> lore = new ArrayList<>();

        if (type.isGeneratorBlock()) {
            meta.getPersistentDataContainer().set(directionKey, PersistentDataType.STRING, BlockFace.NORTH.name());
            lore.add("§7Generator block");
            lore.add("§7Direction: §fNORTH");
            lore.add("§7Max distance: §f319 blocks");
        } else if (type.isLavaGenBucket()) {
            meta.getPersistentDataContainer().set(directionKey, PersistentDataType.STRING, BlockFace.UP.name());
            lore.add("§7Places source lava to build limit.");
            lore.add("§7Direction: §fUP");
        } else {
            meta.getPersistentDataContainer().set(unitsKey, PersistentDataType.INTEGER, FAT_BUCKET_CAPACITY);
            lore.add("§7Stored Units: §f" + FAT_BUCKET_CAPACITY + "§7/" + FAT_BUCKET_CAPACITY);
            lore.add("§7Uses 1 unit per source placement.");
            lore.add("§7Fill right-click adds +" + FAT_BUCKET_FILL_UNITS + " units.");
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.getId());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDirectionOptionDisplay(GenItemType type, BlockFace direction) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName() + " §7[" + direction.name() + "]");

        List<String> lore = new ArrayList<>();
        lore.add("§7Direction: §f" + direction.name());
        lore.add("§7Max distance: §f319 blocks");
        lore.add("§7");
        lore.add("§7Price: §a$" + format(type.getPrice()));
        lore.add("§eLeft Click: Buy 1");
        lore.add("§eShift + Left: Buy 16");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(shopTypeKey, PersistentDataType.STRING, type.getId());
        meta.getPersistentDataContainer().set(directionKey, PersistentDataType.STRING, direction.name());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDirectionalGeneratorItem(GenItemType type, BlockFace direction, int amount) {
        int finalAmount = Math.max(1, Math.min(64, amount));
        ItemStack item = new ItemStack(type.getIcon(), finalAmount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName() + " §7[" + direction.name() + "]");

        List<String> lore = new ArrayList<>();
        lore.add("§7Generator block");
        lore.add("§7Direction: §f" + direction.name());
        lore.add("§7Max distance: §f319 blocks");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.getId());
        meta.getPersistentDataContainer().set(directionKey, PersistentDataType.STRING, direction.name());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDirectionalLavaGenBucket(BlockFace direction) {
        ItemStack item = new ItemStack(GenItemType.LAVA_GEN_BUCKET.getIcon(), 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(GenItemType.LAVA_GEN_BUCKET.getDisplayName() + " §7[" + direction.name() + "]");

        List<String> lore = new ArrayList<>();
        lore.add("§7Places source lava to build limit.");
        lore.add("§7Direction: §f" + direction.name());
        lore.add("§7");
        lore.add("§7Price: §a$" + format(GenItemType.LAVA_GEN_BUCKET.getPrice()));
        lore.add("§eLeft Click: Buy 1");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, GenItemType.LAVA_GEN_BUCKET.getId());
        meta.getPersistentDataContainer().set(shopTypeKey, PersistentDataType.STRING, GenItemType.LAVA_GEN_BUCKET.getId());
        meta.getPersistentDataContainer().set(directionKey, PersistentDataType.STRING, direction.name());
        item.setItemMeta(meta);
        return item;
    }

    public GenItemType getTypeFromShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String id = meta.getPersistentDataContainer().get(shopTypeKey, PersistentDataType.STRING);
        return GenItemType.fromId(id);
    }

    public GenItemType getTypeFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String id = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return GenItemType.fromId(id);
    }

    public BlockFace getDirectionFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String dir = meta.getPersistentDataContainer().get(directionKey, PersistentDataType.STRING);
        if (dir == null) return null;
        try {
            return BlockFace.valueOf(dir);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public int getFatBucketUnits(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer units = meta.getPersistentDataContainer().get(unitsKey, PersistentDataType.INTEGER);
        return units == null ? 0 : units;
    }

    public void setFatBucketUnits(ItemStack item, int units) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int safeUnits = Math.max(0, Math.min(FAT_BUCKET_CAPACITY, units));
        meta.getPersistentDataContainer().set(unitsKey, PersistentDataType.INTEGER, safeUnits);

        List<String> lore = new ArrayList<>();
        lore.add("§7Stored Units: §f" + safeUnits + "§7/" + FAT_BUCKET_CAPACITY);
        lore.add("§7Uses 1 unit per source placement.");
        lore.add("§7Fill right-click adds +" + FAT_BUCKET_FILL_UNITS + " units.");
        meta.setLore(lore);

        item.setItemMeta(meta);
    }

    private String format(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.format("%.0f", value);
    }
}
