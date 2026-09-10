package fr.ht06.justBoxed.Box;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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

    private final Set<NamespacedKey> unlockedAdvancements = new HashSet<>();

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

    /// Set the new owner as the owner and the set previous owner as a member
    /// Return the previous owner
    public UUID setOwner(UUID newOwner) {
        UUID previousOwner = this.owner;
        // owner -> member
        this.members.add(this.owner);

        // member -> new owner
        this.owner = newOwner;
        this.members.remove(newOwner);

        return previousOwner;
    }

    public Set<UUID> getAllMembersWithLeader() {
        Set<UUID> all = new HashSet<>(this.members);
        all.add(this.owner);
        return all;
    }

    public Set<NamespacedKey> getUnlockedAdvancements() {
        return unlockedAdvancements;
    }

    public String getOverworldWorldName() {
        return "box_" + this.uuid;
    }

    public String getNetherWorldName() {
        return "box_" + this.uuid + "_nether";
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

    public void clearInvitations() {
        for (BoxInvite invite : this.invitedPlayers) {
            invite.cancel();
        }
        this.invitedPlayers.clear();
    }

    public void broadcastMessage(Component message){
        for (UUID uuid : this.getAllMembersWithLeader()){
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                p.sendMessage(message);
        }
    }

    public void sendMessageToOwner(Component message){
        Player player = Bukkit.getPlayer(this.owner);
        if (player != null && player.isOnline())
            player.sendMessage(message);
    }

    public boolean addAdvancement(NamespacedKey key) {
        return this.unlockedAdvancements.add(key);
    }

    public boolean hasAdvancement(NamespacedKey key) {
        return this.unlockedAdvancements.contains(key);
    }
}
