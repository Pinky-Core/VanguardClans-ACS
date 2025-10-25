package me.lewisainsworth.vanguardclans.listeners;

import me.lewisainsworth.vanguardclans.VanguardClan;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        }
    }

    private void incrementKills(String playerName) {
        plugin.getStorageProvider().incrementPlayerKills(playerName);
    }

    private void incrementDeaths(String playerName) {
        plugin.getStorageProvider().incrementPlayerDeaths(playerName);
    }
}
