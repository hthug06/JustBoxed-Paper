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
            String queryBoxes = "SELECT box_uuid, display_name, owner_uuid FROM boxes";
            String queryMembers = "SELECT player_uuid FROM box_members WHERE box_uuid = ?";

            try (PreparedStatement psBoxes = dbManager.getConnection().prepareStatement(queryBoxes);
                 ResultSet rsBoxes = psBoxes.executeQuery()) {

                while (rsBoxes.next()) {
                    UUID boxUuid = UUID.fromString(rsBoxes.getString("box_uuid"));
                    String displayName = rsBoxes.getString("display_name");
                    UUID ownerUuid = UUID.fromString(rsBoxes.getString("owner_uuid"));

                    Box box = new Box(boxUuid, Component.text(displayName), ownerUuid);

                    try (PreparedStatement psMembers = dbManager.getConnection().prepareStatement(queryMembers)) {
                        psMembers.setString(1, boxUuid.toString());
                        try (ResultSet rsMembers = psMembers.executeQuery()) {
                            while (rsMembers.next()) {
                                box.addMember(UUID.fromString(rsMembers.getString("player_uuid")));
                            }
                        }
                    }

                    registry.registerBox(box);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error when loading boxes : " + e.getMessage());
            }
        });
    }

    /// Save a new box into the db
    public CompletableFuture<Void> saveBox(Box box) {
        return CompletableFuture.runAsync(() -> {
            String insertBox = "INSERT OR REPLACE INTO boxes (box_uuid, display_name, owner_uuid) VALUES (?, ?, ?)";
            String insertMember = "INSERT OR IGNORE INTO box_members (box_uuid, player_uuid) VALUES (?, ?)";

            try (Connection conn = dbManager.getConnection()) {
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
                plugin.getLogger().severe("Error when saving a box : " + e.getMessage());
            }
        });
    }

    /// Delete a box
    public CompletableFuture<Void> deleteBox(UUID boxUuid) {
        return CompletableFuture.runAsync(() -> {
            String delete = "DELETE FROM boxes WHERE box_uuid = ?";
            try (PreparedStatement ps = dbManager.getConnection().prepareStatement(delete)) {
                ps.setString(1, boxUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur when deleting a box : " + e.getMessage());
            }
        });
    }
}