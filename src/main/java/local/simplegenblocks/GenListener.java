package local.simplegenblocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenListener implements Listener {

    private static final int  GENERATOR_MAX_DISTANCE = 319;
    private static final long     TICKS_PER_BLOCK = 4L;  // 5 blocks/sec
    private static final Material ANIM_ACTIVE     = Material.GREEN_WOOL;
    private static final Material ANIM_REMOVING   = Material.RED_WOOL;

    // ── Animation state ───────────────────────────────────────────────────────
    private static final class GenAnimation {
        final Player      player;
        final Block       origin;       // the green-wool marker block
        final List<Block> targets;      // blocks to fill (excluding origin)
        final List<Block> placed;       // blocks already filled (for undo)
        final Material    output;
        final BlockFace   direction;
        int               nextIndex = 0;
        boolean           removing  = false;
        int               removeIndex = 0;
        BukkitTask        task;

        GenAnimation(Player player, Block origin, List<Block> targets, Material output, BlockFace direction) {
            this.player    = player;
            this.origin    = origin;
            this.targets   = targets;
            this.placed    = new ArrayList<>();
            this.output    = output;
            this.direction = direction;
        }
    }

    // Key = blockKey of origin block → active animation
    private final Map<String, GenAnimation> animations = new HashMap<>();

    private final SimpleGenBlocksPlugin plugin;
    private final GenItemFactory        itemFactory;

    public GenListener(SimpleGenBlocksPlugin plugin) {
        this.plugin      = plugin;
        this.itemFactory = plugin.getItemFactory();
    }

    // ── GUI Click ─────────────────────────────────────────────────────────────
    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!GenHud.TITLE_MAIN.equals(title)
                && !title.startsWith(GenHud.TITLE_DIRECTION_PREFIX)
                && !GenHud.TITLE_LAVA_DIRECTION.equals(title)) return;

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
        if (direction == null) direction = resolveDirection(player);

        final BlockFace dir    = direction;
        final Material  output = type.getGeneratorOutput();
        final Block     origin = event.getBlockPlaced();
        final String    key    = blockKey(origin);

        // Cancel any previous animation at this location
        cancelExistingAnimation(key, false);

        // Collect target blocks in direction (excluding the origin itself)
        List<Block> targets = new ArrayList<>();
        for (int i = 1; i < GENERATOR_MAX_DISTANCE; i++) {
            Block next = origin.getRelative(dir, i);
            if (!canOverwrite(next.getType())) break;
            targets.add(next);
        }

        // Defer one tick so Bukkit's post-event block finalisation is done
        // before we overwrite the origin with green wool.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Origin block becomes the green-wool marker
            origin.setType(ANIM_ACTIVE, false);

            GenAnimation anim = new GenAnimation(player, origin, targets, output, dir);
            animations.put(key, anim);

            player.sendMessage("§aGenerator placed! Filling §f" + targets.size() + " §a"
                    + output.name().toLowerCase() + " blocks toward §f" + dir.name()
                    + "§a. §7(Left-click the green wool to cancel & undo)");

            BukkitTask task = new BukkitRunnable() {
                @Override public void run() {
                    GenAnimation a = animations.get(key);
                    if (a == null) { cancel(); return; }

                    if (a.nextIndex >= a.targets.size()) {
                        // Done — remove the green wool marker
                        animations.remove(key);
                        if (a.origin.getType() == ANIM_ACTIVE) a.origin.setType(Material.AIR, false);
                        player.sendMessage("§a✔ Generation complete! §f" + a.placed.size()
                                + " §a" + output.name().toLowerCase() + " blocks placed.");
                        cancel();
                        return;
                    }

                    Block b = a.targets.get(a.nextIndex++);
                    if (canOverwrite(b.getType())) {
                        b.setType(output, false);
                        a.placed.add(b);
                    }
                }
            }.runTaskTimer(plugin, TICKS_PER_BLOCK, TICKS_PER_BLOCK);

            anim.task = task;
        });
    }

    // ── Left-click cancel + fat bucket right-click ────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Left-click the red-wool marker while removal is in progress → ignore
        if (event.getAction().isLeftClick() && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == ANIM_REMOVING) {
            String key = blockKey(event.getClickedBlock());
            if (animations.containsKey(key)) {
                event.setCancelled(true);
                return;
            }
        }

        // Left-click the green-wool marker → turn red and remove blocks 5/sec in reverse
        if (event.getAction().isLeftClick() && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == ANIM_ACTIVE) {
            String key = blockKey(event.getClickedBlock());
            GenAnimation anim = animations.get(key);
            if (anim != null && !anim.removing) {
                // Stop the forward fill task
                if (anim.task != null) anim.task.cancel();

                // Turn green wool → red wool
                if (anim.origin.getType() == ANIM_ACTIVE) anim.origin.setType(ANIM_REMOVING, false);
                anim.removing    = true;
                anim.removeIndex = anim.placed.size() - 1;
                int total = anim.placed.size();
                anim.player.sendMessage("§c✖ Cancelling gen... removing §f" + total + " §cblocks.");

                BukkitTask removeTask = new BukkitRunnable() {
                    @Override public void run() {
                        GenAnimation a = animations.get(key);
                        if (a == null) { cancel(); return; }

                        if (a.removeIndex < 0) {
                            animations.remove(key);
                            if (a.origin.getType() == ANIM_REMOVING) a.origin.setType(Material.AIR, false);
                            a.player.sendMessage("§c✔ Gen removed! Cleared §f" + total + " §cblocks.");
                            cancel();
                            return;
                        }

                        Block b = a.placed.get(a.removeIndex--);
                        if (b.getType() == a.output) b.setType(Material.AIR, false);
                    }
                }.runTaskTimer(plugin, TICKS_PER_BLOCK, TICKS_PER_BLOCK);

                anim.task = removeTask;
                event.setCancelled(true);
                return;
            }
        }

        // Fat bucket: right-click, main hand only
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player    player   = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        GenItemType type   = itemFactory.getTypeFromItem(handItem);
        if (type == null || !type.isFatBucket()) return;

        event.setCancelled(true);

        Material neededFluid = type.getFatBucketFluidType();

        // ── Try to FILL: ray-trace to detect fluid source blocks ──────────────
        // Fluid blocks don't register as a normal clickedBlock, so we ray-trace
        // with FluidCollisionMode.ALWAYS to find lava/water source blocks.
        var rt = player.rayTraceBlocks(5.0, org.bukkit.FluidCollisionMode.ALWAYS);
        if (rt != null && rt.getHitBlock() != null) {
            Block hit = rt.getHitBlock();
            if (hit.getType() == neededFluid) {
                // Only allow picking up a SOURCE block (level == 0)
                boolean isSource = !(hit.getBlockData() instanceof org.bukkit.block.data.Levelled lev)
                        || lev.getLevel() == 0;
                if (isSource) {
                    int units = itemFactory.getFatBucketUnits(handItem);
                    if (units >= GenItemFactory.FAT_BUCKET_CAPACITY) {
                        player.sendMessage("§eThat fat bucket is already full.");
                        return;
                    }
                    int toAdd = Math.min(GenItemFactory.FAT_BUCKET_FILL_UNITS,
                            GenItemFactory.FAT_BUCKET_CAPACITY - units);
                    hit.setType(Material.AIR, false);
                    units += toAdd;
                    itemFactory.setFatBucketUnits(handItem, units);
                    player.getInventory().setItemInMainHand(handItem);
                    player.sendMessage("§aFilled fat bucket: §f+" + toAdd
                            + " §aunits (§f" + units + "§a/" + GenItemFactory.FAT_BUCKET_CAPACITY + ").");
                    return;
                }
            }
        }

        // ── PLACE: place a unit into the adjacent block ───────────────────────
        if (event.getClickedBlock() == null) return;
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
        itemFactory.setFatBucketUnits(handItem, units);
        player.getInventory().setItemInMainHand(handItem);
        if (units <= 0) {
            player.sendMessage("§eFat bucket is now empty (§f0§e/" + GenItemFactory.FAT_BUCKET_CAPACITY + ").");
        } else {
            player.sendMessage("§aPlaced source. §7Remaining units: §f" + units);
        }
    }

    // ── Lava Gen Bucket ───────────────────────────────────────────────────────
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        // Read from the actual hand — event.getItemStack() returns the post-use
        // result (empty bucket) in newer Paper versions, losing PDC data.
        Player    player = event.getPlayer();
        ItemStack item   = getHandItem(player, event.getHand());
        GenItemType type = itemFactory.getTypeFromItem(item);

        // Fat buckets: cancel vanilla empty so our onInteract handles placement
        if (type != null && type.isFatBucket()) {
            event.setCancelled(true);
            return;
        }

        if (type == null || !type.isLavaGenBucket()) return;

        event.setCancelled(true);
        Block     target    = event.getBlockClicked().getRelative(event.getBlockFace());
        BlockFace direction = itemFactory.getDirectionFromItem(item);
        int placed = placeVerticalLavaColumn(target,
                direction == BlockFace.DOWN ? BlockFace.DOWN : BlockFace.UP);
        consumeSingleBucket(player, event.getHand());
        player.sendMessage("§aLava Gen Bucket placed §f" + placed + " §alava source blocks.");
    }

    // ── Animation helpers ─────────────────────────────────────────────────────

    /** Cancel a running animation silently (e.g. when a new gen is placed at same spot). */
    private void cancelExistingAnimation(String key, boolean removeOrigin) {
        GenAnimation anim = animations.remove(key);
        if (anim == null) return;
        if (anim.task != null) anim.task.cancel();
        if (removeOrigin && (anim.origin.getType() == ANIM_ACTIVE || anim.origin.getType() == ANIM_REMOVING))
            anim.origin.setType(Material.AIR, false);
    }

    // ── Lava column placer ────────────────────────────────────────────────────

    private int placeVerticalLavaColumn(Block start, BlockFace direction) {
        int minY = start.getWorld().getMinHeight();
        int maxY = start.getWorld().getMaxHeight() - 1;
        int x = start.getX(), z = start.getZ();
        int count = 0;

        if (direction == BlockFace.UP) {
            for (int y = start.getY(); y <= maxY; y++) {
                Block b = start.getWorld().getBlockAt(x, y, z);
                if (b.getType() == Material.BEDROCK) continue;
                if (!canReplace(b.getType())) continue;
                b.setType(Material.LAVA, false);
                count++;
            }
        } else {
            for (int y = start.getY(); y >= minY; y--) {
                Block b = start.getWorld().getBlockAt(x, y, z);
                if (b.getType() == Material.BEDROCK) continue;
                if (!canReplace(b.getType())) continue;
                b.setType(Material.LAVA, false);
                count++;
            }
        }
        return count;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private BlockFace resolveDirection(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch <= -60f) return BlockFace.UP;
        if (pitch >= 60f)  return BlockFace.DOWN;
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw >= 45  && yaw < 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return BlockFace.NORTH;
        if (yaw >= 225 && yaw < 315) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    private boolean canOverwrite(Material m) { return m != Material.BEDROCK; }
    private boolean canReplace(Material m)   { return m.isAir() || m == Material.WATER || m == Material.LAVA; }

    private void consumeSingleBucket(Player player, EquipmentSlot hand) {
        ItemStack held = getHandItem(player, hand);
        if (held == null) return;
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
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
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(item);
        else                                player.getInventory().setItemInMainHand(item);
    }

    private String format(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000)     return String.format("%.1fK", value / 1_000.0);
        return String.format("%.2f", value);
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
