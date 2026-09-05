package fr.ht06.justBoxed.Box;

import fr.ht06.justBoxed.JustBoxed;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;


public class BoxWorldManager {

    // File to ignore during copy.
    private static final Set<String> IGNORED_FILES = Set.of(
            "chunk_tickets.dat",
            "raids.dat",
            "scheduled_events.dat",
            "uid.dat",
            "session.lock"
    );

    /// Create a box instance, aka a world for this box, a box team and also teleport the player to it
    public static void createBoxInstance(Plugin plugin, Component displayName, UUID owner, Consumer<World> onComplete) {
        UUID boxUuid = UUID.randomUUID();
        Path dimensionsFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed");

        Path sourceDir = dimensionsFolder.resolve(BoxTemplate.TEMPLATE_WORLD_NAME);
        Path targetDir = dimensionsFolder.resolve("box_" + boxUuid);

        // Copy file async (no freeze of tick)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                copyTemplateDirectory(sourceDir, targetDir);

                // Back to main thread to declare the world to Paper
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    WorldCreator creator = WorldCreator.ofKey(new NamespacedKey(plugin, "box_" + boxUuid.toString().toLowerCase()));

                    World boxWorld = creator.createWorld();

                    if (onComplete != null) {
                        onComplete.accept(boxWorld);
                    }

                    // Register the box
                    JustBoxed.getInstance().getBoxRegistry().registerBox(new Box(boxUuid, displayName, owner));
                });
            } catch (IOException e) {
                plugin.getLogger().severe("Error when creating the box " + boxUuid.toString().toLowerCase() + " : " + e.getMessage() + "(invalid path)");
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
        World world = box.getWorld(plugin);

        // Unload World
        if (!box.unloadWorld(plugin)) {
            plugin.getLogger().warning("Failed to unload world for box " + box.getUuid());
            return;
        }

        Path dimensionsFolder = Bukkit.getWorldContainer().toPath()
                .resolve("world")
                .resolve("dimensions")
                .resolve("justboxed");
        Path targetDir = dimensionsFolder.resolve("box_" + box.getUuid());

        // Delete folder Async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteDirectoryRecursively(targetDir);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to delete files in world : " + e.getMessage());
            }
        });
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
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
