package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import org.bukkit.ChatColor;


import java.util.List;
import java.util.regex.Pattern;

import me.lewisainsworth.vanguardclans.Utils.MSG;


public class ClanNameHandler {

    public static final int DEFAULT_MAX_VISIBLE_LENGTH = 16;
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&(#?[a-f0-9]{6})");
    private static final Pattern FORMAT_CODES = Pattern.compile("(?i)&[0-9a-fk-or]");

    public static String getVisibleName(String raw) {
        if (raw == null) return "";
        String noHex = HEX_PATTERN.matcher(raw).replaceAll("");
        return FORMAT_CODES.matcher(noHex).replaceAll("");
    }

    public static boolean isValid(String raw, int maxVisibleLength) {
        if (maxVisibleLength <= 0) return true;
        return getVisibleName(raw).length() <= maxVisibleLength;
    }

    private static int getMaxVisibleLength(VanguardClan plugin) {
        if (plugin == null || plugin.getFH() == null) {
            return DEFAULT_MAX_VISIBLE_LENGTH;
        }
        return plugin.getFH().getConfig().getInt("clan-name.max-length", DEFAULT_MAX_VISIBLE_LENGTH);
    }

    public static void insertClan(VanguardClan plugin, String rawName, String founder, String leader) {
        LangManager lang = plugin.getLangManager();
        int maxVisibleLength = getMaxVisibleLength(plugin);

        String visibleName = getVisibleName(rawName);

        if (!isValid(rawName, maxVisibleLength)) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.create_name_too_long")
                    .replace("{max}", String.valueOf(maxVisibleLength)))
            );
        }

        List<String> blocked = plugin.getFH().getConfig().getStringList("names-blocked.blocked");
        if (blocked.stream().anyMatch(b -> visibleName.equalsIgnoreCase(b))) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.create_name_blocked"))
            );
        }

        String colored = MSG.color(rawName);

        // Use StorageProvider instead of direct SQL
        plugin.getStorageProvider().createClan(visibleName, colored, founder, leader, 0.0, "private");
    }

    public static void updateClanName(VanguardClan plugin, String oldName, String newRawName) {
        LangManager lang = plugin.getLangManager();
        int maxVisibleLength = getMaxVisibleLength(plugin);

        String newVisible = ChatColor.stripColor(MSG.color(newRawName)); // quitar color
        String newColored = MSG.color(newRawName); // aplicar color

        // Validar longitud visible
        if (!isValid(newVisible, maxVisibleLength)) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.edit_name_too_long")
                    .replace("{max}", String.valueOf(maxVisibleLength)))
            );
        }

        // Validar nombres bloqueados
        List<String> blocked = plugin.getFH().getConfig().getStringList("names-blocked.blocked");
        if (blocked.stream().anyMatch(b -> newVisible.equalsIgnoreCase(b))) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.edit_name_blocked"))
            );
        }

        // Verificar que no exista un clan con ese nombre
        if (plugin.getStorageProvider().clanExists(newVisible)) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.edit_name_exists"))
            );
        }

        // Use StorageProvider instead of direct SQL
        plugin.getStorageProvider().updateClanName(oldName, newVisible, newColored);
    }
}
