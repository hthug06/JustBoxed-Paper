package fr.ht06.justBoxed.Storage;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class BoxRepository {

    private final Plugin plugin;
    private final DatabaseManager dbManager;

    public BoxRepository(Plugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    /**
     * Load everything from the db into the memory
     *
     * @param registry the registry to load the boxes into
     */
    public CompletableFuture<Void> loadAll(BoxRegistry registry) {
        return dbManager.runAsync(conn -> {
            String queryBoxes = "SELECT box_uuid, display_name, owner_uuid FROM boxes";
            String queryMembers = "SELECT box_uuid, player_uuid FROM box_members";
            String queryAdvancements = "SELECT advancement_key, box_uuid FROM box_advancements";

            try {
                // Load every box first
                try (PreparedStatement ps = conn.prepareStatement(queryBoxes);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID boxUuid = UUID.fromString(rs.getString("box_uuid"));
                        Component displayName = MiniMessage.miniMessage().deserialize(rs.getString("display_name"));
                        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));

                        registry.registerBox(new Box(boxUuid, displayName, ownerUuid));
                    }
                }

                // Load every member
                try (PreparedStatement ps = conn.prepareStatement(queryMembers);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID boxUuid = UUID.fromString(rs.getString("box_uuid"));
                        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));

                        if (registry.getBoxByUuid(boxUuid) != null) {
                            registry.addMember(boxUuid, playerUuid);
                        }
                    }
                }

                // Load every advancement of boxes
                try (PreparedStatement ps = conn.prepareStatement(queryAdvancements);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID boxUuid = UUID.fromString(rs.getString("box_uuid"));
                        NamespacedKey advancementKey = NamespacedKey.fromString(rs.getString("advancement_key"));

                        if (registry.getBoxByUuid(boxUuid) != null) {
                            registry.addAvancement(boxUuid, advancementKey);
                        }
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error when loading boxes: " + e.getMessage());
            }
        });
    }

    /**
     * Save a new box into the db
     *
     * @param boxSnapshot A snapshot of the box to save
     */
    public CompletableFuture<Void> saveBox(BoxSnapshot boxSnapshot) {
        return dbManager.runAsync(conn -> {
            String insertBox = "INSERT OR REPLACE INTO boxes (box_uuid, display_name, owner_uuid) VALUES (?, ?, ?)";
            String insertMember = "INSERT OR IGNORE INTO box_members (box_uuid, player_uuid) VALUES (?, ?)";
            String insertAdvancement = "INSERT OR IGNORE INTO box_advancements (box_uuid, advancement_key) VALUES (?, ?)";

            try {
                conn.setAutoCommit(false);

                // boxes
                try (PreparedStatement ps = conn.prepareStatement(insertBox)) {
                    ps.setString(1, boxSnapshot.uuid().toString());
                    ps.setString(2, MiniMessage.miniMessage().serialize(boxSnapshot.displayName()));
                    ps.setString(3, boxSnapshot.owner().toString());
                    ps.executeUpdate();
                }

                // box members
                try (PreparedStatement psMember = conn.prepareStatement(insertMember)) {
                    for (UUID member : boxSnapshot.members()) {
                        psMember.setString(1, boxSnapshot.uuid().toString());
                        psMember.setString(2, member.toString());
                        psMember.addBatch();
                    }
                    psMember.executeBatch();
                }

                // Box advancement
                try (PreparedStatement psAdvancement = conn.prepareStatement(insertAdvancement)) {
                    for (NamespacedKey key : boxSnapshot.unlockedAdvancements()) {
                        psAdvancement.setString(1, boxSnapshot.uuid().toString());
                        psAdvancement.setString(2, key.asString());
                        psAdvancement.addBatch();
                    }
                    psAdvancement.executeBatch();
                }

                conn.commit();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                try {
                    conn.rollback();
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
                plugin.getLogger().severe("Error when saving a box : " + e.getMessage());
            }
        });
    }

    /**
     * Delete a box from the db
     *
     * @param boxUuid the uuid of the box to delete
     */
    public CompletableFuture<Void> deleteBox(UUID boxUuid) {
        return dbManager.runAsync(conn -> {
            String delete = "DELETE FROM boxes WHERE box_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(delete)) {
                ps.setString(1, boxUuid.toString().toLowerCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when deleting a box : " + e.getMessage());
            }
        });
    }

    /**
     * Add a member to a box
     *
     * @param boxUuid the uuid of the box to add the member to
     * @param playerUuid the uuid of the player to add
     */
    public CompletableFuture<Void> addMember(UUID boxUuid, UUID playerUuid) {
        return dbManager.runAsync(conn -> {
            String sql = "INSERT OR IGNORE INTO box_members (box_uuid, player_uuid) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, boxUuid.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when adding member: " + e.getMessage());
            }
        });
    }

    /**
     * Remove a member from a box
     *
     * @param boxUuid the uuid of the box to remove the member from
     * @param playerUuid the uuid of the player to remove
     */
    public CompletableFuture<Void> removeMember(UUID boxUuid, UUID playerUuid) {
        return dbManager.runAsync(conn -> {
            String sql = "DELETE FROM box_members WHERE box_uuid = ? AND player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, boxUuid.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when removing member: " + e.getMessage());
            }
        });
    }

    /**
     * Updates the display name of a box
     *
     * @param boxUuid the unique identifier of the box whose display name is to be updated
     * @param displayName the new display name to
     */
    public CompletableFuture<Void> updateDisplayName(UUID boxUuid, Component displayName) {
        return dbManager.runAsync(conn -> {
            String sql = "UPDATE boxes SET display_name = ? WHERE box_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, MiniMessage.miniMessage().serialize(displayName));
                ps.setString(2, boxUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when updating box display name : " + e.getMessage());
            }
        });
    }

    /**
     * Save an advancement to the db
     *
     * @param boxUuid the unique identifier of the box to which the advancement is to be saved
     * @param advancementKey the unique identifier of the advancement to be saved
     */
    public CompletableFuture<Void> saveAdvancement(UUID boxUuid, NamespacedKey advancementKey) {
        return dbManager.runAsync(conn -> {
            String sql = "INSERT OR IGNORE INTO box_advancements (box_uuid, advancement_key) VALUES (?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, boxUuid.toString());
                ps.setString(2, advancementKey.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save advancement to the db: " + e.getMessage());
            }
        });
    }

    /**
     *  Change the owner of a box
     *
     * @param box the box whose owner is to be changed
     * @param newOwnerUuid the UUID of the new owner
     * @param previousOwner the UUID of the previous owner
     */
    public CompletableFuture<Void> setOwner(Box box, UUID newOwnerUuid, UUID previousOwner) {
        return dbManager.runAsync(conn -> {
            String updateOwner = "UPDATE boxes SET owner_uuid = ? WHERE box_uuid = ?";
            String setPreviousOwnerAsAMember = "UPDATE box_members SET player_uuid = ? WHERE box_uuid = ? AND player_uuid = ?";

            try {
                conn.setAutoCommit(false);

                // Update owner
                try (PreparedStatement ps = conn.prepareStatement(updateOwner)) {
                    ps.setString(1, newOwnerUuid.toString());
                    ps.setString(2, box.getUuid().toString());
                    ps.executeUpdate();
                }

                // Replace the new owner in box_member with the previous owner
                try (PreparedStatement ps = conn.prepareStatement(setPreviousOwnerAsAMember)) {
                    ps.setString(1, previousOwner.toString());
                    ps.setString(2, box.getUuid().toString());
                    ps.setString(3, newOwnerUuid.toString());
                    ps.executeUpdate();
                }

                conn.commit();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                try {
                    conn.rollback();
                    conn.setAutoCommit(true);
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Rollback failed: " + rollbackEx.getMessage());
                }
                plugin.getLogger().severe("Failed to transfer box ownership: " + e.getMessage());
                throw new CompletionException(e);
            }
        });
    }
}