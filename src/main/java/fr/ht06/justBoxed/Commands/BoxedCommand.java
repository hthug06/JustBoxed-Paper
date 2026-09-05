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
                )
                .then(Commands.literal("delete")
                        .requires(source -> this.requirePlayerWithBox(source) && this.requireOwner(source))
                        .executes(this::deleteBox));
    }

    private boolean requireBoxlessPlayer(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) == null;
    }

    private boolean requirePlayerWithBox(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) != null;
    }

    private boolean requireOwner(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()).getOwner() == player.getUniqueId();
    }

    private int createBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can create a box!");
            return Command.SINGLE_SUCCESS;
        }
        String rawName = StringArgumentType.getString(ctx, "box_name");

        this.boxService.createBox(rawName, player).thenAccept(box -> {
            player.sendPlainMessage("Box created ! Teleporting...");
        }).exceptionally(ex -> {
            this.plugin.getLogger().severe("Error when creating box : " + ex.getMessage());
            player.sendPlainMessage("Failed to create a box (please contact an administrator)");
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private int deleteBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can delete a box!");
            return Command.SINGLE_SUCCESS;
        }

        // Check for box ownership is done in the brigadier
        this.boxService.deleteBox(JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId())).thenAccept(_ -> {
            player.sendPlainMessage("Box deleted !");
        }).exceptionally(ex -> {
            this.plugin.getLogger().severe("Error when deleting box : " + ex.getMessage());
            player.sendPlainMessage("Failed to delete box (please contact an administrator)");
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}
