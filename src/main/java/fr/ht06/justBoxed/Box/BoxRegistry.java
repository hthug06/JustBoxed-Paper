package fr.ht06.justBoxed.Box;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoxRegistry {
    private final Map<UUID, Box> boxes = new ConcurrentHashMap<>();

    /// Because we use this a lot, it's better to have this in RAM
    /// <OwnerUUID><BoxUUID>
    private final Map<UUID, UUID> playerToBox = new ConcurrentHashMap<>();

    public void registerBox(Box box) {
        boxes.put(box.getUuid(), box);
        playerToBox.put(box.getOwner(), box.getUuid());
        for (UUID member : box.getMembers()) {
            playerToBox.put(member, box.getUuid());
        }
    }

    public @Nullable Box getBoxByPlayer(UUID uuid) {
        UUID boxUuid = playerToBox.get(uuid);
        return boxUuid != null ? boxes.get(boxUuid) : null;
    }

    public @Nullable Box getBoxByUuid(UUID boxUuid) {
        return boxes.get(boxUuid);
    }

    public void deleteBox(UUID boxUuid) {
        Box box = boxes.remove(boxUuid);
        if (box == null) {
            return;
        }

        playerToBox.remove(box.getOwner());
        for (UUID member : box.getMembers()) {
            playerToBox.remove(member);
        }
    }

    /// Add a member into the box.
    public void addMember(UUID boxUuid, UUID memberUuid) {
        playerToBox.put(memberUuid, boxUuid);
        this.boxes.get(boxUuid).addMember(memberUuid);
    }

    /// Remove a member from the box.
    public void removeMember(UUID boxUuid, UUID memberUuid) {
        playerToBox.remove(memberUuid);
        this.boxes.get(boxUuid).removeMember(memberUuid);
    }
}
