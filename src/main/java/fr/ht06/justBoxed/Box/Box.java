package fr.ht06.justBoxed.Box;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// Represents an isolated game box instance and its team data.
///
/// Holds the technical identifier, ownership, active members,
/// and displays metadata associated with the box.
public class Box {
    private final UUID uuid;
    private Component displayName;
    private UUID owner;
    private List<UUID> members;

    public Box(UUID uuid, Component displayName, UUID owner) {
        this.uuid = uuid;
        this.displayName = displayName;
        this.owner = owner;
        this.members = new ArrayList<>();
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
        return creator.createWorld();
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
}
