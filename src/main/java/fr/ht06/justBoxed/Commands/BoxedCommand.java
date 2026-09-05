package fr.ht06.justBoxed.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.ht06.justBoxed.Box.BoxCreator;
import fr.ht06.justBoxed.JustBoxed;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public class BoxedCommand {

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
        Component displayName = MiniMessage.miniMessage().deserialize(rawName);

        player.sendPlainMessage("Creating box...");

        BoxCreator.createBoxInstance(JustBoxed.getInstance(), displayName, player.getUniqueId(), world -> {
            if (world != null) {
                player.teleportAsync(world.getSpawnLocation().toCenterLocation());
                player.sendMessage("Box ready, teleporting...");
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
