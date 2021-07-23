package net.andrews.mechlist;

import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;

public class Utils {

    public static Path getConfigDir() {
       return FabricLoader.getInstance().getConfigDir();
    }
    public static void sendPacket(ServerPlayerEntity player, Packet<?> packet) {
        if (player.isDisconnected()) return;
        player.networkHandler.sendPacket(packet);
    }

    public static String readTextFile(Path path)
    {
    try {
       return new String(Files.readAllBytes(path));
    } catch (Exception e) {
        System.out.println("[MechList] Failed to read file " + path);
        return null;
    }
    }
    public static void writeTextFile(Path path, String str) {

        byte[] strToBytes = str.getBytes();
        try {
        Files.write(path, strToBytes);
        } catch (Exception e) {
            System.out.println("[MechList] Failed to write to file " + path);
        }
    }

    public static String wordWrap(String str,int maxWidth) {
        String newLineStr = "\n";
        boolean found = false;
        String res = "";
        while (str.length() > maxWidth) {                 
            found = false;
            // Inserts new line at first whitespace of the line
            for (int i = maxWidth - 1; i >= 0; i--) {
                if (testWhite(str.charAt(i))) {
                    res = res + str.substring(0, i) + newLineStr;
                    str = str.substring(i + 1);
                    found = true;
                    break;
                }
            }
            // Inserts new line at maxWidth position, the word is too long to wrap
            if (!found) {
                res += str.substring(0, maxWidth) + newLineStr;
                str = str.substring(maxWidth);
            }
        }
        return res + str;
    }
    private static boolean testWhite(char charAt) {
        return charAt == ' ';
    }
    
}
