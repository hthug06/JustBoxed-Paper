package fr.ht06.justBoxed.Box;

import net.kyori.adventure.text.Component;

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

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }
}
