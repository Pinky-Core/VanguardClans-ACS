package me.lewisainsworth.vanguardclans.Events;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;




import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.Econo;
import me.lewisainsworth.vanguardclans.Utils.FileHandler;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import me.lewisainsworth.vanguardclans.Utils.ClanUtils;
import me.lewisainsworth.vanguardclans.Utils.ClanUtils;
import me.lewisainsworth.vanguardclans.CMDs.CCMD;

import static me.lewisainsworth.vanguardclans.VanguardClan.prefix;

import java.sql.*;
import java.util.*;

public class Events implements Listener {
    private final VanguardClan plugin;
    private final CCMD ccCmd;

    public Events(VanguardClan plugin, CCMD ccCmd) {
        this.plugin = plugin;
        this.ccCmd = ccCmd;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileHandler fh = plugin.getFH();
        FileConfiguration config = fh.getConfig();

        if (!config.getBoolean("welcome-message.enabled")) return;

        String clan = plugin.getPlayerClan(player.getName());

        if (clan == null) {
            config.getStringList("welcome-message.no-clan").forEach(
                msg -> player.sendMessage(MSG.color(msg))
            );
        } else {
            List<String> clanUsers = getClanUsers(clan);

            // Mensajes para el jugador que se unió
            for (String line : config.getStringList("welcome-message.self-clan")) {
                player.sendMessage(MSG.color(player, line));
            }

            // Mensajes para los demás miembros del clan
            for (String u : clanUsers) {
                if (!u.equalsIgnoreCase(player.getName())) {
                    Player target = Bukkit.getPlayerExact(u);
                    if (target != null && target.isOnline()) {
                        for (String line : config.getStringList("welcome-message.to-clan")) {
                            target.sendMessage(MSG.color(player, line));
                        }
                    }
                }
            }
        }

        List<String> invites = getInvites(player.getName());
        if (!invites.isEmpty()) {
            player.sendMessage(MSG.color(prefix + " &eFuiste invitado a un clan:"));
            invites.forEach(c -> player.sendMessage(MSG.color("&7- &a" + c + " &7(/clan join " + c + ")")));
        }

        if (plugin.getNameTagManager() != null) {
            plugin.getNameTagManager().applyToPlayer(player);
        }
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        FileHandler fh = plugin.getFH();
        if (!fh.getConfig().getBoolean("economy.enabled")) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            int killReward = fh.getConfig().getInt("economy.earn.kill-enemy");
            VanguardClan.econ.deposit(killer, killReward);
            killer.sendMessage(MSG.color(prefix + "&2 Ganaste: &e&l" + killReward));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.teleportingPlayers.contains(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            plugin.teleportingPlayers.remove(uuid);
            player.sendMessage(MSG.color(plugin.langManager.getMessageWithPrefix("user.teleport_cancelled")));
        }
    }

    @EventHandler
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player damager)) return;

        String clanVictim = plugin.getPlayerClan(victim.getName());
        String clanDamager = plugin.getPlayerClan(damager.getName());


        if (clanVictim == null || clanDamager == null) return;

        if (clanVictim.equals(clanDamager)) {
            // Fuego amigo dentro del mismo clan
            if (!isFriendlyFireEnabled(clanVictim)) {
                event.setCancelled(true);
            }
        } else if (areClansAllied(clanVictim, clanDamager)) {
            // Fuego amigo entre clanes aliados
            if (!ClanUtils.isFriendlyFireEnabledAllies(clanVictim) || !ClanUtils.isFriendlyFireEnabledAllies(clanDamager)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getNameTagManager() != null) {
            plugin.getNameTagManager().handleQuit(event.getPlayer());
        }
    }



    private boolean areClansAllied(String clan1, String clan2) {
        return plugin.getStorageProvider().areClansAllied(clan1, clan2);
    }

    private List<String> getClanUsers(String clan) {
        return plugin.getStorageProvider().getClanMembers(clan);
    }

    private List<String> getInvites(String playerName) {
        return plugin.getStorageProvider().getPlayerInvites(playerName);
    }

    private boolean isFriendlyFireEnabled(String clan) {
        return plugin.getStorageProvider().isFriendlyFireEnabled(clan);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (plugin.isClanChatToggled(player)) {
            event.setCancelled(true);
            String clanName = plugin.getPlayerClan(player.getName());
            if (clanName != null && !clanName.isEmpty()) {
                ccCmd.chat(clanName, player, event.getMessage().split(" "));
            } else {
                player.sendMessage(MSG.color(plugin.getLangManager().getMessageWithPrefix("user.no_clan")));
            }
        }
    }
}
