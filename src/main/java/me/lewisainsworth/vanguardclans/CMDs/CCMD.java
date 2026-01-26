package me.lewisainsworth.vanguardclans.CMDs;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.Econo;
import me.lewisainsworth.vanguardclans.Utils.FileHandler;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import static me.lewisainsworth.vanguardclans.VanguardClan.prefix;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import me.lewisainsworth.vanguardclans.Utils.ClanNameHandler;
import me.lewisainsworth.vanguardclans.Utils.ClanPermission;
import me.lewisainsworth.vanguardclans.Utils.ClanRoleManager;
import me.lewisainsworth.vanguardclans.Utils.TopMetric;
import me.lewisainsworth.vanguardclans.Database.StorageProvider;


import java.util.*;
import java.util.stream.Collectors;
import java.text.DecimalFormat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DriverManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.ChatColor;



import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class CCMD implements CommandExecutor, TabCompleter, Listener {
    private final VanguardClan plugin;
    private final LangManager langManager;
    private List<String> helpLines;
    public Set<UUID> teleportingPlayers = new HashSet<>();
    private final Map<UUID, Long> homeCooldowns = new HashMap<>();
    
    

    public CCMD(VanguardClan plugin, LangManager langManager) {
        this.plugin = plugin;
        this.langManager = langManager;
        this.helpLines = langManager.getMessageList("user.help_lines");
    }

    public void reloadHelpLines() {
        this.helpLines = langManager.getMessageList("user.help_lines");
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MSG.color(langManager.getMessage("user.console_command_only")));
            return true;
        }

        if (plugin.isWorldBlocked(player.getWorld())) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.command_blocked_world")));
            return true;
        }

        String playerName = player.getName();
        String playerClan = this.getPlayerClan(playerName);
        boolean showMainHelpPage = shouldShowMainHelpPage();

        // Comando de ayuda paginada
        if (args.length < 1) {
            if (plugin.getGuiManager() != null) {
                plugin.getGuiManager().openMainMenu(player);
                return true;
            }
            if (!showMainHelpPage) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.command_not_found")));
                return true;
            }
            help(player, 1);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invalid_page_number")));
                    return true;
                }
            }

            // Solo llamamos a tu mActodo help que ya tiene la paginaciA3n y botones
            help(player, page);
            return true;
        }
        // Resto de comandos con permisos individuales
        switch (args[0].toLowerCase()) {
            case "create":
                if (!player.hasPermission("vanguardclans.user.create")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (playerClan != null && !playerClan.isEmpty()) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.already_in_clan")));
                    return true;
                }
                this.create(sender, args);
                break;

            case "disband":
                if (!player.hasPermission("vanguardclans.user.disband")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.disband(sender, playerClan);
                break;

            case "sethome":
                if (!player.hasPermission("vanguardclans.user.sethome")) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (playerClan == null || playerClan.isEmpty()) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                    return true;
                }
                if (!hasClanPermission(player, playerClan, ClanPermission.SETHOME)) {
                    return true;
                }
                plugin.getStorageProvider().setClanHome(playerClan, player.getLocation());
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_set")));
                break;

            case "delhome":
                if (!player.hasPermission("vanguardclans.user.delhome")) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (playerClan == null || playerClan.isEmpty()) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                    return true;
                }
                if (!hasClanPermission(player, playerClan, ClanPermission.DELHOME)) {
                    return true;
                }
                plugin.getStorageProvider().deleteClanHome(playerClan);
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_deleted")));
                break;

            case "home":
                if (!player.hasPermission("vanguardclans.user.home")) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (playerClan == null || playerClan.isEmpty()) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                    return true;
                }
                teleportToClanHome(player, playerClan);
                break;

            case "report":
                if (!player.hasPermission("vanguardclans.user.report")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.usage_report")));
                    return true;
                }
                this.report(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                break;

            case "menu":
            case "gui":
                if (plugin.getGuiManager() != null) {
                    plugin.getGuiManager().openMainMenu(player);
                    break;
                }
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.command_not_found")));
                break;

            case "commands":
                help(player, 1);
                break;

            case "top":
                if (!player.hasPermission("vanguardclans.user.top")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                handleTopCommand(player, args);
                break;

            case "rank":
                if (!player.hasPermission("vanguardclans.user.rank")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                handleRankCommand(player, playerClan, args);
                break;

            case "list":
                if (!player.hasPermission("vanguardclans.user.list")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.list(sender);
                break;

            case "info":
                if (!player.hasPermission("vanguardclans.user.info")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                handleInfoCommand(player, playerClan, args);
                break;

            case "join":
                if (!player.hasPermission("vanguardclans.user.join")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.usage_join")));
                    return true;
                }
                this.joinClan(sender, playerName, args[1]);
                break;

            case "decline":
                if (!player.hasPermission("vanguardclans.user.join")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_decline_usage")));
                    return true;
                }
                this.declineInvite(sender, args);
                break;

            case "leave":
                if (!player.hasPermission("vanguardclans.user.leave")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.leave(sender, playerClan);
                break;

            case "kick":
                if (!player.hasPermission("vanguardclans.user.kick")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.kick(sender, args);
                break;

            case "invite":
                if (!player.hasPermission("vanguardclans.user.invite")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.usage_invite")));
                    return true;
                }
                this.inviteToClan(sender, args[1]);
                break;

            case "chat":
                if (!player.hasPermission("vanguardclans.user.chat")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }

                if (playerClan == null || playerClan.isEmpty()) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                    return true;
                }

                if (args.length >= 2) {
                    // Modo cl??sico: mensaje directo al clan
                    this.chat(playerClan, player, Arrays.copyOfRange(args, 1, args.length));
                } else {
                    // Modo toggle: activ??s o desactiv??s el modo chat clan
                    plugin.toggleClanChat(player);
                    boolean toggled = plugin.isClanChatToggled(player);
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix(
                        toggled ? "user.chat_enabled" : "user.chat_disabled")));
                }
                break;

            case "stats":
                if (!player.hasPermission("vanguardclans.user.stats")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }

                if (args.length >= 2) {
                    String targetClan = args[1];
                    this.stats(sender, targetClan);
                } else {
                    if (playerClan == null || playerClan.isEmpty()) {
                        sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                        return true;
                    }
                    this.stats(sender, playerClan);
                }
                break;

            case "resign":
                if (!player.hasPermission("vanguardclans.user.resign")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.resign(sender, playerClan);
                break;

            case "ff":
                if (!player.hasPermission("vanguardclans.user.ff")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (playerClan == null || playerClan.isEmpty()) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                    return true;
                }
                if (args.length != 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.usage_ff")));
                    return true;
                }
                handleFriendlyFireCommand(sender, playerClan, args);
                break;

            case "ally":
                if (!player.hasPermission("vanguardclans.user.ally")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                if (playerClan == null || playerClan.isEmpty()) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.usage_ally")));
                    return true;
                }
                handleAllyCommand(sender, playerName, playerClan, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "edit":
                if (!player.hasPermission("vanguardclans.user.edit")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.edit(player, playerClan, args);
                break;

            case "economy":
                if (!player.hasPermission("vanguardclans.user.economy")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                handleClanEconomy(player, playerClan, args);
                break;
            
            case "slots":
                if (!player.hasPermission("vanguardclans.user.slots")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                handleSlotsCommand(player, playerClan, args);
                break;

            default:
                if (showMainHelpPage) {
                    this.help(player, 1);
                } else {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.command_not_found")));
                }
                break;
        }

        return true;
    }





    public void help(Player player, int page) {
        int linesPerPage = 5;
        int totalPages = (int) Math.ceil((double) helpLines.size() / linesPerPage);

        if (page < 1 || page > totalPages) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invalid_page_help")
                .replace("{total_pages}", String.valueOf(totalPages))));
            return;
        }

        player.sendMessage(MSG.color(langManager.getMessage("user.help_header")));
        player.sendMessage(MSG.color(langManager.getMessage("user.help_title")
            .replace("{page}", String.valueOf(page))
            .replace("{total_pages}", String.valueOf(totalPages))));
        player.sendMessage(MSG.color(langManager.getMessage("user.help_header")));

        int start = (page - 1) * linesPerPage;
        int end = Math.min(start + linesPerPage, helpLines.size());

        for (int i = start; i < end; i++) {
            player.sendMessage(MSG.color(helpLines.get(i)));
        }

        // Flechas de navegaci??n
        TextComponent nav = new TextComponent();

        if (page > 1) {
            TextComponent prev = new TextComponent(MSG.color(langManager.getMessage("user.help_previous_page")));
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan help " + (page - 1)));
            prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(langManager.getMessage("user.help_click_to_page").replace("{page}", String.valueOf(page - 1)))));
            nav.addExtra(prev);
        }

        if (page < totalPages) {
            TextComponent next = new TextComponent(MSG.color(langManager.getMessage("user.help_next_page")));
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan help " + (page + 1)));
            next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(langManager.getMessage("user.help_click_to_page").replace("{page}", String.valueOf(page + 1)))));
            nav.addExtra(next);
        }

        player.spigot().sendMessage(nav);
        player.sendMessage(MSG.color(langManager.getMessage("user.help_footer")));
    }





    public void kick(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.usage_kick")));
            return;
        }

        String target = args[1];
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.console_command_only")));
            return;
        }

        String clanName = getPlayerClan(player.getName());

        if (clanName == null) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        try {
            if (!hasClanPermission(player, clanName, ClanPermission.KICK)) {
                return;
            }

            if (target.equalsIgnoreCase(player.getName())) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_cant_kick_self")));
                return;
            }

            // Check if target is in the clan
            if (!plugin.getStorageProvider().isPlayerInClan(target, clanName)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_player_not_member")));
                return;
            }

            String clanLeader = plugin.getStorageProvider().getClanLeader(clanName);
            if (clanLeader != null && !clanLeader.equalsIgnoreCase(player.getName())
                && clanLeader.equalsIgnoreCase(target)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_cant_kick_leader")));
                return;
            }

            // Remove player from clan
            plugin.getStorageProvider().removePlayerFromClan(target, clanName);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_success")
                .replace("{player}", target)
                .replace("{clan}", clanName)));

            // Check if clan is now empty and delete it if so
            List<String> clanMembers = plugin.getStorageProvider().getClanMembers(clanName);
            if (clanMembers.isEmpty()) {
                plugin.getStorageProvider().deleteClan(clanName);
                plugin.notifyClanDeleted(clanName);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_clan_deleted")));
            }

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_error")));
        }
    }



    public void resign(CommandSender sender, String playerClan) {
        String playerName = sender.getName();

        if (playerClan == null) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        try {
            // Check if player is the leader
            String clanLeader = plugin.getStorageProvider().getClanLeader(playerClan);
            if (clanLeader == null || !clanLeader.equalsIgnoreCase(playerName)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.resign_not_leader")));
                return;
            }

            // Get all clan members except the current leader
            List<String> clanMembers = plugin.getStorageProvider().getClanMembers(playerClan);
            clanMembers.removeIf(member -> member.equalsIgnoreCase(playerName));

            if (!clanMembers.isEmpty()) {
                // Assign leadership to the first available member
                String newLeader = clanMembers.get(0);
                plugin.getStorageProvider().updateClanLeader(playerClan, newLeader);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.resign_success")
                    .replace("{newLeader}", newLeader)));
            } else {
                // No other members, delete the clan
                plugin.getStorageProvider().deleteClan(playerClan);
                plugin.notifyClanDeleted(playerClan);
                plugin.notifyClanDeleted(playerClan);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.resign_clan_deleted")));
            }

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.resign_error")));
        }
    }



