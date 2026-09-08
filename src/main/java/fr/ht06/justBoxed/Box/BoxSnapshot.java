package fr.ht06.justBoxed.Box;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;

import java.util.Set;
import java.util.UUID;

/**
 * Represents an immutable snapshot of a Box instance.
 * <p>
 * This record captures the state of a Box at a specific moment, including its UUID, display name,
 * owner, members, and unlocked advancements.
 */
public record BoxSnapshot(
        UUID uuid,
        Component displayName,
        UUID owner,
        Set<UUID> members,
        Set<NamespacedKey> unlockedAdvancements
) {
    public static BoxSnapshot from(Box box) {
        return new BoxSnapshot(
                    box.getUuid(),
                    box.getDisplayName(),
                    box.getOwner(),
                    Set.copyOf(box.getMembers()),
                    Set.copyOf(box.getUnlockedAdvancements())
                );
    }
}