package fr.ht06.justBoxed.Storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Plugin plugin;
    private Connection connection;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /// Create the database with tables and open a UNIQUE connection
    public void init() throws SQLException, IOException, ClassNotFoundException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Failed to create folder " + dataFolder.getPath());
        }

        Class.forName("org.sqlite.JDBC");
        this.connection = DriverManager.getConnection("jdbc:sqlite:plugins/TestPlugin/data.db");

        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Boxes Table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS boxes (
                    box_uuid TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    owner_uuid TEXT NOT NULL,
                );
            """);

            // box_member table
            // 1 member -> 1 box | 1 box -> N members
            statement.execute("""
                CREATE TABLE IF NOT EXISTS box_members (
                    box_uuid TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (box_uuid, player_uuid),
                    FOREIGN KEY (box_uuid) REFERENCES boxes(box_uuid) ON DELETE CASCADE
                );
            """);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when closing sqlite : " + e.getMessage());
        }
    }
}