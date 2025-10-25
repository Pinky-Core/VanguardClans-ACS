package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.LangManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.bukkit.ChatColor;


import java.util.List;
import java.util.regex.Pattern;

import me.lewisainsworth.vanguardclans.Utils.MSG;


public class ClanNameHandler {

    private static final int MAX_VISIBLE_LENGTH = 16;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}");
    private static final Pattern FORMAT_CODES = Pattern.compile("&[0-9a-fl-or]");

    public static String getVisibleName(String raw) {
        if (raw == null) return "";
        String noHex = HEX_PATTERN.matcher(raw).replaceAll("");
        return FORMAT_CODES.matcher(noHex).replaceAll("");
    }

    public static boolean isValid(String raw) {
        return getVisibleName(raw).length() <= MAX_VISIBLE_LENGTH;
    }

    public static void insertClan(VanguardClan plugin, String rawName, String founder, String leader) {
        LangManager lang = plugin.getLangManager();

        String visibleName = getVisibleName(rawName);

        if (!isValid(rawName)) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.create_name_too_long")
                    .replace("{max}", String.valueOf(MAX_VISIBLE_LENGTH)))
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

        String newVisible = ChatColor.stripColor(MSG.color(newRawName)); // quitar color
        String newColored = MSG.color(newRawName); // aplicar color

        // Validar longitud visible
        if (!isValid(newVisible)) {
            throw new IllegalArgumentException(
                MSG.color(lang.getMessageWithPrefix("user.edit_name_too_long")
                    .replace("{max}", String.valueOf(MAX_VISIBLE_LENGTH)))
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
