package fr.ht06.justBoxed;

import fr.ht06.justBoxed.Box.BoxTemplate;
import org.bukkit.plugin.java.JavaPlugin;

public final class JustBoxed extends JavaPlugin {

    public static JustBoxed getInstance() {
        return getPlugin(JustBoxed.class);
    }

    @Override
    public void onEnable() {
        // On first launch, create a template world with seed 8500081009970950196 (every biome and structure in 1000 blocks)
        // else do nothing
        if (!BoxTemplate.exist()) {
            getLogger().info("Template world not found, creating it...");
            BoxTemplate.create(this);
            getLogger().info("Template world created !");

        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
