package me.lewisainsworth.vanguardclans.Utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import me.lewisainsworth.vanguardclans.VanguardClan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;


import java.util.List;

public class PAPI extends PlaceholderExpansion {

    private final VanguardClan plugin;
    private final FileConfiguration data;

    public PAPI(VanguardClan plugin) {
        this.plugin = plugin;
        this.data = plugin.getFH().getData();
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vanguardclans";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "N/A";

        Econo econ = plugin.getEcon();
        String clanName = getPlayerClan(player.getName());
        if (clanName == null) return "N/A";

        switch (identifier.toLowerCase()) {
            case "prefix":
                return VanguardClan.prefix;

            case "player_money":
                return String.valueOf(econ.getBalance(player));

            case "clan_leader":
                String leader = plugin.getStorageProvider().getClanLeader(clanName);
                return leader != null ? leader : "N/A";

            case "clan_founder":
                String founder = plugin.getStorageProvider().getClanFounder(clanName);
                return founder != null ? founder : "N/A";

            case "clan_name":
                return clanName;

            case "clan_tag":
                String coloredName = plugin.getStorageProvider().getClanColoredName(clanName);
                return MSG.color(coloredName != null ? coloredName : "N/A");

            case "clan_money":
                double money = plugin.getStorageProvider().getClanMoney(clanName);
                return String.valueOf(money);

            case "clan_membercount":
                int memberCount = plugin.getStorageProvider().getClanMemberCount(clanName);
                return String.valueOf(memberCount);

            case "clan_membercount_online": {
                List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
                int onlineCount = 0;

                for (Player p : onlinePlayers) {
                    String pClan = getPlayerClan(p.getName());
                    if (clanName.equalsIgnoreCase(pClan)) onlineCount++;
                }
                return String.valueOf(onlineCount);
            }

            case "clan_membercount_offline": {
                int total = plugin.getStorageProvider().getClanMemberCount(clanName);
                int onlineCount = 0;
                List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
                for (Player p : onlinePlayers) {
                    String pClan = getPlayerClan(p.getName());
                    if (clanName.equalsIgnoreCase(pClan)) onlineCount++;
                }
                return String.valueOf(total - onlineCount);
            }

            default:
                return "&c&lVanguard&6&lClans";
        }
    }




    private String getPlayerClan(String playerName) {
        if (playerName == null) return null;
        return plugin.getStorageProvider().getPlayerClan(playerName);
    }


    public void registerPlaceholders() {
        if (!register()) {
            plugin.getLogger().warning("Failed to register VanguardClans placeholders.");
        } else {
            plugin.getLogger().info("VanguardClans placeholders registered!");
        }
    }
}

