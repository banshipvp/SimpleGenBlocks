package local.simplegenblocks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SimpleGenBlocksPlugin extends JavaPlugin {

    private Economy economy;
    private GenItemFactory itemFactory;
    private GenHud hud;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found. Disabling SimpleGenBlocks.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        itemFactory = new GenItemFactory(
                new NamespacedKey(this, "gen_type"),
                new NamespacedKey(this, "fat_units"),
            new NamespacedKey(this, "shop_type"),
            new NamespacedKey(this, "gen_direction")
        );
        hud = new GenHud(itemFactory);

        GenCommand command = new GenCommand(this);
        SimpleGenBlocksTabCompleter tabCompleter = new SimpleGenBlocksTabCompleter();
        if (getCommand("gen") != null) {
            getCommand("gen").setExecutor(command);
            getCommand("gen").setTabCompleter(tabCompleter);
        }
        if (getCommand("genblocks") != null) {
            getCommand("genblocks").setExecutor(command);
            getCommand("genblocks").setTabCompleter(tabCompleter);
        }

        Bukkit.getPluginManager().registerEvents(new GenListener(this), this);

        getLogger().info("SimpleGenBlocks enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleGenBlocks disabled.");
    }

    public Economy getEconomy() {
        return economy;
    }

    public GenItemFactory getItemFactory() {
        return itemFactory;
    }

    public GenHud getHud() {
        return hud;
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
