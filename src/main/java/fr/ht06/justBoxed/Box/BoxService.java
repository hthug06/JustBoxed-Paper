package fr.ht06.justBoxed.Box;

import fr.ht06.justBoxed.Box.Invitation.BoxInvite;
import fr.ht06.justBoxed.JustBoxed;
import fr.ht06.justBoxed.Storage.BoxRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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
        BoxWorldManager.createBoxInstance(plugin, box, world -> {
            if (world == null) {
                future.completeExceptionally(new IllegalStateException("Failed to create the world for the box for " + owner.getName()));
                return;
            }
            //Teleport
            owner.teleportAsync(world.getSpawnLocation().toCenterLocation());

            // Register the box
            registry.registerBox(box);

            // Save in SQLite
            // Create a snapshot of the box to avoid problem with async
            BoxSnapshot boxSnapshot = BoxSnapshot.from(box);
            repository.saveBox(boxSnapshot).thenRun(() -> future.complete(box));

            // Update the player command (sync for safety reasons)
            // We do this because the command registration suggestion has changed
            plugin.getServer().getScheduler().runTask(plugin, owner::updateCommands);

        });

        return future;
    }

    /// Deletes a box: unloads and deletes the world, purges RAM and removes the SQLite entry.
    public CompletableFuture<Void> deleteBox(Box box) {
        // Remove from RAM
        registry.deleteBox(box.getUuid());

        // Unload world and delete folder
        BoxWorldManager.deleteBox(plugin, box);

        // Update the player command
        // We do this because the command registration suggestion has changed
        for (UUID uuid : box.getAllMembersWithLeader()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.updateCommands();
            }
        }

        // Delete from SQLite
        return repository.deleteBox(box.getUuid());
    }

    /// Add a member to the box and save it into SQLite
    public CompletableFuture<Void> addMember(Box box, UUID memberUuid) {
        registry.addMember(box.getUuid(), memberUuid);
        return repository.addMember(box.getUuid(), memberUuid);
    }

    /// remove a member from the box and update it into SQLite
    public CompletableFuture<Void> removeMember(Box box, UUID memberUuid) {
        registry.removeMember(box.getUuid(), memberUuid);

        // Update the player command
        // We do this because the command registration suggestion has changed
        Player owner = Bukkit.getPlayer(memberUuid);
        if (owner != null && owner.isOnline()) {
            owner.updateCommands();
        }

        return repository.removeMember(box.getUuid(), memberUuid);
    }

    public CompletableFuture<Void> setBoxName(Box box, String teamName) {
        registry.updateDisplayName(box.getUuid(), teamName);
        return repository.updateDisplayName(box.getUuid(), teamName);
    }

    /// Invite a player to the box
    /// Return true if the player was invited, false if he was already invited
    public boolean invitePlayer(Plugin plugin, Box box, UUID inviterUuid, UUID targetUuid) {
        if (box.isInvited(targetUuid)) {
            return false;
        } else {
            box.addInvitation(new BoxInvite(plugin, box, inviterUuid, targetUuid, () -> {
                box.removeInvitation(targetUuid);

                OfflinePlayer player = Bukkit.getOfflinePlayer(targetUuid);
                box.sendMessageToOwner(Component.text("Invitation send to " + player.getName() + " expired."));
            }));
            return true;
        }

    }

    /// The target accepting the invite from the box UUID
    /// return true if the player successfully join the box, else false
    public CompletableFuture<Boolean> acceptInvitation(Box box, UUID targetUuid) {

        if (!box.isInvited(targetUuid)) {
            return CompletableFuture.completedFuture(false);
        }

        // Remove every invitation about this player
        registry.removeInvitation(targetUuid);
        registry.addMember(box.getUuid(), targetUuid);

        // Add member async to the db
        return repository.addMember(box.getUuid(), targetUuid).thenApply(_ -> {

            // But we need to load the world sync
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = plugin.getServer().getPlayer(targetUuid);
                if (player == null || !player.isOnline()) {
                    return;
                }

                if (!box.isWorldLoaded(this.plugin)) {
                    box.loadWorld(this.plugin);
                }

                World world = box.getWorld(this.plugin);
                if (world != null) {
                    Location targetLoc = world.getSpawnLocation().toCenterLocation();
                    player.teleportAsync(targetLoc)
                            // Grant every advancement after teleporting in case the world the
                            // player was in before has the gamerule show_advancement true
                            .thenAccept(_ -> {
                                // revoke every advancement
                                Iterator<Advancement> revokeIterator = Bukkit.getServer().advancementIterator();
                                while (revokeIterator.hasNext()) {
                                    AdvancementProgress progress = player.getAdvancementProgress(revokeIterator.next());
                                    for (String criteria : progress.getAwardedCriteria())
                                        progress.revokeCriteria(criteria);
                                }

                                // Grant box advancement
                                for (NamespacedKey namespacedKey : box.getUnlockedAdvancements()) {
                                    Advancement advancement = Bukkit.getAdvancement(namespacedKey);
                                    if (advancement != null) {
                                        AdvancementProgress progress = player.getAdvancementProgress(advancement);
                                        for (String criteria : advancement.getCriteria())
                                            progress.awardCriteria(criteria);
                                    }
                                }
                            });
                }

                box.broadcastMessage(player.name().append(Component.text(" has joined the box !")));
                player.updateCommands();
            });

            return true;
        });
    }

    /// The target deny the invite from the box UUID
    /// return true if the player successfully deny the invitation, else false
    public boolean denyInvite(Box box, UUID targetUuid) {
        if (!box.isInvited(targetUuid)) return false;

        box.removeInvitation(targetUuid);
        return true;
    }

    public CompletableFuture<Void> grantAdvancement(Box box, Advancement advancement, Player getter) {
        NamespacedKey key = advancement.getKey();

        if (!box.addAdvancement(key)) {
            return CompletableFuture.completedFuture(null);
        }

        return this.repository.saveAdvancement(box.getUuid(), key)
                .thenAccept(_ -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // sync every player
                    this.syncOneAdvancementForEveryPlayer(box, advancement, getter);

                    box.updateWorldBorder(this.plugin);
                }))
                .exceptionally(ex -> {
                    JustBoxed.getInstance().getLogger().severe("Failed to grant advancement : " + ex.getMessage());
                    return null;
                });
    }

    public void syncOneAdvancementForEveryPlayer(Box box, Advancement advancement, Player getter){
        for (UUID memberUuid : box.getAllMembersWithLeader()) {
            if (memberUuid.equals(getter.getUniqueId())) {
                continue;
            }

            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null && player.isOnline()) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criteria : advancement.getCriteria()) {
                    if (!progress.getAwardedCriteria().contains(criteria)) {
                        progress.awardCriteria(criteria);
                    }
                }
            }
        }
    }

    /// Add every advancement from a box to a player and return the list of advancement the player get
    public Set<Advancement> syncEveryAdvancementForOnePlayer(Box box, Player player){
        Set<Advancement> advancements = new HashSet<>();

        if (player != null && player.isOnline()) {
            for (NamespacedKey key : box.getUnlockedAdvancements()){
                Advancement advancement = Bukkit.getAdvancement(key);
                if (advancement != null){
                    AdvancementProgress progress = player.getAdvancementProgress(advancement);
                    for (String criteria : advancement.getCriteria()) {
                        if (!progress.getAwardedCriteria().contains(criteria)) {
                            progress.awardCriteria(criteria);
                            advancements.add(advancement);
                        }
                    }
                }
            }
        }
        return advancements;
    }

    public CompletableFuture<Void> setOwner(Box box, UUID newOwnerUuid) {
        // Get the previous owner, else, when changing in the database, the owner of the box will be the new owner
        // And this will break everything
        UUID previousOwnerUuid = this.registry.setOwner(box, newOwnerUuid);

        // Update players commands suggestions
        Player newOwner = Bukkit.getPlayer(newOwnerUuid);
        if (newOwner != null && newOwner.isOnline()) {
            newOwner.updateCommands();
        }

        Player previousOwner = Bukkit.getPlayer(newOwnerUuid);
        if (previousOwner != null && previousOwner.isOnline()) {
            previousOwner.updateCommands();
        }

        return this.repository.setOwner(box, newOwnerUuid, previousOwnerUuid);
    }
}