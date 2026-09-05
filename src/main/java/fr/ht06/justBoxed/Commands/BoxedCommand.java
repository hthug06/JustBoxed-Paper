package fr.ht06.justBoxed.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.ht06.justBoxed.Box.BoxCreator;
import fr.ht06.justBoxed.JustBoxed;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.SignedMessageResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class BoxedCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("box")
                // Only if the player doesn't have a box
                .then(Commands.literal("create")
                        .requires(BoxedCommand::requireBoxlessPlayer)
                        .then(Commands.argument("box name", ArgumentTypes.signedMessage())
                                .executes(BoxedCommand::createBox))
                );
    }

    private static boolean requireBoxlessPlayer(CommandSourceStack source){
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) == null;
    }

    private static boolean requirePlayerWithBox(CommandSourceStack source){
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) != null;
    }

    private static int createBox(CommandContext<CommandSourceStack> ctx) {
        final SignedMessageResolver boxDisplayName = ctx.getArgument("box name", SignedMessageResolver.class);
        Component displayName = MiniMessage.miniMessage().deserialize(boxDisplayName.content());
        CommandSender sender = ctx.getSource().getSender(); // Retrieve the command sender
        Entity executor = ctx.getSource().getExecutor(); // Retrieve the command executor, which may or may not be the same as the sender

        if (!(executor instanceof Player player)) {
            sender.sendPlainMessage("Only players can create a box!");
            return Command.SINGLE_SUCCESS;
        }

        BoxCreator.createBoxInstance(JustBoxed.getInstance(), displayName, executor.getUniqueId(), world -> {
            if (world != null) {
                player.teleportAsync(world.getSpawnLocation().toCenterLocation());
                player.sendMessage("Box ready, teleporting...");
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
