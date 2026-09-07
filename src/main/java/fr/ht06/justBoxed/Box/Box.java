package fr.ht06.justBoxed.Box;

import fr.ht06.justBoxed.Box.Invitation.BoxInvite;
import fr.ht06.justBoxed.JustBoxed;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Represents an isolated game box instance and its team data.
///
/// Holds the technical identifier, ownership, active members,
/// and displays metadata associated with the box.
public class Box {
    private final UUID uuid;
    private Component displayName;
    private UUID owner;
    private final List<UUID> members = new ArrayList<>();

    private final List<BoxInvite> invitedPlayers = new ArrayList<>();

    private Set<Advancement> advancements = new HashSet<>();

    public Box(UUID uuid, Component displayName, UUID owner) {
        this.uuid = uuid;
        this.displayName = displayName;
        this.owner = owner;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Component displayName) {
        this.displayName = displayName;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID member) {
        this.members.add(member);
    }

    public void removeMember(UUID memberUuid) {
        this.members.remove(memberUuid);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<Advancement> getAdvancements() {
        return advancements;
    }

    public @Nullable World getWorld(Plugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "box_" + this.uuid);
        return Bukkit.getWorld(key);
    }

    public boolean isWorldLoaded(Plugin plugin) {
        return getWorld(plugin) != null;
    }

    /// Load a world and return it
    public @Nullable World loadWorld(Plugin plugin) {
        World existing = getWorld(plugin);
        if (existing != null) {
            return existing;
        }

        WorldCreator creator = WorldCreator.ofKey(new NamespacedKey(plugin, "box_" + this.uuid.toString().toLowerCase()));
        World world = creator.createWorld();

        this.updateWorldBorder(plugin);

        return world;
    }

    /// Unload a world and tp all the player to the world `world`
    /// return true if the world was unloaded, false otherwise
    public boolean unloadWorld(Plugin plugin){
        if (!this.isWorldLoaded(plugin))
            return false;

        World world = this.getWorld(plugin);

        // Put player on the base world
        Location fallbackSpawn = Bukkit.getWorld("world").getSpawnLocation();
        for (Player p : world.getPlayers()) {
            p.teleport(fallbackSpawn);
        }

        //Unload without saving because we're deleting it
        return Bukkit.unloadWorld(world, false);
    }

    public void updateWorldBorder(Plugin plugin){
        if (this.isWorldLoaded(plugin)){
            // World border need to have a minimum size of 1
            this.getWorld(plugin).getWorldBorder().changeSize(1 + (this.advancements.size()*2), 20L);
        }
    }

    public boolean isInvited(UUID playerUUID){
        return this.invitedPlayers.stream().anyMatch(invite -> invite.getTargetUuid().equals(playerUUID));
    }

    public void addInvitation(BoxInvite invite){
        this.invitedPlayers.add(invite);
    }

    public void removeInvitation(UUID playerUUID){
        this.invitedPlayers.removeIf(invite -> {
            if (invite.getTargetUuid().equals(playerUUID)) {
                invite.cancel();
                return true;
            }
            return false;
        });
    }

    public void broadcastMessage(Component message){
        for (UUID uuid : this.members){
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                p.sendMessage(message);
        }

        Player player = Bukkit.getPlayer(this.owner);
        if (player != null && player.isOnline())
            player.sendMessage(message);
    }

    public void sendMessageToOwner(Component message){
        Player player = Bukkit.getPlayer(this.owner);
        if (player != null && player.isOnline())
            player.sendMessage(message);
    }

    public void grantAdvancement(@NotNull Advancement advancement, Player getter){
        if (this.advancements.contains(advancement)) return;

        this.advancements.add(advancement);

        // Send message to players
        this.broadcastMessage(getter.name().append(Component.text(" get the advancement ").append(advancement.displayName())));
        this.members
                .stream()
                .filter(member -> {
                    Player player = Bukkit.getPlayer(member);
                    return player != null && player.isOnline() && member != getter.getUniqueId();
                })
                .map(Bukkit::getPlayer)
                .forEach(player -> {
                    for (String criteria: advancement.getCriteria())
                        player.getAdvancementProgress(advancement).awardCriteria(criteria);
                });

        // Update world if loaded
        this.updateWorldBorder(JustBoxed.getInstance());


    }
}
