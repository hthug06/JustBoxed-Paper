package fr.ht06.justBoxed;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.Box.BoxTemplate;
import fr.ht06.justBoxed.Commands.AdminBoxedCommand;
import fr.ht06.justBoxed.Commands.BoxedCommand;
import fr.ht06.justBoxed.Listeners.PlayerListeners;
import fr.ht06.justBoxed.Storage.BoxRepository;
import fr.ht06.justBoxed.Storage.DatabaseManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class JustBoxed extends JavaPlugin {

    private DatabaseManager databaseManager;
    private BoxRepository boxRepository;
    private BoxRegistry boxRegistry;
    private BoxService boxService;

    public BoxRegistry getBoxRegistry() {
        return this.boxRegistry;
    }

    public static JustBoxed getInstance() {
        return getPlugin(JustBoxed.class);
    }

    @Override
    public void onEnable() {
        // launch the box registry
        this.boxRegistry = new BoxRegistry();

        // Init the db manager
        this.databaseManager = new DatabaseManager(this);
        try {
            getLogger().info("Loading boxes...");
            this.databaseManager.init();
            boxRepository = new BoxRepository(this, this.databaseManager);

            // Loading async in RAM
            boxRepository.loadAll(this.boxRegistry).thenRun(() -> getLogger().info("Successfully loading boxes !"));
        } catch (Exception e) {
            getLogger().severe("Critical error when initializing SQLite : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Start the box service with the registry and the repository
        this.boxService = new BoxService(this, this.boxRegistry, boxRepository);

        // Register the command via the LifecycleManager of the plugin
        BoxedCommand boxedCommand = new BoxedCommand(this, this.boxService);
        AdminBoxedCommand adminBoxedCommand = new AdminBoxedCommand(this, this.boxService);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands registrar = event.registrar();
            registrar.register(
                    boxedCommand.createCommand().build(),
                    "A command related to every box thing"
            );
            registrar.register(
                    adminBoxedCommand.createCommand().build(),
                    "Admin command for boxed plugin"
            );
        });

        // Register Listeners / events
        getServer().getPluginManager().registerEvents(new PlayerListeners(this, this.boxService, this.boxRegistry), this);


        // On first launch, create a template world with seed 8500081009970950196 (every biome and structure in 1000 blocks)
        // It will be used to create boxes by copying it instead of generating a new world
        if (!BoxTemplate.exist(this)) {
            getLogger().info("Template world not found, creating it...");
            BoxTemplate.create(this);
            getLogger().info("Template world created !");
        }
    }

    @Override
    public void onDisable() {
        if (this.boxRegistry != null) {
            Location fallbackSpawn = Bukkit.getWorlds().getFirst().getSpawnLocation();

            // remove every invitation to avoid memory leak on reload
            for (Box box : this.boxRegistry.getAllBoxes()) {
                box.clearInvitations();

                // Teleport player to another world because if this is a box world,
                // Next time they rejoint, they're going to spawn in the base world but at the coordinate of their box world
                World world = box.getWorld(this);
                if (world != null) {
                    for (Player p : world.getPlayers()) {
                        p.teleport(fallbackSpawn);
                    }

                    Bukkit.unloadWorld(world, true);
                }
            }
        }

        // Close the connection to the database
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
    }



}
