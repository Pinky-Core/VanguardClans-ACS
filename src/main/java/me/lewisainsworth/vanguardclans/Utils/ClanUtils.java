package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;

import java.sql.*;

import org.bukkit.World;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public class ClanUtils {

    private static VanguardClan plugin;

    public static void init(VanguardClan instance) {
        plugin = instance;
    }

    public static boolean isFriendlyFireEnabledAllies(String clan) {
        return VanguardClan.getInstance().getStorageProvider().isFriendlyFireAlliesEnabled(clan);
    }

    public static boolean areClansAllied(String clan1, String clan2) {
        return VanguardClan.getInstance().getStorageProvider().areClansAllied(clan1, clan2);
    }

    public boolean isWorldBlocked(World world) {
        List<String> blocked = plugin.getConfig().getStringList("blocked-worlds");
        return blocked.contains(world.getName());
    }
}
