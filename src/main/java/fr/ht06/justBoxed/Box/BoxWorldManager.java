package fr.ht06.justBoxed.Box;

import org.bukkit.*;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.function.Consumer;


public class BoxWorldManager {

    // File to ignore during copy.
    private static final Set<String> IGNORED_FILES = Set.of(
            "chunk_tickets.dat",
            "raids.dat",
            "scheduled_events.dat",
            "uid.dat",
            "session.lock",
            // If you don't ignore this, paper will consider every world have the same uuid and only 1 world can be loaded at a time
            "metadata.dat"
    );

    /// Create a box instance, aka a world for this box, a box team and also teleport the player to it
    public static void createBoxInstance(Plugin plugin, Box box, Consumer<World> onComplete) {
        Path dimensionsFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed");

        Path sourceDir = dimensionsFolder.resolve(BoxTemplate.TEMPLATE_WORLD_NAME);
        Path targetDir = dimensionsFolder.resolve("box_" + box.getUuid());

        // Copy file async (no freeze of tick)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                copyTemplateDirectory(sourceDir, targetDir);

                // Back to main thread to declare the world to Paper
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    WorldCreator creator = WorldCreator.ofKey(new NamespacedKey(plugin, "box_" + box.getUuid().toString().toLowerCase()));

                    World boxWorld = creator.createWorld();

                    if (onComplete != null) {
                        onComplete.accept(boxWorld);
                    }
                });
            } catch (IOException e) {
                plugin.getLogger().severe("Error when creating the box " + box.getUuid().toString().toLowerCase() + " : " + e.getMessage() + "(invalid path)");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                });

                // Delete it if the world failed to create
                Path deleteDimensionsFolder = Bukkit.getWorldContainer().toPath()
                        .resolve("world")
                        .resolve("dimensions")
                        .resolve("justboxed");
                Path deleteTargetDir = dimensionsFolder.resolve("box_" + box.getUuid());
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

    /// Delete a box by deleting all the files
    public static void deleteBox(Plugin plugin, Box box) {
        // Unload World
        box.unloadWorld(plugin);

        // Clear pending invitation
        box.clearInvitations();

        Path dimensionsFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed");
        Path targetDir = dimensionsFolder.resolve("box_" + box.getUuid());

        // Wait 1 second for the world to be fully unloaded before deleting the files
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    deleteDirectoryRecursively(targetDir);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to delete world directory for box " + box.getUuid() + ": " + e.getMessage());
                }
            });
        }, 20L);
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
