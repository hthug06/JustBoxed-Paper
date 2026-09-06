package fr.ht06.justBoxed.Storage;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

                // And then load every member
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
}