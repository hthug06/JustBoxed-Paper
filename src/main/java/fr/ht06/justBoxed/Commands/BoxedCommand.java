package fr.ht06.justBoxed.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public class BoxedCommand {
    private final JustBoxed plugin;
    private final BoxService boxService;


    public BoxedCommand(JustBoxed plugin, BoxService boxService) {
        this.plugin = plugin;
        this.boxService = boxService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("box")
                .then(Commands.literal("create")
                        .requires(this::requireBoxlessPlayer)
                        .then(Commands.argument("box_name", StringArgumentType.greedyString())
                                .executes(this::createBox))
                );
    }

    private boolean requireBoxlessPlayer(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) == null;
    }

    private int createBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can create a box!");
            return Command.SINGLE_SUCCESS;
        }
        String rawName = StringArgumentType.getString(ctx, "box_name");

        this.boxService.createBox(rawName, player).thenAccept(box -> {
            player.sendPlainMessage("Box created ! Teleporting...");
            player.teleportAsync(box.getWorld(this.plugin).getSpawnLocation().toCenterLocation());
        }).exceptionally(ex -> {
            player.sendPlainMessage("Error when creating box : " + ex.getMessage());
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}
