package fr.ht06.justBoxed.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class AdminBoxedCommand {
    private final JustBoxed plugin;
    private final BoxService boxService;

    public AdminBoxedCommand(JustBoxed plugin, BoxService boxService) {
        this.plugin = plugin;
        this.boxService = boxService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("abox")
                .requires(source -> source.getSender().isOp())
                .then(Commands.literal("change_world")
                        .then(Commands.argument("world", ArgumentTypes.world())
                                .executes(this::executeChangeWorld)
                        )
                );
    }

    public int executeChangeWorld(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can create a box!");
            return Command.SINGLE_SUCCESS;
        }
        World world = ctx.getArgument("world", World.class);

        player.teleportAsync(world.getSpawnLocation()).thenAcceptAsync(_ -> {
            player.sendPlainMessage("Teleported to " + world.getName());
        });


        return Command.SINGLE_SUCCESS;
    }
}
