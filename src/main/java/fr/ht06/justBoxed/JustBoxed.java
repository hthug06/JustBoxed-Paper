package fr.ht06.justBoxed;

import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxTemplate;
import fr.ht06.justBoxed.Commands.BoxedCommand;
import fr.ht06.justBoxed.Storage.BoxRepository;
import fr.ht06.justBoxed.Storage.DatabaseManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class JustBoxed extends JavaPlugin {

    private DatabaseManager databaseManager;
    private BoxRepository boxRepository;
    private BoxRegistry boxRegistry;

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
        this.databaseManager = new DatabaseManager(this);

        try {
            getLogger().info("Loading boxes...");
            this.databaseManager.init();
            this.boxRepository = new BoxRepository(this, this.databaseManager);

            // Loading async in RAM
            this.boxRepository.loadAll(this.boxRegistry).thenRun(() -> {
                getLogger().info("Successfully loading boxes !");
            });
        } catch (Exception e) {
            getLogger().severe("Critical error when initializing SQLite : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register the command via the LifecycleManager of the plugin
        BoxedCommand boxedCommand = new BoxedCommand();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands registrar = event.registrar();
            registrar.register(
                    boxedCommand.createCommand().build(),
                    "A command related to every box thing",
                    List.of("box")
            );
        });


        // On first launch, create a template world with seed 8500081009970950196 (every biome and structure in 1000 blocks)
        // else do nothing
        if (!BoxTemplate.exist()) {
            getLogger().info("Template world not found, creating it...");
            BoxTemplate.create(this);
            getLogger().info("Template world created !");
        }
    }

    @Override
    public void onDisable() {
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
    }
}
