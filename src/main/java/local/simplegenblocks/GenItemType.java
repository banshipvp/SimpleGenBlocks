package local.simplegenblocks;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum GenItemType {
    COBBLE_GEN("cobble_gen", "§7§lCobblestone Gen Block", Material.COBBLESTONE, 1500, Material.COBBLESTONE, true),
    OBSIDIAN_GEN("obsidian_gen", "§8§lObsidian Gen Block", Material.OBSIDIAN, 7500, Material.OBSIDIAN, true),
    QUARTZ_GEN("quartz_gen", "§f§lQuartz Gen Block", Material.QUARTZ_BLOCK, 3500, Material.QUARTZ_BLOCK, true),
    SAND_GEN("sand_gen", "§e§lSand Gen Block", Material.SAND, 2000, Material.SAND, true),
    LAVA_GEN_BUCKET("lava_gen_bucket", "§6§lLava Gen Bucket", Material.LAVA_BUCKET, 25000, null, false),
    FAT_LAVA_BUCKET("fat_lava_bucket", "§c§lFat Lava Bucket", Material.LAVA_BUCKET, 12000, null, false),
    FAT_WATER_BUCKET("fat_water_bucket", "§b§lFat Water Bucket", Material.WATER_BUCKET, 12000, null, false);

    private static final Map<String, GenItemType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(GenItemType::getId, Function.identity()));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final double price;
    private final Material generatorOutput;
    private final boolean stackable;

    GenItemType(String id, String displayName, Material icon, double price, Material generatorOutput, boolean stackable) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.price = price;
        this.generatorOutput = generatorOutput;
        this.stackable = stackable;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public double getPrice() {
        return price;
    }

    public Material getGeneratorOutput() {
        return generatorOutput;
    }

    public boolean isGeneratorBlock() {
        return generatorOutput != null;
    }

    public boolean isLavaGenBucket() {
        return this == LAVA_GEN_BUCKET;
    }

    public boolean isFatBucket() {
        return this == FAT_LAVA_BUCKET || this == FAT_WATER_BUCKET;
    }

    public Material getFatBucketFluidType() {
        return this == FAT_LAVA_BUCKET ? Material.LAVA : Material.WATER;
    }

    public boolean isStackable() {
        return stackable;
    }

    public static GenItemType fromId(String id) {
        if (id == null) return null;
        return BY_ID.get(id);
    }
}
