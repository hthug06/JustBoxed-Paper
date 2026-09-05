package fr.ht06.justBoxed.Box;

import fr.ht06.justBoxed.Storage.BoxRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BoxService {

    private final Plugin plugin;
    private final BoxRegistry registry;
    private final BoxRepository repository;

    public BoxService(Plugin plugin, BoxRegistry registry, BoxRepository repository) {
        this.plugin = plugin;
        this.registry = registry;
        this.repository = repository;
    }

    /// Create a new box: Instantiates the entity, clones the world, saves to RAM and persists in SQLite
    public CompletableFuture<Box> createBox(String rawName, Player owner) {
        UUID boxUuid = UUID.randomUUID();
        Component displayName = MiniMessage.miniMessage().deserialize(rawName);

        Box box = new Box(boxUuid, displayName, owner.getUniqueId());

        CompletableFuture<Box> future = new CompletableFuture<>();

        // Clone and load the template world
        BoxWorldManager.createBoxInstance(plugin, displayName, owner.getUniqueId(), world -> {
            if (world == null) {
                future.completeExceptionally(new IllegalStateException("Failed to create the world for the box for " + owner.getName()));
                return;
            }

            // Register the box
            registry.registerBox(box);

            // Save in SQLite
            repository.saveBox(box).thenRun(() -> future.complete(box));
        });

        return future;
    }

    /// Deletes a box: unloads and deletes the world, purges RAM and removes the SQLite entry.
    public CompletableFuture<Void> deleteBox(Box box) {
        // Remove from RAM
        registry.deleteBox(box.getUuid());

        // Unload world and delete folder
        BoxWorldManager.deleteBox(plugin, box);

        // 3. Delete from SQLite
        return repository.deleteBox(box.getUuid());
    }

    /// Add a member to the box and save it into SQLite
    public CompletableFuture<Void> addMember(Box box, UUID memberUuid) {
        box.addMember(memberUuid);
        registry.addMember(box.getUuid(), memberUuid);
        return repository.addMember(box.getUuid(), memberUuid);
    }

    /// remove a member from the box and update it into SQLite
    public CompletableFuture<Void> removeMember(Box box, UUID memberUuid) {
        box.removeMember(memberUuid);
        registry.removeMember(box.getUuid(), memberUuid);
        return repository.removeMember(box.getUuid(), memberUuid);
    }
}