package fr.ht06.justBoxed.Box;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoxRegistry {
    private final Map<UUID, Box> boxes = new ConcurrentHashMap<>();

    /// Because we use this a lot, it's better to have this in cache
    private final Map<UUID, UUID> playerToBox = new ConcurrentHashMap<>();

    public void registerBox(Box box) {
        boxes.put(box.getUuid(), box);
        playerToBox.put(box.getOwner(), box.getUuid());
        for (UUID member : box.getMembers()) {
            playerToBox.put(member, box.getUuid());
        }
    }

    public @Nullable Box getBoxByPlayer(UUID uuid) {
        UUID boxId = playerToBox.get(uuid);
        return boxId != null ? boxes.get(boxId) : null;
    }
}
