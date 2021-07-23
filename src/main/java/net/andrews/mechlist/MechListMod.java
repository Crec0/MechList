package net.andrews.mechlist;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Whitelist;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.thread.ThreadExecutor;

public class MechListMod {

    private static String authURL = null;

    public static ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void init() {

    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        Configs.loadFromFile();
        dispatcher.register(CommandManager.literal("mechlist").requires((serverCommandSource) -> {
            return serverCommandSource.hasPermissionLevel(2);
        }).then(CommandManager.literal("sync").executes(MechListMod::refreshCommand))
                .then(CommandManager.literal("config").then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(Configs.getFields(), b)).then(CommandManager
                                .argument("value", StringArgumentType.greedyString()).executes(MechListMod::setConfig))
                        .executes(MechListMod::getConfig)))

        );

    }

    private static boolean notifyAuth(CommandContext<ServerCommandSource> ctx) {
        if (authURL == null)
            return false;
        String link = authURL;
        sendFeedback(ctx, Text.Serializer.fromJson(
                "{\"text\":\"[Authenticate]\",\"color\":\"dark_green\",\"underlined\":true,\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\""
                        + link + "\"}]},\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + link + "\"}}"),
                true);
        return true;
    }

    private static int refreshCommand(CommandContext<ServerCommandSource> ctx) {
        try {
            GoogleSheets.reset();
            executor.submit(() -> {
                try {
                    Set<String> names = GoogleSheets.getUsernames(() -> {
                        notifyAuth(ctx);
                    });

                    if (names == null) {
                        sendFeedback(ctx, "No data!", true);
                        return;
                    }

                    MinecraftServer server = ctx.getSource().getMinecraftServer();
                    ((ThreadExecutor) server).execute(() -> {
                        Whitelist whitelist = server.getPlayerManager().getWhitelist();

                       
                        int removed = 0;
                        for (String name : whitelist.getNames()) {
                            if (names.contains(name)) continue;
                            GameProfile gameProfile = server.getUserCache().findByName(name);
                            if (gameProfile != null) {
                                if (server.getPermissionLevel(gameProfile) < 2) {
                                    removed++;
                                    whitelist.remove(gameProfile);
                                }
                            }
                        }
                        int count = 0;
                        for (String name : names) {
                            GameProfile gameProfile = server.getUserCache().findByName(name);
                            if (gameProfile != null) {
                                count++;

                                if (!whitelist.isAllowed(gameProfile)) {
                                    whitelist.add(new WhitelistEntry(gameProfile));
                                }

                            }
                        }
                        server.kickNonWhitelistedPlayers(ctx.getSource());

                        sendFeedback(ctx, "Whitelisted " + count + " players from sheet and unwhitelisted " + removed + " players", true);

                    });

                } catch (Exception e) {
                    sendFeedback(ctx, "An error has occured: " + e, true);
                }

            });

        } catch (Exception e) {
            sendFeedback(ctx, "An error has occured: " + e, true);

        }
        return 1;
    }

    private static int setConfig(CommandContext<ServerCommandSource> ctx) {
        try {

            String name = StringArgumentType.getString(ctx, "name");
            String value = StringArgumentType.getString(ctx, "value");

            if (Configs.setConfig(name, value)) {
                sendFeedback(ctx, "Set " + name + " to:\n" + value, true);
            } else {
                sendFeedback(ctx, "Failed to set " + name + " to " + value, true);
            }

        } catch (Exception e) {
            sendFeedback(ctx, "An error has occured: " + e, true);

        }
        return 1;
    }

    private static int getConfig(CommandContext<ServerCommandSource> ctx) {
        try {

            String name = StringArgumentType.getString(ctx, "name");

            sendFeedback(ctx, name + " is set to:\n" + Configs.getConfig(name), true);

        } catch (Exception e) {
            sendFeedback(ctx, "An error has occured: " + e, true);

        }
        return 1;
    }

    private static void sendFeedback(CommandContext<ServerCommandSource> ctx, String str, boolean ops) {
        ctx.getSource().sendFeedback(new LiteralText(str), ops);
    }

    private static void sendFeedback(CommandContext<ServerCommandSource> ctx, String str) {
        sendFeedback(ctx, str, false);
    }

    private static void sendFeedback(CommandContext<ServerCommandSource> ctx, Text str, boolean ops) {
        ctx.getSource().sendFeedback(str, ops);
    }

    public static void setAuthUrl(String url) {
        authURL = url;
    }

}
