package fr.ht06.justBoxed.Box;

import fr.ht06.justBoxed.JustBoxed;
import org.bukkit.*;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;

/// # Create a Box template world
/// This world will be located in `./world/dimensions/justboxed/world_box_template`
/// The seed is fixed to `8500081009970950196`
///
/// This world is used to create a Box world, by copying the world instead of creating it from scratch
public class BoxTemplate {

    /// Need to be in lower case
    public static final String TEMPLATE_WORLD_NAME = "box_template";
    public static final long TEMPLATE_WORLD_SEED = 8500081009970950196L;

    public static NamespacedKey getKey(Plugin plugin) {
        return new NamespacedKey(plugin, TEMPLATE_WORLD_NAME);
    }

    /// Create the template world, load the spawn chunk, and unload the world
    public static void create(Plugin plugin) {
        // Create the template world with his seed and various configs
        WorldCreator creator = WorldCreator.ofKey(getKey(plugin));
        creator.seed(TEMPLATE_WORLD_SEED);
        World world = creator.createWorld();

        // Unload the world after his creation
        if (world != null) {
            world.getWorldBorder().setCenter(17.5, -23.5);
            world.getWorldBorder().setSize(1);
            world.setSpawnLocation(new Location(world, 17.5, 63, -23.5, 0, 0));
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);

            // Load the spawn chunk (async)
            world.getChunkAt(17 >> 4, (-24) >> 4, true);

            // Save and load just after
            Bukkit.unloadWorld(world, true);
        } else {
            JustBoxed.getInstance().getLogger().severe("Cannot unload Template world, world is null");
        }

    }

    /// Check if the template world exist by checking:
    /// - If the world is loaded in memory
    /// - If the world folder exists on disk
    public static boolean exist(Plugin plugin) {
        // Unload world if he was loaded
        World world = Bukkit.getWorld(getKey(plugin));
        if (world != null) {
            Bukkit.unloadWorld(world, true);
        }

        // Check if the world folder exists on disk
        Path worldFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed")
                .resolve(TEMPLATE_WORLD_NAME);

        return Files.isDirectory(worldFolder);
    }


}
