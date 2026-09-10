package fr.ht06.justBoxed.Box;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static fr.ht06.justBoxed.Box.BoxTemplate.TEMPLATE_NETHER_NAME;
import static fr.ht06.justBoxed.Box.BoxTemplate.TEMPLATE_WORLD_NAME;


public class BoxWorldManager {

    private final Plugin plugin;
    private final Path dimensionsFolder;

    public BoxWorldManager(Plugin plugin) {
        this.plugin = plugin;
        this.dimensionsFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed");
    }

    public NamespacedKey getWorldKey(Box box, World.Environment env) {
        String name = env == World.Environment.NORMAL ? box.getOverworldWorldName() : box.getNetherWorldName();
        return new NamespacedKey(this.plugin, name);
    }

    public Optional<World> getWorld(Box box, World.Environment env) {
        return Optional.ofNullable(Bukkit.getWorld(this.getWorldKey(box, env)));
    }

    public World loadWorld(Box box, World.Environment env) {
        World existing = Bukkit.getWorld(this.getWorldKey(box, env));
        if (existing != null) {
            return existing;
        }

        WorldCreator creator = WorldCreator.ofKey(this.getWorldKey(box, env));
        creator.environment(env);
        creator.seed(BoxTemplate.TEMPLATE_SEED);

        World world = creator.createWorld();
        if (world != null) {
            this.syncWorldBorder(box, world);
        }
        return world;
    }

    public void unloadWorld(Box box, World.Environment env, boolean save) {
        this.getWorld(box, env).ifPresent(world -> {
            Location fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
            for (Player p : world.getPlayers()) {
                p.teleport(fallback);
            }
            Bukkit.unloadWorld(world, save);
        });
    }

    public void unloadAllWorlds(Box box, boolean save) {
        unloadWorld(box, World.Environment.NORMAL, save);
        unloadWorld(box, World.Environment.NETHER, save);
    }

    public void syncWorldBorder(Box box, World world) {
        double size = 1.0 + (box.getUnlockedAdvancements().size() * 2.0);
        world.getWorldBorder().changeSize(size, 20L);
    }

    public void syncWorldBorders(Box box) {
        this.getWorld(box, World.Environment.NORMAL).ifPresent(world -> this.syncWorldBorder(box, world));
        this.getWorld(box, World.Environment.NETHER).ifPresent(world -> this.syncWorldBorder(box, world));
    }

    // File to ignore during copy.
    private static final Set<String> IGNORED_FILES = Set.of(
            "chunk_tickets.dat",
            "raids.dat",
            "scheduled_events.dat",
            "uid.dat",
            "session.lock",
            // If you don't ignore this, paper will consider every world has the same uuid and only 1 world can be loaded at a time
            "metadata.dat"
    );

    /// Create a box instance, aka a world for this box, a box team and also teleport the player to it
    public void createWorldInstance(Plugin plugin, Box box, World.Environment environment, Consumer<World> onComplete) {
        String templateName = environment == World.Environment.NORMAL ? TEMPLATE_WORLD_NAME : TEMPLATE_NETHER_NAME;
        String folderName = environment == World.Environment.NORMAL
                ? "box_" + box.getUuid().toString().toLowerCase()
                : "box_" + box.getUuid().toString().toLowerCase() + "_nether";

        Path dimensionsFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed");

        Path sourceDir = dimensionsFolder.resolve(templateName);
        Path targetDir = dimensionsFolder.resolve(folderName);

        // Copy file async (no freeze of tick)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                copyTemplateDirectory(sourceDir, targetDir);

                // Back to main thread to declare the world to Paper
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    WorldCreator creator = WorldCreator.ofKey(new NamespacedKey(plugin, folderName));
                    creator.environment(environment);

                    World boxWorld = creator.createWorld();

                    if (onComplete != null) {
                        onComplete.accept(boxWorld);
                    }
                });
            } catch (IOException e) {
                plugin.getLogger().severe("Error when creating the box " + folderName + " : " + e.getMessage() + "(invalid path)");

                // Delete it if the world failed to create
                try {
                    deleteDirectoryRecursively(targetDir);
                } catch (IOException ignored) {}

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                });
            }
        });
    }

    /// Copy the template to create a world more quickly
    private static void copyTemplateDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                // Ignore unnecessary or dangerous files for duplication.
                if (!IGNORED_FILES.contains(file.getFileName().toString())) {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void deleteBox(Box box) {
        this.unloadAllWorlds(box, false);

        Path overworldDir = dimensionsFolder.resolve("box_" + box.getUuid());
        Path netherDir = dimensionsFolder.resolve("box_" + box.getUuid() + "_nether");

        // Clear folders async
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                deleteSafely(overworldDir, box.getUuid());
                deleteSafely(netherDir, box.getUuid());
            });
        }, 20L);
    }

    private void deleteSafely(Path dir, UUID boxUuid) {
        try {
            deleteDirectoryRecursively(dir);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to delete world directory " + dir.getFileName() + " for box " + boxUuid + ": " + e.getMessage());
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    // Pause if a lock persists
                    try {
                        Thread.sleep(50);
                        Files.delete(file);
                    } catch (InterruptedException | IOException ex) {
                        throw new IOException("Failed to delete locked file: " + file, ex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
