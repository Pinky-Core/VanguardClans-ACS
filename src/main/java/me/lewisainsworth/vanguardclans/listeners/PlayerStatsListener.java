package me.lewisainsworth.vanguardclans.listeners;

import me.lewisainsworth.vanguardclans.VanguardClan;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import me.lewisainsworth.vanguardclans.Utils.MSG;

public class PlayerStatsListener implements Listener {

    private final VanguardClan plugin;

    public PlayerStatsListener(VanguardClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        Player killer = deceased.getKiller();

        incrementDeaths(deceased.getName());

        if (killer != null && !killer.getName().equalsIgnoreCase(deceased.getName())) {
            incrementKills(killer.getName());
            awardClanKillPoints(killer, deceased);
        }
    }

    private void incrementKills(String playerName) {
        plugin.getStorageProvider().incrementPlayerKills(playerName);
    }

    private void incrementDeaths(String playerName) {
        plugin.getStorageProvider().incrementPlayerDeaths(playerName);
    }

    private void awardClanKillPoints(Player killer, Player victim) {
        FileConfiguration config = plugin.getFH().getConfig();
        if (!config.getBoolean("clan-slots.enabled", false)) return;
        if (!config.getBoolean("clan-slots.use-points", true)) return;

        int pointsPerKill = Math.max(0, config.getInt("clan-slots.points-per-kill", 0));
        if (pointsPerKill <= 0) return;

        String killerClan = plugin.getPlayerClan(killer.getName());
        if (killerClan == null || killerClan.isEmpty()) return;

        String victimClan = plugin.getPlayerClan(victim.getName());
        if (victimClan != null && victimClan.equalsIgnoreCase(killerClan)) return;

        plugin.getStorageProvider().addClanPoints(killerClan, pointsPerKill);
        killer.sendMessage(MSG.color(plugin.getLangManager().getMessageWithPrefix("user.slots_points_gained")
            .replace("{points}", String.valueOf(pointsPerKill))));
    }
}
