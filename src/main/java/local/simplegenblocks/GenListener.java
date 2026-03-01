package local.simplegenblocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GenListener implements Listener {

    private static final int GENERATOR_MAX_DISTANCE = 319;

    private final SimpleGenBlocksPlugin plugin;
    private final GenItemFactory itemFactory;
    private final Map<String, BukkitTask> pendingGeneratorTasks = new HashMap<>();

    public GenListener(SimpleGenBlocksPlugin plugin) {
        this.plugin = plugin;
        this.itemFactory = plugin.getItemFactory();
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!GenHud.TITLE_MAIN.equals(title) && !title.startsWith(GenHud.TITLE_DIRECTION_PREFIX)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        // Main type HUD
        if (GenHud.TITLE_MAIN.equals(title)) {
            GenItemType type = itemFactory.getTypeFromShopItem(clicked);
            if (type == null) return;

            if (type.isGeneratorBlock()) {
                plugin.getHud().openDirection(player, type, plugin.getEconomy().getBalance(player));
                return;
            }

            if (type.isLavaGenBucket()) {
                plugin.getHud().openLavaDirection(player, plugin.getEconomy().getBalance(player));
                return;
            }

            int amount = 1;
            purchaseItem(player, type, amount, null);
            return;
        }

        if (GenHud.TITLE_LAVA_DIRECTION.equals(title)) {
            if (clicked.hasItemMeta() && clicked.getItemMeta() != null && clicked.getItemMeta().hasDisplayName()
                    && clicked.getItemMeta().getDisplayName().contains("Back to Block Types")) {
                plugin.getHud().open(player, plugin.getEconomy().getBalance(player));
                return;
            }

            GenItemType type = itemFactory.getTypeFromShopItem(clicked);
            if (type != GenItemType.LAVA_GEN_BUCKET) return;

            BlockFace direction = itemFactory.getDirectionFromItem(clicked);
            if (direction != BlockFace.UP && direction != BlockFace.DOWN) return;

            purchaseItem(player, type, 1, direction);
            return;
        }

        // Direction HUD
        if (title.startsWith(GenHud.TITLE_DIRECTION_PREFIX)) {
            if (clicked.hasItemMeta() && clicked.getItemMeta() != null && clicked.getItemMeta().hasDisplayName()
                    && clicked.getItemMeta().getDisplayName().contains("Back to Block Types")) {
                plugin.getHud().open(player, plugin.getEconomy().getBalance(player));
                return;
            }

            GenItemType type = itemFactory.getTypeFromShopItem(clicked);
            if (type == null || !type.isGeneratorBlock()) return;

            BlockFace direction = itemFactory.getDirectionFromItem(clicked);
            if (direction == null) return;

            int amount = event.isShiftClick() ? 16 : 1;
            purchaseItem(player, type, amount, direction);
        }
    }

    private void purchaseItem(Player player, GenItemType type, int amount, BlockFace direction) {
        double totalCost = type.getPrice() * amount;

        if (!plugin.getEconomy().has(player, totalCost)) {
            player.sendMessage("§cYou need §f$" + format(totalCost) + " §cto buy that.");
            return;
        }

        ItemStack purchased;
        if (type.isGeneratorBlock() && direction != null) {
            purchased = itemFactory.createDirectionalGeneratorItem(type, direction, amount);
        } else if (type.isLavaGenBucket() && direction != null) {
            purchased = itemFactory.createDirectionalLavaGenBucket(direction);
        } else {
            purchased = itemFactory.createPurchasedItem(type, amount);
        }
        plugin.getEconomy().withdrawPlayer(player, totalCost);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(purchased);
        int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();

        if (leftoverAmount > 0) {
            double refund = type.getPrice() * leftoverAmount;
            plugin.getEconomy().depositPlayer(player, refund);
            player.sendMessage("§eInventory was full, refunded §f$" + format(refund) + "§e.");
        }

        int delivered = amount - leftoverAmount;
        if (delivered <= 0) {
            player.sendMessage("§cInventory full.");
            return;
        }

        if (direction != null) {
            player.sendMessage("§aPurchased §f" + delivered + "x " + type.getDisplayName() + " §7[" + direction.name() + "] §afor §f$" + format(type.getPrice() * delivered));
        } else {
            player.sendMessage("§aPurchased §f" + delivered + "x " + type.getDisplayName() + " §afor §f$" + format(type.getPrice() * delivered));
        }
    }

    @EventHandler
    public void onGenBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        GenItemType type = itemFactory.getTypeFromItem(item);
        if (type == null || !type.isGeneratorBlock()) return;

        Player player = event.getPlayer();
        BlockFace direction = itemFactory.getDirectionFromItem(item);
        if (direction == null) {
            direction = resolveDirection(player);
        }
        final BlockFace generationDirection = direction;

        Material output = type.getGeneratorOutput();
        Block placed = event.getBlockPlaced();
        if (canOverwriteGenerated(placed.getType())) {
            placed.setType(output, false);
        }

        long delayTicks = Math.max(1L, plugin.getConfig().getLong("generator.place-delay-ticks", 40L));
        String key = blockKey(placed);

        BukkitTask existing = pendingGeneratorTasks.remove(key);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingGeneratorTasks.remove(key);

            int generated = 1;
            for (int i = 1; i < GENERATOR_MAX_DISTANCE; i++) {
                Block next = placed.getRelative(generationDirection, i);
                if (!canOverwriteGenerated(next.getType())) continue;
                next.setType(output, false);
                generated++;
            }

            player.sendMessage("§aGenerated §f" + generated + " " + output.name().toLowerCase() + " §ablocks toward §f" + generationDirection.name() + "§a.");
        }, delayTicks);

        pendingGeneratorTasks.put(key, task);
        player.sendMessage("§eGen placement queued (§f" + (delayTicks / 20.0) + "s§e). Left-click the origin block to cancel.");
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        ItemStack item = event.getItemStack();
        GenItemType type = itemFactory.getTypeFromItem(item);
        if (type == null) return;

        Block target = event.getBlockClicked().getRelative(event.getBlockFace());

        if (type.isLavaGenBucket()) {
            event.setCancelled(true);
            BlockFace direction = itemFactory.getDirectionFromItem(item);
            int placed = placeVerticalLavaColumn(target, direction == BlockFace.DOWN ? BlockFace.DOWN : BlockFace.UP);
            consumeSingleBucket(event.getPlayer(), event.getHand());
            event.getPlayer().sendMessage("§aLava Gen Bucket placed §f" + placed + " §alava source blocks.");
            return;
        }
    }

    @EventHandler
    public void onFatBucketFill(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() && event.getClickedBlock() != null) {
            String key = blockKey(event.getClickedBlock());
            BukkitTask task = pendingGeneratorTasks.remove(key);
            if (task != null) {
                task.cancel();
                event.getClickedBlock().setType(Material.AIR, false);
                event.getPlayer().sendMessage("§cCancelled queued gen placement.");
                event.setCancelled(true);
                return;
            }
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        GenItemType type = itemFactory.getTypeFromItem(handItem);
        if (type == null || !type.isFatBucket()) return;

        // Fully override vanilla bucket behavior for fat buckets
        event.setCancelled(true);

        Material neededFluid = type.getFatBucketFluidType();
        Material clickedType = event.getClickedBlock().getType();
        if (clickedType == neededFluid) {
            int units = itemFactory.getFatBucketUnits(handItem);
            if (units >= GenItemFactory.FAT_BUCKET_CAPACITY) {
                player.sendMessage("§eThat fat bucket is already full.");
                return;
            }

            int toAdd = Math.min(GenItemFactory.FAT_BUCKET_FILL_UNITS, GenItemFactory.FAT_BUCKET_CAPACITY - units);
            event.getClickedBlock().setType(Material.AIR, false);
            units += toAdd;

            itemFactory.setFatBucketUnits(handItem, units);
            player.getInventory().setItemInMainHand(handItem);
            player.sendMessage("§aFilled fat bucket: §f+" + toAdd + " §aunits (§f" + units + "§a/" + GenItemFactory.FAT_BUCKET_CAPACITY + ").");
            return;
        }

        // Otherwise try to place a source into the adjacent target block
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!canReplace(target.getType())) {
            player.sendMessage("§cCannot place liquid there.");
            return;
        }

        int units = itemFactory.getFatBucketUnits(handItem);
        if (units <= 0) {
            player.sendMessage("§cThat fat bucket is empty. Refill it from a source block.");
            return;
        }

        target.setType(type.getFatBucketFluidType(), false);
        units--;

        // Keep it as the same fat bucket item even at 0 units
        itemFactory.setFatBucketUnits(handItem, units);
        player.getInventory().setItemInMainHand(handItem);
        if (units <= 0) {
            player.sendMessage("§eFat bucket is now empty (§f0§e/" + GenItemFactory.FAT_BUCKET_CAPACITY + ").");
        } else {
            player.sendMessage("§aPlaced source. §7Remaining units: §f" + units);
        }
    }

    private int placeVerticalLavaColumn(Block start, BlockFace direction) {
        int minY = start.getWorld().getMinHeight();
        int maxY = start.getWorld().getMaxHeight() - 1;

        int x = start.getX();
        int z = start.getZ();
        int count = 0;

        if (direction == BlockFace.UP) {
            for (int y = start.getY(); y <= maxY; y++) {
                Block block = start.getWorld().getBlockAt(x, y, z);
                if (block.getType() == Material.BEDROCK) continue;
                if (!canReplace(block.getType())) continue;
                block.setType(Material.LAVA, false);
                count++;
            }
        } else {
            for (int y = start.getY(); y >= minY; y--) {
                Block block = start.getWorld().getBlockAt(x, y, z);
                if (block.getType() == Material.BEDROCK) continue;
                if (!canReplace(block.getType())) continue;
                block.setType(Material.LAVA, false);
                count++;
            }
        }

        return count;
    }

    private BlockFace resolveDirection(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch <= -60f) return BlockFace.UP;
        if (pitch >= 60f) return BlockFace.DOWN;

        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return BlockFace.NORTH;
        if (yaw >= 225 && yaw < 315) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    private boolean canReplace(Material material) {
        return material.isAir() || material == Material.WATER || material == Material.LAVA;
    }

    private boolean canOverwriteGenerated(Material material) {
        return material != Material.BEDROCK;
    }

    private void consumeSingleBucket(Player player, EquipmentSlot hand) {
        ItemStack held = getHandItem(player, hand);
        if (held == null) return;
        int amount = held.getAmount();
        if (amount > 1) {
            held.setAmount(amount - 1);
            setHandItem(player, hand, held);
            player.getInventory().addItem(new ItemStack(Material.BUCKET));
        } else {
            setHandItem(player, hand, new ItemStack(Material.BUCKET));
        }
    }

    private ItemStack getHandItem(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private void setHandItem(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private String format(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.format("%.2f", value);
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
