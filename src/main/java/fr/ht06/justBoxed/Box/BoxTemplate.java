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
/// This world is used to create a Box world by copying the world instead of creating it from scratch.
///
/// At the same time, create the nether template.
public class BoxTemplate {

    /// Need to be in lower case
    public static final String TEMPLATE_WORLD_NAME = "box_template_world";
    public static final String TEMPLATE_NETHER_NAME = "box_template_nether";
    public static final long TEMPLATE_SEED = 8500081009970950196L;

    public static final int X_SPAWN_POS = 17;
    public static final int Y_SPAWN_POS = 63;
    public static final int Z_SPAWN_POS = -24;

    private static NamespacedKey getOverworldKey(Plugin plugin) {
        return new NamespacedKey(plugin, TEMPLATE_WORLD_NAME);
    }

    private static NamespacedKey getNetherKey(Plugin plugin) {
        return new NamespacedKey(plugin, TEMPLATE_NETHER_NAME);
    }

    /**
     * Creates and configures template worlds (overworld and nether) with
     * specified configurations such as the world seed, spawn location, world border,
     * and game rules. The created worlds are unloaded immediately after creation.
     *
     * @param plugin the Plugin instance to retrieve necessary configurations or resources
     * @param env    the specific environment type to create, like OVERWORLD or NETHER
     */
    public static void create(Plugin plugin, World.Environment env) {

        // Create the template world with his seed and various configs (like the dimension and seed)
        NamespacedKey key = env == World.Environment.NORMAL ? getOverworldKey(plugin) : getNetherKey(plugin);
        WorldCreator worldCreator = WorldCreator.ofKey(key);
        worldCreator.seed(TEMPLATE_SEED);
        worldCreator.environment(env);
        World world = worldCreator.createWorld();

        // Unload the world after his creation
        if (world != null) {
            Location spawnLocation = new Location(world, X_SPAWN_POS, Y_SPAWN_POS, Z_SPAWN_POS, 0, 0).toCenterLocation();

            // World border, spawn location and game rule
            world.getWorldBorder().setSize(1);
            world.getWorldBorder().setCenter(spawnLocation);
            world.setSpawnLocation(spawnLocation);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);

            // Load the spawn chunk
            world.getChunkAt(X_SPAWN_POS >> 4, Z_SPAWN_POS >> 4, true);

            // Save and load just after
            Bukkit.unloadWorld(world, true);
        } else {
            JustBoxed.getInstance().getLogger().severe("Cannot unload world Template (dimension: " + env.name() + "), world is null");
        }
    }

    public static boolean dimensionsExists(Plugin plugin, World.Environment env){
        NamespacedKey key = env == World.Environment.NORMAL ? getOverworldKey(plugin) : getNetherKey(plugin);
        String templateName = env == World.Environment.NORMAL ? TEMPLATE_WORLD_NAME : TEMPLATE_NETHER_NAME;

        // Unload world if he was loaded
        World world = Bukkit.getWorld(key);
        if (world != null) {
            Bukkit.unloadWorld(world, true);
        }

        // Check if the world folder exists on disk
        Path worldFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed")
                .resolve(templateName);

        return Files.isDirectory(worldFolder);
    }



    /// Check if the template world exists by checking:
    /// - If worlds are loaded in memory
    /// - If world folders exist on disk
    public static boolean exist(Plugin plugin) {
        return dimensionsExists(plugin, World.Environment.NORMAL)
                && dimensionsExists(plugin, World.Environment.NETHER);
    }



}
