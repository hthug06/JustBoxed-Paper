package fr.ht06.justBoxed.Storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DatabaseManager {

    private final Plugin plugin;
    private Connection connection;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JustBoxed-SQLite-Thread");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database manager by setting up essential resources and configurations.
     *
     * <ul>
     *   <li>Ensures the plugin's data folder exists. If it does not exist, attempts to create it.
     *       Throws an {@link IOException} if the folder creation fails.</li>
     *   <li>Loads the SQLite JDBC driver class and establishes a database connection to
     *       a file-based SQLite database located at "plugins/JustBoxed/data.db".</li>
     *   <li>Enables foreign key constraints for the SQLite database by executing the necessary
     *       PRAGMA command.</li>
     *   <li>Invokes the {@code createTables} method to ensure all required database tables
     *       are created if they do not already exist.</li>
     * </ul>
     *
     * @throws SQLException if a database access error occurs during connection setup
     *                      or table creation.
     * @throws IOException if the plugin's data folder cannot be created.
     * @throws ClassNotFoundException if the SQLite JDBC driver class is not found on the classpath.
     */
    public void init() throws SQLException, IOException, ClassNotFoundException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Failed to create folder " + dataFolder.getPath());
        }

        Class.forName("org.sqlite.JDBC");
        File dbFile = new File(dataFolder, "data.db");
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
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
        return this.connection;
    }

    /**
     * Execute a writing task to the database on the SQLite thread
     *
     * @param action the {@link Consumer} that accepts a {@link Connection} and defines the operation
     *               to be performed asynchronously
     * @return a {@link CompletableFuture} that completes when the asynchronous operation finishes
     */
    public CompletableFuture<Void> runAsync(Consumer<Connection> action) {
        return CompletableFuture.runAsync(() -> action.accept(getConnection()), dbExecutor);
    }

    /**
     * Close the database connection and shutdown the executor
     */
    public void close() {
        dbExecutor.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when closing sqlite : " + e.getMessage());
        }
    }
}