//    public void wars(CommandSender sender, String[] args, String playerClan) {
//        if (args.length < 2) {
//            sender.sendMessage(MSG.color(prefix + "&c &lUSE:&f /clans wars <declare/peace/alliance/accept/deny> <clan>"));
//            return;
//        }
//
//        if (playerClan == null || playerClan.isEmpty()) {
//            sender.sendMessage(MSG.color(prefix + "&c You are not in a clan."));
//            return;
//        }
//
//        Player player = (Player) sender;
//        String playerName = player.getName();
//        FileHandler fh = plugin.getFH();
//        FileConfiguration data = fh.getData();
//        String leader = data.getString("Clans." + playerClan + ".Leader");
//
//        if (!playerName.equalsIgnoreCase(leader)) {
//            sender.sendMessage(MSG.color(prefix + "&c You are not the leader of this clan."));
//            return;
//        }
//
//        String action = args[1].toLowerCase();
//        String otherClan;
//
//        switch (action) {
//            case "peace":
//                if (args.length < 3) {
//                    sender.sendMessage(MSG.color(prefix + "&c You need to specify a clan name to offer peace."));
//                    return;
//                }
//                otherClan = args[2];
//                handlePeaceOffer(sender, playerClan, otherClan, data, fh);
//                break;
//
//            case "declare":
//                if (args.length < 3) {
//                    sender.sendMessage(MSG.color(prefix + "&c You need to specify a clan name to declare war."));
//                    return;
//                }
//                otherClan = args[2];
//                handleWarDeclaration(sender, playerClan, otherClan, data, fh);
//                break;
//
//            case "alliance":
//                if (args.length < 3) {
//                    sender.sendMessage(MSG.color(prefix + "&c You need to specify a clan name to declare an alliance."));
//                    return;
//                }
//                otherClan = args[2];
//                handleAllianceRequest(sender, playerClan, otherClan, data, fh);
//                break;
//
//            case "accept":
//                if (args.length < 3) {
//                    sender.sendMessage(MSG.color(prefix + "&c You need to specify a clan name to accept the alliance."));
//                    return;
//                }
//                otherClan = args[2];
//                handleAllianceAcceptance(sender, playerClan, otherClan, data, fh);
//                break;
//
//            case "deny":
//                if (args.length < 3) {
//                    sender.sendMessage(MSG.color(prefix + "&c You need to specify a clan name to deny the alliance."));
//                    return;
//                }
//                otherClan = args[2];
//                handleAllianceDenial(sender, playerClan, otherClan, data, fh);
//                break;
//
//            default:
//                sender.sendMessage(MSG.color(prefix + "&c Invalid action. Use: <declare/peace/alliance/accept/deny>"));
//                break;
//        }
//    }
//
//    private void handlePeaceOffer(CommandSender sender, String playerClan, String otherClan, FileConfiguration data, FileHandler fh) {
//        if (!data.contains("Clans." + otherClan)) {
//            sender.sendMessage(MSG.color(prefix + "&c The specified clan does not exist."));
//            return;
//        }
//
//        List<String> pending = data.getStringList("Wars." + otherClan + ".Enemy");
//        pending.remove(playerClan);
//        data.set("Wars." + otherClan + ".Enemy", pending);
//        fh.saveData();
//        sender.sendMessage(MSG.color(prefix + "&2You have offered peace to: &e" + otherClan));
//    }
//
//    private void handleWarDeclaration(CommandSender sender, String playerClan, String otherClan, FileConfiguration data, FileHandler fh) {
//        if (!data.contains("Clans." + otherClan)) {
//            sender.sendMessage(MSG.color(prefix + "&c The specified clan does not exist."));
//            return;
//        }
//
//        List<String> pending = data.getStringList("Wars." + otherClan + ".Enemy");
//        pending.add(playerClan);
//        data.set("Wars." + otherClan + ".Enemy", pending);
//
//        removePendingAlliance(playerClan, otherClan, data);
//
//        List<String> playerEnemy = data.getStringList("Wars." + playerClan + ".Enemy");
//        playerEnemy.add(otherClan);
//        data.set("Wars." + playerClan + ".Enemy", playerEnemy);
//
//        fh.saveData();
//        sender.sendMessage(MSG.color(prefix + "&2Your clan has started a war with: &e" + otherClan));
//    }
//
//    private void handleAllianceRequest(CommandSender sender, String playerClan, String otherClan, FileConfiguration data, FileHandler fh) {
//        if (!data.contains("Clans." + otherClan)) {
//            sender.sendMessage(MSG.color(prefix + "&c No clan found with that name."));
//            return;
//        }
//
//        List<String> pending = data.getStringList("Wars." + otherClan + ".Ally.Pending");
//        pending.add(playerClan);
//        data.set("Wars." + otherClan + ".Ally.Pending", pending);
//
//        fh.saveData();
//        sender.sendMessage(MSG.color(prefix + "&2Alliance request sent to: &e" + otherClan));
//    }
//
//    private void handleAllianceAcceptance(CommandSender sender, String playerClan, String otherClan, FileConfiguration data, FileHandler fh) {
//        if (!data.contains("Clans." + otherClan)) {
//            sender.sendMessage(MSG.color(prefix + "&c The specified clan does not exist."));
//            return;
//        }
//
//        List<String> pending = data.getStringList("Wars." + otherClan + ".Ally.Alliance");
//        pending.add(playerClan);
//        data.set("Wars." + otherClan + ".Ally.Alliance", pending);
//
//        pending = data.getStringList("Wars." + otherClan + ".Ally.Pending");
//        pending.remove(playerClan);
//        data.set("Wars." + otherClan + ".Ally.Pending", pending);
//
//        fh.saveData();
//        sender.sendMessage(MSG.color(prefix + "&2Now you are allied with: &e" + otherClan));
//    }
//
//    private void handleAllianceDenial(CommandSender sender, String playerClan, String otherClan, FileConfiguration data, FileHandler fh) {
//        if (!data.contains("Clans." + otherClan)) {
//            sender.sendMessage(MSG.color(prefix + "&c No clan found with that name."));
//            return;
//        }
//
//        List<String> pending = data.getStringList("Wars." + otherClan + ".Ally.Pending");
//        pending.remove(playerClan);
//        data.set("Wars." + otherClan + ".Ally.Pending", pending);
//
//        fh.saveData();
//        sender.sendMessage(MSG.color(prefix + "&2You rejected the alliance request of: &e" + otherClan));
//    }
//
//    private void removePendingAlliance(String playerClan, String otherClan, FileConfiguration data) {
//        List<String> pending = data.getStringList("Wars." + otherClan + ".Ally.Pending");
//        pending.remove(playerClan);
//        data.set("Wars." + otherClan + ".Ally.Pending", pending);
//
//        List<String> allianceOtherClan = data.getStringList("Wars." + otherClan + ".Ally.Alliance");
//        allianceOtherClan.remove(playerClan);
//        data.set("Wars." + otherClan + ".Ally.Alliance", allianceOtherClan);
//    }

    public void stats(CommandSender sender, String clanName) {
        try {
            var sp = plugin.getStorageProvider();
            if (!sp.clanExists(clanName)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.stats_not_found")));
                return;
            }

            String coloredName = sp.getClanColoredName(clanName);
            String founder = sp.getClanFounder(clanName);
            String leader = sp.getClanLeader(clanName);
            String privacy = sp.getClanPrivacy(clanName);

            sender.sendMessage(MSG.color(""));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_border")));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_title").replace("{clan}", coloredName)));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_border")));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_founder").replace("{founder}", founder != null ? founder : "-")));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_leader").replace("{leader}", leader != null ? leader : "-")));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_privacy").replace("{privacy}", privacy != null ? privacy : "public")));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_money").replace("{money}", formatMoney(sp.getClanMoney(clanName)))));

            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_members_title")));

            double totalKD = 0.0;
            int count = 0;

            java.util.List<String> members = sp.getClanMembers(clanName);
            for (String username : members) {
                double kd = sp.getKillDeathRatio(username);
                totalKD += kd;
                count++;
                sender.sendMessage(MSG.color(langManager.getMessage("user.stats_member_line")
                        .replace("{member}", username)
                        .replace("{kd}", String.format("%.2f", kd))));
            }

            double avgKD = count > 0 ? totalKD / count : 0.0;
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_avg_kd").replace("{avgKD}", String.format("%.2f", avgKD))));
            sender.sendMessage(MSG.color(langManager.getMessage("user.stats_footer")));
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.stats_error")));
        }
    }

    private void handleClanEconomy(Player player, String playerClan, String[] args) {
        FileConfiguration config = plugin.getFH().getConfig();
        if (!config.getBoolean("economy.enabled", true)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_disabled")));
            return;
        }

        if (playerClan == null || playerClan.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        if (args.length == 1) {
            StorageProvider storage = plugin.getStorageProvider();
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_balance")
                .replace("{balance}", formatMoney(storage.getClanMoney(playerClan)))));
            return;
        }

        if (args.length != 3) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_usage")));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        boolean deposit = action.equals("deposit") || action.equals("depositar");
        boolean withdraw = action.equals("withdraw") || action.equals("retirar");

        if (!deposit && !withdraw) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_usage")));
            return;
        }

        Double amountParsed = parsePositiveAmount(args[2]);
        if (amountParsed == null) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_invalid_amount")));
            return;
        }
        double amount = amountParsed;

        Econo econ = VanguardClan.getEcon();
        StorageProvider storage = plugin.getStorageProvider();
        double clanBalance = storage.getClanMoney(playerClan);

        if (deposit) {
            if (!hasClanPermission(player, playerClan, ClanPermission.BANK_DEPOSIT)) {
                return;
            }
            if (!econ.has(player, amount)) {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_not_enough_player")));
                return;
            }

            storage.setClanMoney(playerClan, clanBalance + amount);
            econ.withdraw(player, amount);

            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_deposit_success")
                .replace("{amount}", formatMoney(amount))
                .replace("{balance}", formatMoney(storage.getClanMoney(playerClan)))));
            return;
        }

        if (!hasClanPermission(player, playerClan, ClanPermission.BANK_WITHDRAW)) {
            return;
        }

        if (clanBalance < amount) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_not_enough_clan")));
            return;
        }

        storage.setClanMoney(playerClan, clanBalance - amount);
        econ.deposit(player, amount);

        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_withdraw_success")
            .replace("{amount}", formatMoney(amount))
            .replace("{balance}", formatMoney(storage.getClanMoney(playerClan)))));
    }

    private Double parsePositiveAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.matches(".*[A-Za-z].*")) {
            return null;
        }

        normalized = normalized.replace(" ", "");
        normalized = normalized.replace("$", "");
        normalized = normalized.replaceAll("[^0-9,\\.]", "");
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.matches("^\\d{1,3}(,\\d{3})+$")) {
            normalized = normalized.replace(",", "");
        } else if (normalized.matches("^\\d{1,3}(\\.\\d{3})+$")) {
            normalized = normalized.replace(".", "");
        } else if (normalized.contains(",") && !normalized.contains(".")) {
            normalized = normalized.replace(",", ".");
        } else if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(",", "");
        }

        if (!normalized.matches("^[0-9]+(\\.[0-9]+)?$")) {
            return null;
        }

        try {
            double amount = Double.parseDouble(normalized);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void handleSlotsCommand(Player player, String playerClan, String[] args) {
        if (playerClan == null || playerClan.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        FileConfiguration config = plugin.getFH().getConfig();
        boolean slotsEnabled = config.getBoolean("clan-slots.enabled", false);
        boolean usePoints = config.getBoolean("clan-slots.use-points", true);
        int memberCount = plugin.getStorageProvider().getClanMemberCount(playerClan);

        if (!slotsEnabled) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_disabled")));
            return;
        }

        if (!usePoints) {
            int limit = config.getInt("clan-slots.static-limit", 0);
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_static_limit")
                .replace("{used}", String.valueOf(memberCount))
                .replace("{limit}", formatSlotLimit(limit <= 0 ? Integer.MAX_VALUE : limit))));
            return;
        }

        List<SlotUpgrade> upgrades = getConfiguredUpgrades();
        if (upgrades.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_no_upgrades_config")));
            return;
        }

        StorageProvider storage = plugin.getStorageProvider();
        int points = storage.getClanPoints(playerClan);
        int purchased = storage.getClanSlotUpgrades(playerClan);
        int maxSlots = calculateMaxSlots(playerClan);
        SlotUpgrade nextUpgrade = purchased < upgrades.size() ? upgrades.get(purchased) : null;

        if (args.length == 1) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_status")
                .replace("{used}", String.valueOf(memberCount))
                .replace("{limit}", formatSlotLimit(maxSlots))));
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_points")
                .replace("{points}", String.valueOf(points))));

            if (nextUpgrade != null) {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_next_upgrade")
                    .replace("{cost}", String.valueOf(nextUpgrade.cost()))
                    .replace("{slots}", String.valueOf(nextUpgrade.slots()))));
            } else {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_no_more_upgrades")));
            }
            return;
        }

        if (!args[1].equalsIgnoreCase("buy")) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_usage")));
            return;
        }

        if (!hasClanPermission(player, playerClan, ClanPermission.SLOTS_UPGRADE)) {
            return;
        }

        if (nextUpgrade == null) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_no_more_upgrades")));
            return;
        }

        if (points < nextUpgrade.cost()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_not_enough_points")
                .replace("{cost}", String.valueOf(nextUpgrade.cost()))
                .replace("{points}", String.valueOf(points))));
            return;
        }

        storage.setClanPoints(playerClan, points - nextUpgrade.cost());
        storage.setClanSlotUpgrades(playerClan, purchased + 1);
        int newLimit = calculateMaxSlots(playerClan);

        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_bought")
            .replace("{slots}", String.valueOf(nextUpgrade.slots()))
            .replace("{limit}", formatSlotLimit(newLimit))
            .replace("{cost}", String.valueOf(nextUpgrade.cost()))));
    }

    private String formatMoney(double amount) {
        DecimalFormat formatter = new DecimalFormat("#,##0.##");
        return formatter.format(amount);
    }


    private void inviteToClan(CommandSender sender, String playerToInvite) {
        String prefix = VanguardClan.prefix;

        if (!(sender instanceof Player inviter)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.only_players_invite")));
            return;
        }

        String inviterName = inviter.getName();
        String inviterClan = plugin.getStorageProvider().getCachedPlayerClan(inviterName);

        if (inviterClan == null) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        plugin.getStorageProvider().cleanupExpiredInvites();

        // Verificar si el jugador es l??der del clan
        if (!hasClanPermission(inviter, inviterClan, ClanPermission.INVITE)) {
            return;
        }

        if (!hasAvailableSlot(inviterClan)) {
            int used = plugin.getStorageProvider().getClanMemberCount(inviterClan);
            int limit = calculateMaxSlots(inviterClan);
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_full")
                .replace("{used}", String.valueOf(used))
                .replace("{limit}", formatSlotLimit(limit))));
            return;
        }

        if (playerToInvite.equalsIgnoreCase(inviterName)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.cant_invite_self")));
            return;
        }

        Player invitedPlayer = Bukkit.getPlayerExact(playerToInvite);
        if (invitedPlayer == null || !invitedPlayer.isOnline()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.player_not_online")));
            return;
        }

        String invitedPlayerClan = plugin.getStorageProvider().getCachedPlayerClan(playerToInvite);
        if (invitedPlayerClan != null) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.player_in_other_clan")));
            return;
        }

        // Check if player is already invited
        if (plugin.getStorageProvider().isPlayerInvitedToClan(playerToInvite, inviterClan)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_pending")));
            return;
        }

        // Add the invite
        plugin.getStorageProvider().addClanInvite(inviterClan, playerToInvite);

        sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_sent").replace("{player}", playerToInvite)));
        invitedPlayer.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_received")
            .replace("{clan}", inviterClan)));
        invitedPlayer.sendMessage(MSG.color(langManager.getMessage("user.invite_usage")));
    }



    public void chat(String clanName, Player player, String[] message) {
        String playerClan = getPlayerClan(player.getName());
        if (playerClan == null || playerClan.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        String formattedMessage = String.join(" ", message);

        try {
            List<String> clanMembers = plugin.getStorageProvider().getClanMembers(playerClan);
            
            for (String userName : clanMembers) {
                Player recipient = Bukkit.getPlayerExact(userName);
                if (recipient != null && recipient.isOnline()) {
                    recipient.sendMessage(MSG.color(langManager.getMessage("user.chat_format")
                        .replace("{clan}", plugin.getStorageProvider().getColoredClanName(playerClan))
                        .replace("{player}", player.getName())
                        .replace("{message}", formattedMessage)));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.chat_error")));
        }
    }



    private void leave(CommandSender sender, String playerClan) {
        Player player = (Player) sender;
        String playerName = player.getName();

        if (playerClan == null) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        try {
            // Check if player is leader
            String clanLeader = plugin.getStorageProvider().getClanLeader(playerClan);
            boolean isLeader = clanLeader != null && clanLeader.equalsIgnoreCase(playerName);

            // Remove player from clan
            plugin.getStorageProvider().removePlayerFromClan(playerName, playerClan);

            // Get remaining members
            List<String> remaining = plugin.getStorageProvider().getClanMembers(playerClan);

            if (remaining.isEmpty()) {
                // Delete clan if empty
                plugin.getStorageProvider().deleteClan(playerClan);
                plugin.notifyClanDeleted(playerClan);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clan_deleted_empty")));
                return;
            }

            if (isLeader) {
                // Assign new random leader
                String newLeader = remaining.get(new Random().nextInt(remaining.size()));
                plugin.getStorageProvider().updateClanLeader(playerClan, newLeader);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.leader_left").replace("{newLeader}", newLeader)));
            } else {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.left_clan")));
            }

            plugin.getStorageProvider().reloadCache();

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.leave_error")));
        }
    }



    private void joinClan(CommandSender sender, String playerName, String clanToJoin) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.only_players_join")));
            return;
        }

        String currentClan = getPlayerClan(playerName);
        if (currentClan != null) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.already_in_clan")));
            return;
        }

        try {
            // Check if clan exists
            plugin.getStorageProvider().cleanupExpiredInvites();
            if (!plugin.getStorageProvider().clanExists(clanToJoin)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clan_not_exist")));
                return;
            }

            String canonicalClan = resolveClanName(clanToJoin);
            if (canonicalClan == null || canonicalClan.isEmpty()) {
                canonicalClan = clanToJoin;
            }
            String privacy = plugin.getStorageProvider().getClanPrivacy(canonicalClan);
            boolean canJoin = "Public".equalsIgnoreCase(privacy);

            // If clan is private, check for invitation
            if (!canJoin) {
                canJoin = plugin.getStorageProvider().isPlayerInvitedToClan(playerName, canonicalClan);
                
                if (!canJoin) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clan_private")));
                    return;
                }
            }

            if (!hasAvailableSlot(canonicalClan)) {
                int used = plugin.getStorageProvider().getClanMemberCount(canonicalClan);
                int limit = calculateMaxSlots(canonicalClan);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_full")
                    .replace("{used}", String.valueOf(used))
                    .replace("{limit}", formatSlotLimit(limit))));
                return;
            }

            // Add player to clan
            plugin.getStorageProvider().addPlayerToClan(playerName, canonicalClan);
            
            // Remove invitation if exists
            plugin.getStorageProvider().removeClanInvite(canonicalClan, playerName);
            
            // Add to clan history
            PECMD.addClanToHistory(player, canonicalClan);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.joined_clan").replace("{clan}", canonicalClan)));
            plugin.getStorageProvider().reloadCache();

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.join_error")));
        }
    }

    private void declineInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.only_players_decline")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_decline_usage")));
            return;
        }

        String clanInput = args[1];
        String canonicalClan = resolveClanName(clanInput);
        if (canonicalClan == null || canonicalClan.isEmpty()) {
            canonicalClan = clanInput;
        }

        try {
            plugin.getStorageProvider().cleanupExpiredInvites();
            if (!plugin.getStorageProvider().clanExists(canonicalClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clan_not_exist")));
                return;
            }

            if (!plugin.getStorageProvider().isPlayerInvitedToClan(player.getName(), canonicalClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_decline_no_pending")
                    .replace("{clan}", canonicalClan)));
                return;
            }

            plugin.getStorageProvider().removeClanInvite(canonicalClan, player.getName());
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_declined")
                .replace("{clan}", canonicalClan)));
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_decline_error")));
        }
    }



    private String getPlayerClan(String playerName) {
        return plugin.getStorageProvider().getPlayerClan(playerName);
    }


    public void list(CommandSender sender) {
        try {
            Set<String> allClans = plugin.getStorageProvider().getAllClans();
            
            if (allClans.isEmpty()) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clans")));
                return;
            }

            StringBuilder clansList = new StringBuilder();
            clansList.append(MSG.color(langManager.getMessageWithPrefix("user.clans_header") + "\n"));

            for (String clanName : allClans) {
                if (!plugin.isClanBanned(clanName)) {
                    String coloredName = plugin.getStorageProvider().getClanColoredName(clanName);
                    clansList.append(MSG.color("&7- " + coloredName)).append("\n");
                }
            }

            clansList.append(MSG.color(langManager.getMessage("user.clans_footer")));
            sender.sendMessage(clansList.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clans_error")));
        }
    }

    private void handleInfoCommand(Player player, String playerClan, String[] args) {
        String targetClan = args.length >= 2 ? args[1] : playerClan;
        if (targetClan == null || targetClan.trim().isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }
        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openMembersView(player, targetClan);
        }
    }





    private void report(CommandSender sender, String reportedClan, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.report_no_reason")));
            return;
        }

        try {
            // Check if clan exists
            if (!plugin.getStorageProvider().clanExists(reportedClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.report_clan_not_exist")));
                return;
            }

            // Check for duplicate report
            List<Map<String, Object>> existingReports = plugin.getStorageProvider().getClanReports(reportedClan);
            for (Map<String, Object> report : existingReports) {
                if (reason.equals(report.get("reason"))) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.report_already_sent")));
                    return;
                }
            }

            // Add the report
            plugin.getStorageProvider().addClanReport(reportedClan, sender.getName(), reason, System.currentTimeMillis());

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.report_success")
                .replace("{clan}", reportedClan)
                .replace("{reason}", reason)));

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.report_error")));
        }
    }



    private void edit(Player player, String clanName, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_usage")));
            return;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (type.equals("name")) {
            handleEditName(player, clanName, value);
            return;
        }

        if (type.equals("tag")) {
            handleEditTag(player, clanName, value);
            return;
        }

        if (type.equals("privacy")) {
            handleEditPrivacy(player, clanName, value);
            return;
        }

        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_usage")));
    }

    private void handleEditName(Player player, String clanName, String rawName) {
        if (!hasClanPermission(player, clanName, ClanPermission.EDIT_NAME)) {
            return;
        }
        String plainName = ClanNameHandler.getVisibleName(rawName);
        int maxVisibleLength = getMaxClanNameLength();

        if (maxVisibleLength > 0 && plainName.length() > maxVisibleLength) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_too_long")
                .replace("{max}", String.valueOf(maxVisibleLength))));
            return;
        }

        if (plugin.isClanBanned(plainName)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("msg.clan_name_banned")
                .replace("{clan}", plainName)));
            return;
        }

        List<String> blocked = plugin.getFH().getConfig().getStringList("names-blocked.blocked");
        if (blocked.stream().anyMatch(b -> b.equalsIgnoreCase(plainName))) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_blocked")));
            return;
        }

        boolean sameName = clanName != null && clanName.equalsIgnoreCase(plainName);
        if (!sameName && plugin.getStorageProvider().clanExists(plainName)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_exists")));
            return;
        }

        String currentColored = plugin.getStorageProvider().getClanColoredName(clanName);
        String newColored = (currentColored == null || currentColored.isEmpty()) ? rawName : currentColored;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getStorageProvider().updateClanName(clanName, plainName, newColored);
                plugin.notifyClanRenamed(clanName, plainName);
                plugin.getStorageProvider().reloadCache();

                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_success")
                        .replace("{name}", MSG.color(rawName))))
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_error")))
                );
            }
        });
    }

    private void handleEditTag(Player player, String clanName, String rawTag) {
        if (!hasClanPermission(player, clanName, ClanPermission.EDIT_TAG)) {
            return;
        }
        if (clanName == null || clanName.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        String coloredTag = MSG.color(rawTag);
        String visibleName = ClanNameHandler.getVisibleName(coloredTag);
        int maxVisibleLength = getMaxClanNameLength();
        if (maxVisibleLength > 0 && visibleName.length() > maxVisibleLength) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_too_long")
                .replace("{max}", String.valueOf(maxVisibleLength))));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getStorageProvider().setClanColoredName(clanName, coloredTag);
                plugin.getStorageProvider().reloadCache();

                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_tag_success")
                        .replace("{name}", coloredTag)))
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_tag_error")))
                );
            }
        });
    }

    private void handleEditPrivacy(Player player, String clanName, String rawPrivacy) {
        if (!hasClanPermission(player, clanName, ClanPermission.EDIT_PRIVACY)) {
            return;
        }
        if (clanName == null || clanName.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        String normalized = rawPrivacy.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("public") && !normalized.equals("private")) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_privacy_usage")));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getStorageProvider().setClanPrivacy(clanName, normalized);
                plugin.getStorageProvider().reloadCache();

                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_privacy_success")
                        .replace("{privacy}", normalized)))
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_error")))
                );
            }
        });
    }







    public void disband(CommandSender sender, String playerClan) {
        if (playerClan == null || playerClan.isEmpty()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        Player player = (Player) sender;
        if (!hasClanPermission(player, playerClan, ClanPermission.DISBAND)) {
            return;
        }
        Econo econ = VanguardClan.getEcon();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Delete clan using StorageProvider
                plugin.getStorageProvider().deleteClan(playerClan);

                boolean econEnabled = plugin.getFH().getConfig().getBoolean("economy.enabled");
                int deleteGain = plugin.getFH().getConfig().getInt("economy.earn.delete-clan", 0);
                if (econEnabled) econ.deposit(player, deleteGain);

                plugin.getStorageProvider().reloadCache();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (econEnabled) {
                        sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.disband_success_earn")
                            .replace("{money}", String.valueOf(deleteGain))));
                    } else {
                        sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.disband_success")));
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.disband_error")))
                );
            }
        });
    }


    public void create(CommandSender sender, String[] args) {
        if (args.length < 2 || !(sender instanceof Player player)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_usage")));
            return;
        }

        String rawClanName = args[1];
        String plainClanName = ClanNameHandler.getVisibleName(rawClanName);
        String playerName = player.getName();
        final String creatorIp;
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            creatorIp = player.getAddress().getAddress().getHostAddress();
        } else {
            creatorIp = "unknown";
        }
        FileConfiguration config = plugin.getFH().getConfig();
        Econo econ = VanguardClan.getEcon();
        int maxClanNameLength = getMaxClanNameLength();

        if (maxClanNameLength > 0 && plainClanName.length() > maxClanNameLength) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_name_too_long")
                .replace("{max}", String.valueOf(maxClanNameLength))));
            return;
        }

        // Validar nombres bloqueados
        if (config.getStringList("names-blocked.blocked").stream().anyMatch(b -> b.equalsIgnoreCase(plainClanName))) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_name_blocked")));
            return;
        }

        // ??? Verificar si est?? baneado
        if (plugin.isClanBanned(plainClanName)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("msg.clan_name_banned").replace("{clan}", plainClanName)));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Check if clan already exists
                if (plugin.getStorageProvider().clanExists(plainClanName)) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_exists")))
                    );
                    return;
                }

                // L??mite de clanes
                int maxClans = config.getInt("max-clans", 0);
                if (maxClans > 0) {
                    Set<String> allClans = plugin.getStorageProvider().getAllClans();
                    if (allClans.size() >= maxClans) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_limit")
                                        .replace("{max}", String.valueOf(maxClans))))
                        );
                        return;
                    }
                }

                // Anti-multicuentas
                boolean enforceIp = config.getBoolean("anti-multiaccount.enabled", false);
                int perIpLimit = config.getInt("anti-multiaccount.max-clans-per-ip", 1);
                boolean onlyWhenGlobalLimit = config.getBoolean("anti-multiaccount.only-when-global-limit", true);
                boolean shouldEnforceIp = enforceIp && perIpLimit > 0 && (!onlyWhenGlobalLimit || maxClans > 0);
                if (shouldEnforceIp) {
                    int existing = plugin.getIpClanTracker().getClanCountForIp(creatorIp);
                    if (existing >= perIpLimit) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_ip_limit_reached")
                                        .replace("{limit}", String.valueOf(perIpLimit))))
                        );
                        return;
                    }
                }

                // Econom??a
                if (config.getBoolean("economy.enabled")) {
                    int cost = config.getInt("economy.cost.create-clan");
                    if (econ.getBalance(player) < cost) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_no_money")
                                        .replace("{cost}", String.valueOf(cost))))
                        );
                        return;
                    }
                    econ.withdraw(player, cost);
                }

                // Create clan using StorageProvider
                try {
                    String configuredPrivacy = config.getString("clan.default-privacy", "private");
                    String privacy = "public".equalsIgnoreCase(configuredPrivacy) ? "public" : "private";
                    plugin.getStorageProvider().createClan(plainClanName, rawClanName, playerName, playerName, 0.0, privacy);
                    plugin.getIpClanTracker().addClan(creatorIp, plainClanName);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getDiscordNotifier().onClanCreated(plainClanName, playerName, creatorIp);
                        PECMD.addClanToHistory(player, plainClanName);
                        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_success")
                                .replace("{clan}", MSG.color(rawClanName))));
                    });

                } catch (IllegalArgumentException e) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(e.getMessage());
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.create_error")))
                );
            }
        });
    }





    private void handleFriendlyFireCommand(CommandSender sender, String playerClan, String[] args) {
        Player player = (Player) sender;

        if (!hasClanPermission(player, playerClan, ClanPermission.FF)) {
            return;
        }

        if (playerClan == null || playerClan.isEmpty()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        if (args.length != 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ff_usage")));
            return;
        }

        boolean enabled = args[1].equalsIgnoreCase("on");

        try {
            plugin.getStorageProvider().setFriendlyFireEnabled(playerClan, enabled);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ff_status")
                .replace("{status}", enabled ? langManager.getMessage("status.enabled") : langManager.getMessage("status.disabled"))));

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ff_error")));
        }
    }

    private void handleAllyFriendlyFireCommand(CommandSender sender, String playerClan, String[] args) {
         Player player = (Player) sender;

        if (!hasClanPermission(player, playerClan, ClanPermission.ALLY_FF)) {
            return;
        }

        if (playerClan == null || playerClan.isEmpty()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        if (args.length != 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.allyff_usage")));
            return;
        }

        boolean enabled = args[1].equalsIgnoreCase("on");

        try (Connection con = plugin.getStorageProvider().getConnection();
            PreparedStatement stmt = con.prepareStatement("REPLACE INTO friendlyfire_allies (clan, enabled) VALUES (?, ?)")) {

            stmt.setString(1, playerClan);
            stmt.setBoolean(2, enabled);
            stmt.executeUpdate();

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.allyff_status")
                .replace("{status}", enabled ? langManager.getMessage("status.enabled") : langManager.getMessage("status.disabled"))));

        } catch (SQLException e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.allyff_error")));
        }
    }


    private boolean areClansAllied(String clan1, String clan2) {
        if (clan1.equalsIgnoreCase(clan2)) return true;
        
        try {
            return plugin.getStorageProvider().areClansAllied(clan1, clan2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void handleAllyCommand(CommandSender sender, String playerName, String playerClan, String[] args) {
         Player player = (Player) sender;

        if (!hasClanPermission(player, playerClan, ClanPermission.ALLY)) {
            return;
        }
        
        if (playerClan == null || playerClan.isEmpty()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_usage")));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "request":
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_request_usage")));
                    return;
                }
                sendAllyRequest(sender, playerClan, args[1]);
                break;

            case "accept":
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_accept_usage")));
                    return;
                }
                acceptAlly(sender, playerClan, args[1]);
                break;

            case "decline":
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_decline_usage")));
                    return;
                }
                declineAlly(sender, playerClan, args[1]);
                break;

            case "remove":
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_remove_usage")));
                    return;
                }
                removeAlly(sender, playerClan, args[1]);
                break;

            case "ff":
                if (args.length != 2) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_ff_usage")));
                    return;
                }
                handleAllyFriendlyFireCommand(sender, playerClan, args);
                break;

            default:
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_invalid_subcommand")));
        }
    }

    private void setHome(Player player, String clan) {
        if (!hasClanPermission(player, clan, ClanPermission.SETHOME)) {
            return;
        }

        plugin.getStorageProvider().setClanHome(clan, player.getLocation());
        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_set")));
    }

    private void teleportToClanHome(Player player, String clan) {
        UUID uuid = player.getUniqueId();

        if (plugin.teleportingPlayers.contains(uuid)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.on_teport")));
            return;
        }

        boolean bypassCooldown = player.hasPermission("vanguardclans.bypass.homecooldown");
        boolean bypassDelay = player.hasPermission("vanguardclans.bypass.homedelay");

        if (!bypassCooldown) {
            long lastUsed = plugin.homeCooldowns.getOrDefault(uuid, 0L);
            long timeLeft = ((lastUsed + plugin.clanHomeCooldown * 1000L) - System.currentTimeMillis()) / 1000;

            if (timeLeft > 0) {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_cooldown")
                        .replace("{seconds}", String.valueOf(timeLeft))));
                return;
            }

            plugin.homeCooldowns.put(uuid, System.currentTimeMillis());
        }

        if (bypassDelay) {
            // Teletransporta inmediato sin delay
            Location home = plugin.getStorageProvider().getClanHome(clan);
            if (home != null) {
                player.teleport(home);
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_teleported")));
            } else {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_not_set")));
            }
        } else {
            // Teletransporta con delay y cancelaci??n por movimiento
            plugin.teleportingPlayers.add(uuid);
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.teleporting_home")
                    .replace("{seconds}", String.valueOf(plugin.clanHomeDelay))));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.teleportingPlayers.contains(uuid)) {
                    // Cancelado por movimiento
                    return;
                }

                Location home = plugin.getStorageProvider().getClanHome(clan);
                if (home != null) {
                    player.teleport(home);
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_teleported")));
                } else {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.home_not_set")));
                }

                plugin.teleportingPlayers.remove(uuid);
            }, plugin.clanHomeDelay * 20L);
        }
    }





    private void handleTopCommand(Player player, String[] args) {
        if (args.length < 2) {
            if (plugin.getGuiManager() != null) {
                plugin.getGuiManager().openTopSelect(player);
                return;
            }
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.top_usage")));
            return;
        }

        TopMetric metric = TopMetric.fromKey(args[1]).orElse(null);
        if (metric == null) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.top_invalid_metric")));
            return;
        }

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invalid_page_number")));
                return;
            }
        }

        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openTopList(player, metric, page);
            return;
        }

        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.top_usage")));
    }

    private void handleRankCommand(Player player, String clanName, String[] args) {
        if (clanName == null || clanName.isEmpty()) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        if (!hasClanPermission(player, clanName, ClanPermission.RANK_MANAGE)) {
            return;
        }

        if (args.length < 2) {
            if (plugin.getGuiManager() != null) {
                plugin.getGuiManager().openRolesMenu(player);
                return;
            }
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage")));
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                if (args.length < 3) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage_create")));
                    return;
                }
                String newRole = normalizeRoleName(args[2]);
                if (isReservedRole(newRole)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_cant_edit_role")));
                    return;
                }
                if (roleExists(clanName, newRole)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_exists")));
                    return;
                }
                plugin.getRoleManager().createRole(clanName, newRole);
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_created")
                    .replace("{role}", formatRoleName(newRole))));
                return;
            case "delete":
                if (args.length < 3) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage_delete")));
                    return;
                }
                String deleteRole = normalizeRoleName(args[2]);
                if (isReservedRole(deleteRole)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_cant_edit_role")));
                    return;
                }
                if (!roleExists(clanName, deleteRole)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_not_found")));
                    return;
                }
                List<String> members = plugin.getStorageProvider().getClanMembers(clanName);
                for (String member : members) {
                    String currentRole = plugin.getRoleManager().getPlayerRole(clanName, member);
                    if (deleteRole.equalsIgnoreCase(currentRole)) {
                        plugin.getRoleManager().setPlayerRole(clanName, member, ClanRoleManager.ROLE_MEMBER);
                    }
                }
                plugin.getRoleManager().deleteRole(clanName, deleteRole);
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_deleted")
                    .replace("{role}", formatRoleName(deleteRole))));
                return;
            case "set":
                if (args.length < 4) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage_set")));
                    return;
                }
                String target = args[2];
                if (!plugin.getStorageProvider().isPlayerInClan(target, clanName)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_target_not_in_clan")));
                    return;
                }
                String newRoleName = normalizeRoleName(args[3]);
                if (ClanRoleManager.ROLE_LEADER.equalsIgnoreCase(newRoleName)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_cant_edit_leader")));
                    return;
                }
                if (!roleExists(clanName, newRoleName)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_not_found")));
                    return;
                }
                String leader = plugin.getStorageProvider().getClanLeader(clanName);
                if (leader != null && leader.equalsIgnoreCase(target)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_cant_edit_leader")));
                    return;
                }
                plugin.getRoleManager().setPlayerRole(clanName, target, newRoleName);
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_set")
                    .replace("{player}", target)
                    .replace("{role}", formatRoleName(newRoleName))));
                return;
            case "perms":
                if (args.length < 4) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage_perms")));
                    return;
                }
                String role = normalizeRoleName(args[2]);
                if (isReservedRole(role)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_cant_edit_role")));
                    return;
                }
                if (!roleExists(clanName, role)) {
                    player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_not_found")));
                    return;
                }
                String action = args[3].toLowerCase(Locale.ROOT);
                Map<String, Set<ClanPermission>> rolePerms = plugin.getRoleManager().getClanRolePermissions(clanName);
                Set<ClanPermission> perms = new HashSet<>(rolePerms.getOrDefault(role.toLowerCase(Locale.ROOT), Collections.<ClanPermission>emptySet()));
                switch (action) {
                    case "list":
                        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_permissions_list")
                            .replace("{role}", formatRoleName(role))
                            .replace("{perms}", formatPermissionList(perms))));
                        return;
                    case "clear":
                        plugin.getRoleManager().setRolePermissions(clanName, role, Collections.<ClanPermission>emptySet());
                        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_permissions_cleared")
                            .replace("{role}", formatRoleName(role))));
                        return;
                    case "add":
                    case "remove":
                        if (args.length < 5) {
                            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage_perms")));
                            return;
                        }
                        ClanPermission permission = ClanPermission.fromKey(args[4]).orElse(null);
                        if (permission == null) {
                            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_invalid_permission")));
                            return;
                        }
                        boolean changed;
                        if (action.equals("add")) {
                            changed = perms.add(permission);
                        } else {
                            changed = perms.remove(permission);
                        }
                        if (changed) {
                            plugin.getRoleManager().setRolePermissions(clanName, role, perms);
                            String messageKey = action.equals("add")
                                ? "user.rank_permission_added"
                                : "user.rank_permission_removed";
                            player.sendMessage(MSG.color(langManager.getMessageWithPrefix(messageKey)
                                .replace("{role}", formatRoleName(role))
                                .replace("{permission}", permission.getKey())));
                        }
                        return;
                    default:
                        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage_perms")));
                        return;
                }
            case "list":
                Map<String, Set<ClanPermission>> allRoles = plugin.getRoleManager().getClanRolePermissions(clanName);
                Set<String> roleNames = new HashSet<>(allRoles.keySet());
                roleNames.add(ClanRoleManager.ROLE_MEMBER);
                roleNames.add(ClanRoleManager.ROLE_CO_LEADER);
                List<String> roleList = new ArrayList<>(roleNames);
                Collections.sort(roleList);
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_role_list")
                    .replace("{roles}", String.join(", ", roleList))));
                return;
            default:
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.rank_usage")));
                return;
        }
    }


    // ------------------------------------
    // M??todos auxiliares:


    private void deleteEntireClanData(Connection con, String clan) throws SQLException {
        try (PreparedStatement ps1 = con.prepareStatement("DELETE FROM clan_users WHERE clan=?");
            PreparedStatement ps2 = con.prepareStatement("DELETE FROM clans WHERE name=?");
            PreparedStatement ps3 = con.prepareStatement("DELETE FROM reports WHERE clan=?");
            PreparedStatement ps4 = con.prepareStatement("DELETE FROM alliances WHERE clan1=? OR clan2=?");
            PreparedStatement ps5 = con.prepareStatement("DELETE FROM friendlyfire WHERE clan=?");
            PreparedStatement ps6 = con.prepareStatement("DELETE FROM clan_invites WHERE clan=?");
            PreparedStatement ps7 = con.prepareStatement("DELETE FROM pending_alliances WHERE clan1=? OR clan2=?");
            PreparedStatement ps8 = con.prepareStatement("DELETE FROM friendlyfire_allies WHERE clan=?");
            PreparedStatement ps9 = con.prepareStatement("UPDATE player_clan_history SET current_clan = NULL WHERE current_clan = ?")) {

            ps1.setString(1, clan); ps1.executeUpdate();
            ps2.setString(1, clan); ps2.executeUpdate();
            ps3.setString(1, clan); ps3.executeUpdate();
            ps4.setString(1, clan); ps4.setString(2, clan); ps4.executeUpdate();
            ps5.setString(1, clan); ps5.executeUpdate();
            ps6.setString(1, clan); ps6.executeUpdate();
            ps7.setString(1, clan); ps7.setString(2, clan); ps7.executeUpdate();
            ps8.setString(1, clan); ps8.executeUpdate();
            ps9.setString(1, clan); ps9.executeUpdate();
        }
    }


    private void sendAllyRequest(CommandSender sender, String playerClan, String targetClan) {
        if (targetClan.equalsIgnoreCase(playerClan)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_same_clan")));
            return;
        }

        try {
            if (!plugin.getStorageProvider().clanExists(targetClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_target_not_exist").replace("{target}", targetClan)));
                return;
            }

            List<String> pendingAlliances = plugin.getStorageProvider().getPendingAlliances(targetClan);
            if (pendingAlliances.contains(playerClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_already_requested")));
                return;
            }

            plugin.getStorageProvider().addPendingAlliance(playerClan, targetClan);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_request_sent").replace("{target}", targetClan)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                String pClan = this.getPlayerClan(p.getName());
                if (pClan != null && pClan.equalsIgnoreCase(targetClan)) {
                    p.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_request_received").replace("{clan}", playerClan)));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_request_error")));
        }
    }


    private void acceptAlly(CommandSender sender, String playerClan, String requesterClan) {
        if (requesterClan.equalsIgnoreCase(playerClan)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_accept_same_clan")));
            return;
        }

        try {
            List<String> pendingAlliances = plugin.getStorageProvider().getPendingAlliances(playerClan);
            if (!pendingAlliances.contains(requesterClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_accept_no_pending")));
                return;
            }

            plugin.getStorageProvider().createAlliance(requesterClan, playerClan, false);
            plugin.getStorageProvider().removePendingAlliance(requesterClan, playerClan);
            plugin.getStorageProvider().reloadCache();

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_accept_success").replace("{requester}", requesterClan)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                String pClan = this.getPlayerClan(p.getName());
                if (pClan != null && pClan.equalsIgnoreCase(requesterClan)) {
                    p.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_accepted_notify").replace("{clan}", playerClan)));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_accept_error")));
        }
    }


    private void declineAlly(CommandSender sender, String playerClan, String requesterClan) {
        if (requesterClan.equalsIgnoreCase(playerClan)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_decline_same_clan")));
            return;
        }

        try {
            List<String> pendingAlliances = plugin.getStorageProvider().getPendingAlliances(playerClan);
            if (!pendingAlliances.contains(requesterClan)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_decline_no_pending")));
                return;
            }

            plugin.getStorageProvider().removePendingAlliance(requesterClan, playerClan);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_decline_success").replace("{requester}", requesterClan)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                String pClan = this.getPlayerClan(p.getName());
                if (pClan != null && pClan.equalsIgnoreCase(requesterClan)) {
                    p.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_declined_notify").replace("{clan}", playerClan)));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_decline_error")));
        }
    }


    private void removeAlly(CommandSender sender, String playerClan, String targetClan) {
        if (targetClan.equalsIgnoreCase(playerClan)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_remove_same_clan")));
            return;
        }

        try {
            List<String> alliances = plugin.getStorageProvider().getClanAlliances(playerClan);
            if (alliances.contains(targetClan)) {
                plugin.getStorageProvider().removeAlliance(playerClan, targetClan);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_remove_success").replace("{target}", targetClan)));
            } else {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_remove_none")));
            }

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.ally_remove_error")));
        }
    }


    private void handleAllyFriendlyFireCommand(CommandSender sender, String playerClan, String value) {
         Player player = (Player) sender;

        if (!hasClanPermission(player, playerClan, ClanPermission.ALLY_FF)) {
            return;
        }

        if (!value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.allyff_usage")));
            return;
        }

        boolean ffEnabled = value.equalsIgnoreCase("on");

        try (Connection con = plugin.getStorageProvider().getConnection();
            PreparedStatement stmt = con.prepareStatement(
                "REPLACE INTO friendlyfire_allies (clan, enabled) VALUES (?, ?)"
            )) {

            stmt.setString(1, playerClan);
            stmt.setBoolean(2, ffEnabled);
            stmt.executeUpdate();

            String status = langManager.getMessageWithPrefix(ffEnabled ? "status.enabled" : "status.disabled");
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.allyff_status").replace("{status}", status)));

        } catch (SQLException e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("command.allyff_error")));
        }
    }



    private boolean shouldShowMainHelpPage() {
        return plugin.getFH().getConfig().getBoolean("commands.show-main-help-page", true);
    }

    private int getMaxClanNameLength() {
        return plugin.getFH().getConfig().getInt("clan-name.max-length", ClanNameHandler.DEFAULT_MAX_VISIBLE_LENGTH);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return args.length == 1 ? List.of("reload") : new ArrayList<>();
        }

        String playerClan = VanguardClan.getInstance().getStorageProvider().getCachedPlayerClan(player.getName());
        List<String> completions = new ArrayList<>();

        switch (args.length) {
            case 1 -> completions.addAll(List.of(
                    "create", "disband", "report", "list", "info", "join",
                    "kick", "invite", "chat", "leave", "stats", "resign", "edit",
                    "ff", "ally", "help", "home", "sethome", "delhome", "economy", "slots",
                    "top", "rank", "menu", "gui", "commands"
            ));

            case 2 -> {
                String arg0 = args[0].toLowerCase();
                switch (arg0) {
                    case "join" -> {
                        if (isNotInClan(playerClan)) completions.addAll(VanguardClan.getInstance().getStorageProvider().getCachedClanNames());
                    }
                    case "invite" -> {
                        if (isInClan(playerClan) && hasClanPermissionSilent(player, playerClan, ClanPermission.INVITE)) {
                            completions.addAll(getOnlinePlayerNames());
                        }
                    }
                    case "kick" -> {
                        if (isInClan(playerClan) && hasClanPermissionSilent(player, playerClan, ClanPermission.KICK)) {
                            completions.addAll(getOnlinePlayerNames());
                        }
                    }
                    case "economy" -> completions.addAll(List.of("deposit", "withdraw"));
                    case "report", "allyremove" -> completions.addAll(VanguardClan.getInstance().getStorageProvider().getCachedClanNames());
                    case "edit" -> {
                        if (isInClan(playerClan) && (
                            hasClanPermissionSilent(player, playerClan, ClanPermission.EDIT_NAME)
                                || hasClanPermissionSilent(player, playerClan, ClanPermission.EDIT_TAG)
                                || hasClanPermissionSilent(player, playerClan, ClanPermission.EDIT_PRIVACY)
                        )) {
                            completions.addAll(List.of("name", "tag", "privacy"));
                        }
                    }
                    case "ff" -> {
                        completions.addAll(List.of("on", "off"));
                    }
                    case "ally" -> {
                        completions.addAll(List.of("request", "accept", "decline", "remove", "ff"));
                    }
                    case "slots" -> completions.add("buy");
                    case "top" -> completions.addAll(List.of("kda", "points", "money", "members"));
                    case "rank" -> completions.addAll(List.of("create", "delete", "set", "perms", "list"));
                }
            }

            case 3 -> {
                String arg0 = args[0].toLowerCase();
                String arg1 = args[1].toLowerCase();

                if (arg0.equals("ally")) {
                    if (List.of("request", "accept", "decline", "remove").contains(arg1)) {
                        completions.addAll(VanguardClan.getInstance().getStorageProvider().getCachedClanNames());
                    } else if (arg1.equals("ff")) {
                        completions.addAll(List.of("on", "off"));
                    }
                } else if (arg0.equals("rank")) {
                    if (arg1.equals("set")) {
                        completions.addAll(getClanMemberNames(playerClan));
                    } else if (arg1.equals("delete") || arg1.equals("perms")) {
                        completions.addAll(getRoleNames(playerClan));
                    }
                }
            }

            case 4 -> {
                String arg0 = args[0].toLowerCase();
                String arg1 = args[1].toLowerCase();
                if (arg0.equals("rank")) {
                    if (arg1.equals("set")) {
                        completions.addAll(getRoleNames(playerClan));
                    } else if (arg1.equals("perms")) {
                        completions.addAll(List.of("list", "add", "remove", "clear"));
                    }
                }
            }

            case 5 -> {
                String arg0 = args[0].toLowerCase();
                String arg1 = args[1].toLowerCase();
                String arg3 = args[3].toLowerCase();
                if (arg0.equals("rank") && arg1.equals("perms") && (arg3.equals("add") || arg3.equals("remove"))) {
                    for (ClanPermission permission : ClanPermission.values()) {
                        completions.add(permission.getKey());
                    }
                }
            }
        }

        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private boolean isInClan(String clan) {
        return clan != null && !clan.isEmpty();
    }

    private boolean isNotInClan(String clan) {
        return !isInClan(clan);
    }

    private boolean isLeader(Player player, String clanName) {
        if (clanName == null || clanName.isEmpty()) {
            return false;
        }

        if (plugin.getRoleManager() != null && plugin.getRoleManager().isCoLeader(player, clanName)) {
            return true;
        }
        
        // Try to get the leader using StorageProvider
        String leader = plugin.getStorageProvider().getClanLeader(clanName);
        if (leader != null) {
            return player.getName().equalsIgnoreCase(leader);
        }
        
        // If not found, try with stripped color codes
        String plainName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', clanName));
        if (!plainName.equals(clanName)) {
            leader = plugin.getStorageProvider().getClanLeader(plainName);
            if (leader != null) {
                return player.getName().equalsIgnoreCase(leader);
            }
        }
        
        return false;
    }

    private boolean hasAvailableSlot(String clanName) {
        int maxSlots = calculateMaxSlots(clanName);
        if (maxSlots == Integer.MAX_VALUE) {
            return true;
        }
        if (maxSlots <= 0) {
            return false;
        }
        int current = plugin.getStorageProvider().getClanMemberCount(clanName);
        return current < maxSlots;
    }

    private int calculateMaxSlots(String clanName) {
        FileConfiguration config = plugin.getFH().getConfig();
        if (!config.getBoolean("clan-slots.enabled", false)) {
            return Integer.MAX_VALUE;
        }

        if (!config.getBoolean("clan-slots.use-points", true)) {
            int staticLimit = config.getInt("clan-slots.static-limit", 0);
            return staticLimit <= 0 ? Integer.MAX_VALUE : staticLimit;
        }

        int baseSlots = config.getInt("clan-slots.base-slots", 0);
        int upgradesPurchased = clanName == null ? 0 : plugin.getStorageProvider().getClanSlotUpgrades(clanName);
        List<SlotUpgrade> upgrades = getConfiguredUpgrades();

        int extraSlots = 0;
        for (int i = 0; i < upgradesPurchased && i < upgrades.size(); i++) {
            extraSlots += upgrades.get(i).slots();
        }
        return baseSlots + extraSlots;
    }

    private String resolveClanName(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return clanName;
        }
        for (String stored : plugin.getStorageProvider().getCachedClanNames()) {
            if (stored.equalsIgnoreCase(clanName)) {
                return stored;
            }
        }
        return clanName;
    }

    private List<SlotUpgrade> getConfiguredUpgrades() {
        List<SlotUpgrade> upgrades = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getFH().getConfig().getMapList("clan-slots.upgrades")) {
            Object costObj = entry.get("cost");
            Object slotsObj = entry.get("slots");
            int cost = costObj instanceof Number ? ((Number) costObj).intValue() : 0;
            int slots = slotsObj instanceof Number ? ((Number) slotsObj).intValue() : 0;
            if (cost > 0 && slots > 0) {
                upgrades.add(new SlotUpgrade(cost, slots));
            }
        }
        return upgrades;
    }

    private String formatSlotLimit(int limit) {
        if (limit == Integer.MAX_VALUE) {
            return langManager.getMessage("user.slots_unlimited");
        }
        return String.valueOf(limit);
    }

    private record SlotUpgrade(int cost, int slots) {}

    private List<String> getClanNames() {
        return new ArrayList<>(plugin.getStorageProvider().getAllClans());
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> getClanMemberNames(String clanName) {
        if (clanName == null || clanName.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(plugin.getStorageProvider().getClanMembers(clanName));
    }

    private List<String> getRoleNames(String clanName) {
        if (clanName == null || clanName.isEmpty() || plugin.getRoleManager() == null) {
            return Collections.emptyList();
        }
        Map<String, Set<ClanPermission>> roles = plugin.getRoleManager().getClanRolePermissions(clanName);
        Set<String> names = new HashSet<>(roles.keySet());
        names.add(ClanRoleManager.ROLE_MEMBER);
        names.add(ClanRoleManager.ROLE_CO_LEADER);
        return new ArrayList<>(names);
    }

    private boolean hasClanPermissionSilent(Player player, String clanName, ClanPermission permission) {
        if (player == null || clanName == null || clanName.isEmpty() || permission == null) {
            return false;
        }
        if (plugin.getRoleManager() != null) {
            return plugin.getRoleManager().hasPermission(player, clanName, permission);
        }
        return isLeader(player, clanName);
    }

    private String normalizeRoleName(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ClanRoleManager.ROLE_MEMBER;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT)
            .replace("_", "-");
        if (normalized.equals("coleader") || normalized.equals("co-leader") || normalized.equals("colider") || normalized.equals("co-lider")) {
            return ClanRoleManager.ROLE_CO_LEADER;
        }
        return normalized;
    }

    private boolean isReservedRole(String role) {
        if (role == null) {
            return false;
        }
        return ClanRoleManager.ROLE_LEADER.equalsIgnoreCase(role)
            || ClanRoleManager.ROLE_CO_LEADER.equalsIgnoreCase(role);
    }

    private boolean roleExists(String clanName, String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }
        if (ClanRoleManager.ROLE_MEMBER.equalsIgnoreCase(role) || ClanRoleManager.ROLE_CO_LEADER.equalsIgnoreCase(role)) {
            return true;
        }
        if (plugin.getRoleManager() == null) {
            return false;
        }
        Map<String, Set<ClanPermission>> roles = plugin.getRoleManager().getClanRolePermissions(clanName);
        return roles.containsKey(role.toLowerCase(Locale.ROOT));
    }

    private String formatRoleName(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ClanRoleManager.ROLE_MEMBER;
        }
        String[] parts = role.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
            builder.append(" ");
        }
        return builder.toString().trim();
    }

    private String formatPermissionList(Set<ClanPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return langManager.getMessage("user.rank_permissions_none");
        }
        return permissions.stream()
            .map(ClanPermission::getKey)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private boolean hasClanPermission(Player player, String clanName, ClanPermission permission) {
        if (player == null || clanName == null || clanName.isEmpty() || permission == null) {
            return false;
        }
        if (plugin.getRoleManager() != null && plugin.getRoleManager().hasPermission(player, clanName, permission)) {
            return true;
        }
        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.role_no_permission")));
        return false;
    }
}

