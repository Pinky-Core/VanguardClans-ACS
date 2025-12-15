package me.lewisainsworth.vanguardclans.CMDs;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.Econo;
import me.lewisainsworth.vanguardclans.Utils.FileHandler;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import static me.lewisainsworth.vanguardclans.VanguardClan.prefix;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import me.lewisainsworth.vanguardclans.Utils.ClanNameHandler;
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

            case "list":
                if (!player.hasPermission("vanguardclans.user.list")) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_permission")));
                    return true;
                }
                this.list(sender);
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
                    // Modo clásico: mensaje directo al clan
                    this.chat(playerClan, player, Arrays.copyOfRange(args, 1, args.length));
                } else {
                    // Modo toggle: activás o desactivás el modo chat clan
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

        // Flechas de navegación
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
            // Check if player is the leader
            String clanLeader = plugin.getStorageProvider().getClanLeader(clanName);
            if (clanLeader == null || !clanLeader.equalsIgnoreCase(player.getName())) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_only_leader")));
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

            // Remove player from clan
            plugin.getStorageProvider().removePlayerFromClan(target, clanName);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.kick_success")
                .replace("{player}", target)
                .replace("{clan}", clanName)));

            // Check if clan is now empty and delete it if so
            List<String> clanMembers = plugin.getStorageProvider().getClanMembers(clanName);
            if (clanMembers.isEmpty()) {
                plugin.getStorageProvider().deleteClan(clanName);
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

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_invalid_amount")));
            return;
        }

        Econo econ = VanguardClan.getEcon();
        StorageProvider storage = plugin.getStorageProvider();
        double clanBalance = storage.getClanMoney(playerClan);

        if (deposit) {
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

        if (!isLeader(player, playerClan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.economy_only_leader_withdraw")));
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

        if (!isLeader(player, playerClan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_not_leader")));
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

        // Verificar si el jugador es líder del clan
        if (!isLeader(inviter, inviterClan)) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.invite_only_leader")));
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



    private static final long INVITE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutos en ms

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
            if (!plugin.getStorageProvider().clanExists(clanToJoin)) {
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clan_not_exist")));
                return;
            }

            String privacy = plugin.getStorageProvider().getClanPrivacy(clanToJoin);
            boolean canJoin = "Public".equalsIgnoreCase(privacy);

            // If clan is private, check for invitation
            if (!canJoin) {
                canJoin = plugin.getStorageProvider().isPlayerInvitedToClan(playerName, clanToJoin);
                
                if (!canJoin) {
                    sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.clan_private")));
                    return;
                }
            }

            if (!hasAvailableSlot(clanToJoin)) {
                int used = plugin.getStorageProvider().getClanMemberCount(clanToJoin);
                int limit = calculateMaxSlots(clanToJoin);
                sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.slots_full")
                    .replace("{used}", String.valueOf(used))
                    .replace("{limit}", formatSlotLimit(limit))));
                return;
            }

            // Add player to clan
            plugin.getStorageProvider().addPlayerToClan(playerName, clanToJoin);
            
            // Remove invitation if exists
            plugin.getStorageProvider().removeClanInvite(clanToJoin, playerName);
            
            // Add to clan history
            PECMD.addClanToHistory(player, clanToJoin);

            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.joined_clan").replace("{clan}", clanToJoin)));
            plugin.getStorageProvider().reloadCache();

        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.join_error")));
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
        if (!isLeader(player, clanName)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_no_leader")));
            return;
        }

        if (args.length != 3) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_usage")));
            return;
        }

        String type = args[1];
        String value = args[2];

        if (type.equalsIgnoreCase("name")) {
            final String coloredName = ChatColor.translateAlternateColorCodes('&', value);
            final String plainName = ChatColor.stripColor(coloredName);
            final String oldClanName = clanName;

            if (plugin.isClanBanned(plainName)) {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("msg.clan_name_banned").replace("{clan}", plainName)));
                return;
            }

            // Verificar que el nombre no sea demasiado largo
            if (plainName.length() > 16) {
                player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_too_long").replace("{max}", "16")));
                return;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Verificar que no exista un clan con el nuevo nombre
                    if (plugin.getStorageProvider().clanExists(plainName)) {
                        Bukkit.getScheduler().runTask(plugin, () -> 
                            player.sendMessage(MSG.color("&cYa existe un clan con ese nombre."))
                        );
                        return;
                    }

                    // Actualizar el nombre del clan usando StorageProvider
                    plugin.getStorageProvider().updateClanName(oldClanName, plainName, coloredName);
                    
                    // Recargar el cache después de la actualización
                    plugin.getStorageProvider().reloadCache();

                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_success").replace("{name}", coloredName)))
                    );

                } catch (Exception e) {
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.edit_name_error")))
                    );
                }
            });
        }
    }






    public void disband(CommandSender sender, String playerClan) {
        if (playerClan == null || playerClan.isEmpty()) {
            sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.no_clan")));
            return;
        }

        Player player = (Player) sender;
        Econo econ = VanguardClan.getEcon();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Check if player is the leader
                String clanLeader = plugin.getStorageProvider().getClanLeader(playerClan);
                if (clanLeader == null || !clanLeader.equalsIgnoreCase(player.getName())) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.disband_not_leader")))
                    );
                    return;
                }

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

        // ❌ Verificar si está baneado
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

                // Límite de clanes
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

                // Economía
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
                    plugin.getStorageProvider().createClan(plainClanName, rawClanName, playerName, playerName, 0.0, "public");

                    Bukkit.getScheduler().runTask(plugin, () -> {
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

        if (!isLeader(player, playerClan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.not_leader_command")));
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

        if (!isLeader(player, playerClan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.not_leader_command")));
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

        if (!isLeader(player, playerClan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.not_leader_command")));
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
        if (!isLeader(player, clan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.not_leader_home")));
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
            // Teletransporta con delay y cancelación por movimiento
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





    // ------------------------------------
    // Métodos auxiliares:


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

        if (!isLeader(player, playerClan)) {
            player.sendMessage(MSG.color(langManager.getMessageWithPrefix("user.not_leader_command")));
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
                    "create", "disband", "report", "list", "join",
                    "kick", "invite", "chat", "leave", "stats", "resign", "edit",
                    "ff", "ally", "help", "home", "sethome", "delhome", "economy", "slots"
            ));

            case 2 -> {
                String arg0 = args[0].toLowerCase();
                switch (arg0) {
                    case "join" -> {
                        if (isNotInClan(playerClan)) completions.addAll(VanguardClan.getInstance().getStorageProvider().getCachedClanNames());
                    }
                    case "invite", "kick" -> {
                        if (isInClan(playerClan) && isLeader(player, playerClan)) completions.addAll(getOnlinePlayerNames());
                    }
                    case "economy" -> completions.addAll(List.of("deposit", "withdraw"));
                    case "report", "allyremove" -> completions.addAll(VanguardClan.getInstance().getStorageProvider().getCachedClanNames());
                    case "edit" -> {
                        if (isInClan(playerClan) && isLeader(player, playerClan)) {
                            completions.addAll(List.of("name", "privacy"));
                        }
                    }
                    case "ff" -> {
                        completions.addAll(List.of("on", "off"));
                    }
                    case "ally" -> {
                        completions.addAll(List.of("request", "accept", "decline", "remove", "ff"));
                    }
                    case "slots" -> completions.add("buy");
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
}
