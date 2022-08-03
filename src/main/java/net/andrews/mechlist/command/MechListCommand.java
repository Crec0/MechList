package net.andrews.mechlist.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.andrews.mechlist.GoogleSheets;
import net.andrews.mechlist.MechList;
import net.andrews.mechlist.Styles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.Whitelist;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.TreeSet;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MechListCommand {
	private static final String SHEET_ID_NAME = "sheet-id";
	private static final String SHEET_RANGE_NAME = "sheet-range";

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			literal("mechlist")
				.requires(source -> source.hasPermissionLevel(2))
				.then(literal("sync")
					.executes(MechListCommand::sync))
				.then(literal("reload-configs")
					.executes(MechListCommand::reloadConfigs))
				.then(literal("config")
					.then(literal(SHEET_ID_NAME)
						.then(argument(SHEET_ID_NAME, greedyString())
							.executes(MechListCommand::setSheetId)))
					.then(literal(SHEET_RANGE_NAME)
						.then(argument(SHEET_RANGE_NAME, greedyString())
							.executes(MechListCommand::setSheetRange)))
					.executes(MechListCommand::getConfig))
		);
	}

	private static int sync(CommandContext<ServerCommandSource> context) {
		context.getSource().sendFeedback(new LiteralText("Syncing..."), true);

		MechList.EXECUTOR.submit(() -> {
			Set<String> names = null;

			try {
				names = GoogleSheets.getUsernames();
			} catch (IOException | GeneralSecurityException e) {
				String error = "Failed to refresh mechlist. " + e;
				MechList.LOGGER.error(error);
			}

			if (names == null) {
				reply(
					context,
					new LiteralText("No Data found. Please Check server log for error and confirm your sheet id.").setStyle(Styles.RED.getStyle())
				);
				return;
			}

			MinecraftServer server = context.getSource().getServer();
			Whitelist whitelist = server.getPlayerManager().getWhitelist();
			PlayerManager manager = server.getPlayerManager();

			TreeSet<String> unwhitelistedPlayers = new TreeSet<>();
			TreeSet<String> deops = new TreeSet<>();

			for (String name : whitelist.getNames()) {
				if (names.contains(name.toLowerCase())) continue;
				GameProfile gameProfile = server.getUserCache().findByName(name).orElse(null);
				if (gameProfile != null) {
					unwhitelistedPlayers.add(name);
					whitelist.remove(gameProfile);

					if (manager.isOperator(gameProfile)) {
						deops.add(gameProfile.getName());
						manager.removeFromOperators(gameProfile);
					}
				}
			}

			TreeSet<String> whitelistedPlayers = new TreeSet<>();
			TreeSet<String> ops = new TreeSet<>();

			for (String name : names) {
				GameProfile gameProfile = server.getUserCache().findByName(name).orElse(null);

				if (gameProfile == null) {
					reply(
						context,
						new LiteralText("Failed to whitelist ").setStyle(Styles.RED.getStyle())
						   .append(new LiteralText(name).setStyle(Styles.GOLD.getStyle()))
						   .append(new LiteralText(". Please check the username.").setStyle(Styles.RED.getStyle()))
					);
					continue;
				}

				if (!whitelist.isAllowed(gameProfile)) {
					whitelistedPlayers.add(gameProfile.getName());
					whitelist.add(new WhitelistEntry(gameProfile));
				}
				if (MechList.getConfig().shouldOpAll() && !manager.isOperator(gameProfile)) {
					ops.add(gameProfile.getName());
					manager.addToOperators(gameProfile);
				}
			}

			server.kickNonWhitelistedPlayers(context.getSource());
			reply(
				context,
				new LiteralText("Whitelist updated").setStyle(Styles.DARK_GREEN_BOLD.getStyle())
					.append(new LiteralText("\nWhitelisted: ").setStyle(Styles.DARK_GREEN.getStyle()))
					.append(new LiteralText(whitelistedPlayers.toString()).setStyle(Styles.DARK_AQUA.getStyle()))
					.append(new LiteralText("\nOps: ").setStyle(Styles.DARK_GREEN.getStyle()))
					.append(new LiteralText(ops.toString()).setStyle(Styles.DARK_AQUA.getStyle()))
					.append(new LiteralText("\nUn-whitelisted: ").setStyle(Styles.DARK_GREEN.getStyle()))
					.append(new LiteralText(unwhitelistedPlayers.toString()).setStyle(Styles.DARK_AQUA.getStyle()))
					.append(new LiteralText("\nDe-ops: ").setStyle(Styles.DARK_GREEN.getStyle()))
					.append(new LiteralText(deops.toString()).setStyle(Styles.DARK_AQUA.getStyle()))
			);
		});

		return 1;
	}

	private static int setSheetId(CommandContext<ServerCommandSource> context) {
		String sheetId = context.getArgument(SHEET_ID_NAME, String.class).trim();
		MechList.getConfig().setSpreadsheetId(sheetId);
		reply(context, Text.of("Sheet ID set to: " + sheetId));
		return 1;
	}

	private static int setSheetRange(CommandContext<ServerCommandSource> context) {
		String range = context.getArgument("sheet-range", String.class).trim();
		MechList.getConfig().setSheetRange(range);
		reply(context, Text.of("Sheet Range set to: " + range));
		return 1;
	}

	private static int getConfig(CommandContext<ServerCommandSource> context) {
		var config = MechList.getConfig();

		reply(context,
			new LiteralText("Mechlist Config").setStyle(Styles.DARK_GREEN_BOLD.getStyle())
				.append(new LiteralText(           "\nSheet ID: ").setStyle(Styles.DARK_GREEN.getStyle()))
				.append(new LiteralText(   config.spreadsheetId()).setStyle(Styles.DARK_AQUA.getStyle()))
				.append(new LiteralText(        "\nSheet Range: ").setStyle(Styles.DARK_GREEN.getStyle()))
				.append(new LiteralText(      config.sheetRange()).setStyle(Styles.DARK_AQUA.getStyle()))
			    .append(new LiteralText(      "\nShould Op all: ").setStyle(Styles.DARK_GREEN.getStyle()))
				.append(new LiteralText("" + config.shouldOpAll()).setStyle(Styles.DARK_AQUA.getStyle()))
		);
		return 1;
	}

	private static int reloadConfigs(CommandContext<ServerCommandSource> context) {
		MechList.setConfig(MechList.getConfig().loadFromFile());
		reply(context, Text.of("Config Reloaded"));
		return 1;
	}

	private static void reply(CommandContext<ServerCommandSource> context, Text message) {
		context.getSource().sendFeedback(message, true);
	}
}
