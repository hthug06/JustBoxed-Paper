package fr.ht06.justBoxed.Storage;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
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

    /// Load everything from the db into the memory
    public CompletableFuture<Void> loadAll(BoxRegistry registry) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = dbManager.getConnection();
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

    /// Save a new box into the db
    public CompletableFuture<Void> saveBox(Box box) {
        return CompletableFuture.runAsync(() -> {
            String insertBox = "INSERT OR REPLACE INTO boxes (box_uuid, display_name, owner_uuid) VALUES (?, ?, ?)";
            String insertMember = "INSERT OR IGNORE INTO box_members (box_uuid, player_uuid) VALUES (?, ?)";
            String insertAdvancement = "INSERT OR IGNORE INTO box_advancements (box_uuid, advancement_key) VALUES (?, ?)";

            Connection conn = dbManager.getConnection();
            try {
                conn.setAutoCommit(false);

                // boxes
                try (PreparedStatement ps = conn.prepareStatement(insertBox)) {
                    ps.setString(1, box.getUuid().toString());
                    ps.setString(2, MiniMessage.miniMessage().serialize(box.getDisplayName()));
                    ps.setString(3, box.getOwner().toString());
                    ps.executeUpdate();
                }

                // box members
                try (PreparedStatement psMember = conn.prepareStatement(insertMember)) {
                    for (UUID member : box.getMembers()) {
                        psMember.setString(1, box.getUuid().toString());
                        psMember.setString(2, member.toString());
                        psMember.addBatch();
                    }
                    psMember.executeBatch();
                }

                // Box advancement
                try (PreparedStatement psAdvancement = conn.prepareStatement(insertAdvancement)) {
                    for (NamespacedKey key : box.getUnlockedAdvancements()) {
                        psAdvancement.setString(1, box.getUuid().toString());
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

    /// Delete a box
    public CompletableFuture<Void> deleteBox(UUID boxUuid) {
        return CompletableFuture.runAsync(() -> {
            String delete = "DELETE FROM boxes WHERE box_uuid = ?";
            Connection conn = dbManager.getConnection();

            try (PreparedStatement ps = conn.prepareStatement(delete)) {
                ps.setString(1, boxUuid.toString().toLowerCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when deleting a box : " + e.getMessage());
            }
        });
    }

    /// Add a member to a box
    public CompletableFuture<Void> addMember(UUID boxUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR IGNORE INTO box_members (box_uuid, player_uuid) VALUES (?, ?)";
            Connection conn = dbManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, boxUuid.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when adding member: " + e.getMessage());
            }
        });
    }

    /// Remove a member from a box
    public CompletableFuture<Void> removeMember(UUID boxUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM box_members WHERE box_uuid = ? AND player_uuid = ?";
            Connection conn = dbManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, boxUuid.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when removing member: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> updateDisplayName(UUID boxUuid, String displayName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE boxes SET display_name = ? WHERE box_uuid = ?";
            Connection conn = dbManager.getConnection();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, displayName);
                ps.setString(2, boxUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when updating box display name : " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> saveAdvancement(UUID boxUuid, NamespacedKey advancementKey) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR IGNORE INTO box_advancements (box_uuid, advancement_key) VALUES (?, ?)";

            Connection conn = dbManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, boxUuid.toString());
                ps.setString(2, advancementKey.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save advancement to the db: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> setOwner(Box box, UUID newOwnerUuid, UUID previousOwner) {
        return CompletableFuture.runAsync(() -> {
            String updateOwner = "UPDATE boxes SET owner_uuid = ? WHERE box_uuid = ?";
            String setPreviousOwnerAsAMember = "UPDATE box_members SET player_uuid = ? WHERE box_uuid = ? AND player_uuid = ?";

            Connection conn = dbManager.getConnection();
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