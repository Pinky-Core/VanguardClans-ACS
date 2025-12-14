package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Handles nametag privacy per player so clanmates see real names and others see obfuscated names.
 */
public class NameTagManager {

    private enum Mode {
        OBFUSCATE, // use &k style for other clans
        HIDE,      // hide other clan nametags
        RESET      // show everything normally
    }

    private enum Relation {
        SAME,
        ENEMY,
        NO_CLAN
    }

    private final VanguardClan plugin;
    private final ScoreboardManager manager;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private boolean enabled = false;
    private Mode mode = Mode.OBFUSCATE;
    private boolean showNoClanNames = true;
    private boolean forceOverride = true;
    private int taskId = -1;

    public NameTagManager(VanguardClan plugin) {
        this.plugin = plugin;
        this.manager = Bukkit.getScoreboardManager();

        if (manager == null) {
            plugin.getLogger().warning("No ScoreboardManager found. Nametag privacy disabled.");
            return;
        }

        reload();
        startAutoRefresh();
    }

    public void reload() {
        if (manager == null) return;

        FileConfiguration config = plugin.getFH().getConfig();
        this.enabled = config.getBoolean("nametag-privacy.enabled", false);
        this.showNoClanNames = config.getBoolean("nametag-privacy.show-no-clan-names", true);
        this.forceOverride = config.getBoolean("nametag-privacy.force-override", true);

        String modeValue = config.getString("nametag-privacy.mode", "obfuscate");
        if ("hide".equalsIgnoreCase(modeValue)) {
            this.mode = Mode.HIDE;
        } else if ("reset".equalsIgnoreCase(modeValue)) {
            this.mode = Mode.RESET;
        } else {
            this.mode = Mode.OBFUSCATE;
        }

        if (!enabled) {
            disableAll();
            return;
        }

        refreshAll();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        disableAll();
    }

    public void handleQuit(Player player) {
        if (manager == null) return;

        playerBoards.remove(player.getUniqueId());
        player.setScoreboard(manager.getMainScoreboard());

        for (Scoreboard board : playerBoards.values()) {
            removeFromPluginTeams(board, player.getName());
        }
    }

    public void applyToPlayer(Player player) {
        if (!enabled || manager == null) {
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
            return;
        }

        updateViewerBoard(player);
        refreshOthersFor(player);

        if (forceOverride) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    updateViewerBoard(player);
                }
            });
        }
    }

    private void startAutoRefresh() {
        if (manager == null) return;

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 100L).getTaskId(); // every 5s
    }

    private void refreshAll() {
        if (!enabled || manager == null) return;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewerBoard(viewer);
        }

        cleanupOfflineEntries();
    }

    private void refreshOthersFor(Player newTarget) {
        if (!enabled || manager == null) return;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(newTarget)) {
                updateViewerBoard(viewer);
            }
        }
    }

    private void updateViewerBoard(Player viewer) {
        Scoreboard board = playerBoards.computeIfAbsent(viewer.getUniqueId(), id -> manager.getNewScoreboard());
        viewer.setScoreboard(board);

        Team sameTeam = getOrCreateTeam(board, "vc_same");
        Team enemyTeam = getOrCreateTeam(board, "vc_enemy");
        Team noClanTeam = getOrCreateTeam(board, "vc_noclan");

        configureTeam(sameTeam, Relation.SAME);
        configureTeam(enemyTeam, Relation.ENEMY);
        configureTeam(noClanTeam, Relation.NO_CLAN);

        Set<String> assigned = new HashSet<>();
        String viewerClan = plugin.getPlayerClan(viewer.getName());

        for (Player target : Bukkit.getOnlinePlayers()) {
            Relation relation = relationOf(viewer, viewerClan, target);
            Team team = switch (relation) {
                case SAME -> sameTeam;
                case ENEMY -> enemyTeam;
                case NO_CLAN -> noClanTeam;
            };

            team.addEntry(target.getName());
            assigned.add(target.getName());
        }

        removeUnassigned(board, assigned);
    }

    private Relation relationOf(Player viewer, String viewerClan, Player target) {
        if (viewer.equals(target)) {
            return Relation.SAME;
        }

        String targetClan = plugin.getPlayerClan(target.getName());
        if (viewerClan != null && viewerClan.equalsIgnoreCase(targetClan)) {
            return Relation.SAME;
        }

        if (targetClan == null || targetClan.isEmpty()) {
            return showNoClanNames ? Relation.NO_CLAN : Relation.ENEMY;
        }

        return Relation.ENEMY;
    }

    private Team getOrCreateTeam(Scoreboard board, String name) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        return team;
    }

    private void configureTeam(Team team, Relation relation) {
        Team.OptionStatus visibility = Team.OptionStatus.ALWAYS;
        String prefix = "";
        String suffix = ChatColor.RESET.toString();

        if (relation == Relation.ENEMY) {
            if (mode == Mode.HIDE) {
                visibility = Team.OptionStatus.NEVER;
            } else if (mode == Mode.OBFUSCATE) {
                prefix = ChatColor.MAGIC.toString();
            }
        } else if (relation == Relation.NO_CLAN && !showNoClanNames) {
            if (mode == Mode.HIDE) {
                visibility = Team.OptionStatus.NEVER;
            } else if (mode == Mode.OBFUSCATE) {
                prefix = ChatColor.MAGIC.toString();
            }
        }

        team.setOption(Team.Option.NAME_TAG_VISIBILITY, visibility);
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        // Avoid setting team color to prevent stripping obfuscation codes.
    }

    private void removeUnassigned(Scoreboard board, Set<String> assigned) {
        for (Team team : board.getTeams()) {
            if (!isPluginTeam(team.getName())) continue;

            Set<String> copy = new HashSet<>(team.getEntries());
            for (String entry : copy) {
                if (!assigned.contains(entry)) {
                    team.removeEntry(entry);
                }
            }
        }
    }

    private void cleanupOfflineEntries() {
        for (Scoreboard board : playerBoards.values()) {
            for (Team team : board.getTeams()) {
                if (!isPluginTeam(team.getName())) continue;
                Set<String> copy = new HashSet<>(team.getEntries());
                for (String entry : copy) {
                    if (Bukkit.getPlayerExact(entry) == null) {
                        team.removeEntry(entry);
                    }
                }
            }
        }
    }

    private void removeFromPluginTeams(Scoreboard board, String entry) {
        for (Team team : board.getTeams()) {
            if (isPluginTeam(team.getName()) && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private void disableAll() {
        if (manager == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(manager.getMainScoreboard());
        }
        playerBoards.clear();
    }

    private boolean isPluginTeam(String name) {
        return name != null && name.startsWith("vc_");
    }
}
