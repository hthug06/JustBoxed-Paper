package fr.ht06.justBoxed;

import fr.ht06.justBoxed.Box.*;
import fr.ht06.justBoxed.Commands.AdminBoxedCommand;
import fr.ht06.justBoxed.Commands.BoxedCommand;
import fr.ht06.justBoxed.Listeners.PlayerListeners;
import fr.ht06.justBoxed.Storage.BoxRepository;
import fr.ht06.justBoxed.Storage.DatabaseManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class JustBoxed extends JavaPlugin {

    private DatabaseManager databaseManager;
    private BoxRegistry boxRegistry;
    private BoxWorldManager boxWorldManager;

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
        BoxRepository boxRepository;
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

        // Start the world manager
        this.boxWorldManager = new BoxWorldManager(this);

        // Start the box service with the registry and the repository
        BoxService boxService = new BoxService(this, this.boxRegistry, boxRepository, this.boxWorldManager);

        // Register the command via the LifecycleManager of the plugin
        BoxedCommand boxedCommand = new BoxedCommand(this, boxService);
        AdminBoxedCommand adminBoxedCommand = new AdminBoxedCommand(this, boxService);
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
        getServer().getPluginManager().registerEvents(new PlayerListeners(boxService, this.boxRegistry), this);

        createTemplatesWorld();
    }

    @Override
    public void onDisable() {
        if (this.boxRegistry != null) {
            // remove every invitation to avoid memory leak on reload
            for (Box box : this.boxRegistry.getAllBoxes()) {
                box.clearInvitations();

                this.boxWorldManager.unloadAllWorlds(box, true);
            }
        }

        // Close the connection to the database
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
    }

    /// On first launch, create a template world with seed 8500081009970950196 (every biome and structure in 1000 blocks)
    /// It will be used to create boxes by copying it instead of generating a new world
    private void createTemplatesWorld() {
        if (!BoxTemplate.exist(this)) {
            getComponentLogger().info(Component.text("Some Templates worlds not found, creating them...", NamedTextColor.RED));

            if (BoxTemplate.dimensionsExists(this, World.Environment.NORMAL)){
                getComponentLogger().info(Component.text("Found overworld template !", NamedTextColor.DARK_GREEN));
            }
            else {
                getComponentLogger().info(Component.text("Overworld template not found, creating it...", NamedTextColor.RED));
                BoxTemplate.create(this, World.Environment.NORMAL);
                getComponentLogger().info(Component.text("Overworld template created !", NamedTextColor.GREEN));
            }

            if (BoxTemplate.dimensionsExists(this, World.Environment.NETHER)){
                getComponentLogger().info(Component.text("Found nether template !", NamedTextColor.DARK_GREEN));
            }
            else {
                getComponentLogger().info(Component.text("Nether template not found, creating it...", NamedTextColor.RED));
                BoxTemplate.create(this, World.Environment.NETHER);
                getComponentLogger().info(Component.text("Nether template created !", NamedTextColor.GREEN));
            }

            getComponentLogger().info(Component.text("Templates worlds are all created !", NamedTextColor.DARK_GREEN));
        }
        else{
            getComponentLogger().info(Component.text("Found all world templates !", NamedTextColor.DARK_GREEN));
        }
    }



}
