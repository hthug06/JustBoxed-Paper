package fr.ht06.justBoxed.Storage;

import fr.ht06.justBoxed.JustBoxed;
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

    /// Create the database with tables and open a connection
    public void init() throws SQLException, IOException, ClassNotFoundException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Failed to create folder " + dataFolder.getPath());
        }

        Class.forName("org.sqlite.JDBC");
        this.connection = DriverManager.getConnection("jdbc:sqlite:plugins/JustBoxed/data.db");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Boxes Table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS boxes (
                    box_uuid TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    owner_uuid TEXT NOT NULL
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

            // box_advancements table
            // Contains every box with all their advancement
            // Link to a box | 1 box -> N advancement
            statement.execute("""
                CREATE TABLE IF NOT EXISTS box_advancements (
                    box_uuid TEXT NOT NULL,
                    advancement_key TEXT NOT NULL,
                    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (box_uuid, advancement_key),
                    FOREIGN KEY (box_uuid) REFERENCES boxes(box_uuid) ON DELETE CASCADE
                );
            """);


        }
    }

    public Connection getConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                File dbFile = new File(plugin.getDataFolder(), "data.db");
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                this.connection = DriverManager.getConnection(url);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get SQLite connection : " + e.getMessage());
            plugin.getServer().getPluginManager().disablePlugin(JustBoxed.getInstance());
        }
        return this.connection;
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