package com.xm.thefourthfrequency.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class TerminalCommands {
	private TerminalCommands() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var root = Commands.literal("tff")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("debug")
							.then(Commands.argument("enabled", BoolArgumentType.bool())
									.executes(context -> {
										var player = context.getSource().getPlayerOrException();
										boolean enabled = BoolArgumentType.getBool(context, "enabled");
										if (!DebugPanelService.setEnabled(player, enabled)) {
											context.getSource().sendFailure(Component.translatable(
													"command.thefourthfrequency.debug.no_record"));
											return 0;
										}
										context.getSource().sendSuccess(() -> Component.translatable(enabled
												? "command.thefourthfrequency.debug.enabled"
												: "command.thefourthfrequency.debug.disabled"), false);
										return Command.SINGLE_SUCCESS;
									})))
					.then(Commands.literal("repair_terminal")
							.then(Commands.argument("player", EntityArgument.player())
									.executes(context -> {
										var target = EntityArgument.getPlayer(context, "player");
										if (!TerminalLifecycleService.adminRepair(target)) {
											context.getSource().sendFailure(Component.translatable(
													"command.thefourthfrequency.repair.no_record", target.getDisplayName()));
											return 0;
										}
										context.getSource().sendSuccess(() -> Component.translatable(
												"command.thefourthfrequency.repair.success", target.getDisplayName()), true);
										return Command.SINGLE_SUCCESS;
									})));
			dispatcher.register(root);
			// Compatibility for old development worlds; all new testing uses /tff.
			dispatcher.register(Commands.literal("thefourthfrequency")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("repair_terminal")
							.then(Commands.argument("player", EntityArgument.player())
									.redirect(dispatcher.getRoot().getChild("tff").getChild("repair_terminal")))));
		});
	}
}
