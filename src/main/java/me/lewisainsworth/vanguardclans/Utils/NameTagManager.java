package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simple nametag privacy manager with optional obfuscation (&k style). No ProtocolLib required.
 */
public class NameTagManager {

    private enum Relation {
        SAME,
        ENEMY,
        NO_CLAN
    }

    private enum Provider {
        INTERNAL,
        TAB
    }

    private final VanguardClan plugin;
    private final ScoreboardManager manager;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private Provider provider = Provider.INTERNAL;
    private boolean enabled = false;
    private boolean showNoClanNames = true;
    private boolean forceOverride = true;
    private int refreshIntervalTicks = 100;
    private String bypassPermission = "vanguardclans.bypass.nametag";
    private String legacyMode = "hide";
    private Team.OptionStatus visibilitySame = Team.OptionStatus.ALWAYS;
    private Team.OptionStatus visibilityEnemy = Team.OptionStatus.NEVER;
    private Team.OptionStatus visibilityNoClan = Team.OptionStatus.ALWAYS;
    private boolean obfuscateEnabled = false;
    private final Set<Relation> obfuscateRelations = EnumSet.noneOf(Relation.class);
    private String obfuscatePrefix = ChatColor.MAGIC.toString();
    private String obfuscateSuffix = ChatColor.RESET.toString();
    private int taskId = -1;

    public NameTagManager(VanguardClan plugin) {
        this.plugin = plugin;
        this.manager = Bukkit.getScoreboardManager();

        if (manager == null) {
            plugin.getLogger().warning("No ScoreboardManager found. Nametag privacy disabled.");
            return;
        }

        reload();
    }

    public void reload() {
        if (manager == null) return;

        FileConfiguration config = plugin.getFH().getConfig();
        boolean configEnabled = config.getBoolean("nametag-privacy.enabled", false);
        this.provider = parseProvider(config.getString("nametag-privacy.provider", "internal"));
        boolean tabPresent = Bukkit.getPluginManager().isPluginEnabled("TAB");
        if (provider == Provider.TAB && !tabPresent) {
            plugin.getLogger().warning("TAB configurado como proveedor de nametag pero no esta instalado; usando proveedor interno.");
            this.provider = Provider.INTERNAL;
        }

        this.showNoClanNames = config.getBoolean("nametag-privacy.show-no-clan-names", true);
        this.forceOverride = config.getBoolean("nametag-privacy.force-override", true);
        this.refreshIntervalTicks = Math.max(20, config.getInt("nametag-privacy.refresh-interval-ticks", 100));
        String bypass = config.getString("nametag-privacy.bypass-permission", "vanguardclans.bypass.nametag");
        this.bypassPermission = bypass == null ? "" : bypass.trim();

        ConfigurationSection visibilitySection = config.getConfigurationSection("nametag-privacy.visibility");
        this.legacyMode = config.getString("nametag-privacy.mode", "hide").toLowerCase(Locale.ROOT);
        Team.OptionStatus defaultEnemy = switch (legacyMode) {
            case "reset" -> Team.OptionStatus.ALWAYS;
            case "obfuscate" -> Team.OptionStatus.NEVER; // for obfuscate force hiding nametag (can't obfuscate the name itself)
            default -> Team.OptionStatus.NEVER;
        };
        boolean legacyObfuscate = "obfuscate".equalsIgnoreCase(legacyMode);

        // Force sensible defaults for hide/obfuscate
        this.visibilitySame = parseVisibility(visibilitySection, "same-clan", Team.OptionStatus.ALWAYS);
        this.visibilityEnemy = "hide".equals(legacyMode) || "obfuscate".equals(legacyMode)
            ? Team.OptionStatus.NEVER
            : parseVisibility(visibilitySection, "enemy-clan", defaultEnemy);
        Team.OptionStatus defaultNoClan = showNoClanNames ? Team.OptionStatus.ALWAYS : this.visibilityEnemy;
        this.visibilityNoClan = parseVisibility(visibilitySection, "no-clan", defaultNoClan);

        parseObfuscation(config, legacyObfuscate);

        if (provider == Provider.TAB && obfuscateEnabled) {
            plugin.getLogger().warning("Obfuscacion de nametag con TAB activa: usa los placeholders rel_vanguardclans_nametag_obfuscate_* en TAB. Gestor interno desactivado.");
        }

        this.enabled = provider == Provider.INTERNAL && configEnabled;

        logConfigSummary(configEnabled);

        if (!enabled) {
            if (provider == Provider.INTERNAL) {
                disableAll();
            }
            return;
        }

        refreshAll();
        restartAutoRefresh();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        disableAll();
    }

    public void resetTeams() {
        disableAll();
        if (enabled) {
            refreshAll();
        }
    }

    public void handleQuit(Player player) {
        if (manager == null) return;

        playerBoards.remove(player.getUniqueId());
        if (provider == Provider.INTERNAL) {
            player.setScoreboard(manager.getMainScoreboard());
        }

        for (Scoreboard board : playerBoards.values()) {
            removeFromPluginTeams(board, player.getName());
        }
    }

    public void applyToPlayer(Player player) {
        if (!enabled || manager == null) {
            if (manager != null && provider == Provider.INTERNAL) {
                player.setScoreboard(manager.getMainScoreboard());
            }
            playerBoards.remove(player.getUniqueId());
            return;
        }

        if (hasBypass(player)) {
            player.setScoreboard(manager.getMainScoreboard());
            playerBoards.remove(player.getUniqueId());
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
        if (!enabled || manager == null) return;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, refreshIntervalTicks).getTaskId();
    }

    private void restartAutoRefresh() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        startAutoRefresh();
    }

    private void refreshAll() {
        if (!enabled || manager == null) return;

        Map<String, String> clanSnapshot = snapshotClans();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewerBoard(viewer, clanSnapshot);
        }

        cleanupOfflineEntries();
    }

    private void refreshOthersFor(Player newTarget) {
        if (!enabled || manager == null) return;

        Map<String, String> clanSnapshot = snapshotClans();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(newTarget)) {
                updateViewerBoard(viewer, clanSnapshot);
            }
        }
    }

    private Map<String, String> snapshotClans() {
        Map<String, String> map = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            map.put(p.getName(), plugin.getPlayerClan(p.getName()));
        }
        return map;
    }

    private void updateViewerBoard(Player viewer) {
        updateViewerBoard(viewer, snapshotClans());
    }

    private void updateViewerBoard(Player viewer, Map<String, String> clanSnapshot) {
        if (hasBypass(viewer)) {
            viewer.setScoreboard(manager.getMainScoreboard());
            playerBoards.remove(viewer.getUniqueId());
            return;
        }

        Scoreboard board = playerBoards.computeIfAbsent(viewer.getUniqueId(), id -> manager.getNewScoreboard());
        viewer.setScoreboard(board);

        Team sameTeam = getOrCreateTeam(board, "vc_same");
        Team enemyTeam = getOrCreateTeam(board, "vc_enemy");
        Team noClanTeam = getOrCreateTeam(board, "vc_noclan");

        configureTeam(sameTeam, Relation.SAME);
        configureTeam(enemyTeam, Relation.ENEMY);
        configureTeam(noClanTeam, Relation.NO_CLAN);

        Set<String> assigned = new HashSet<>();
        String viewerClan = clanSnapshot.get(viewer.getName());

        for (Player target : Bukkit.getOnlinePlayers()) {
            Relation relation = relationOf(viewer, viewerClan, target, clanSnapshot);
            Team team = switch (relation) {
                case SAME -> sameTeam;
                case ENEMY -> enemyTeam;
                case NO_CLAN -> noClanTeam;
            };

            Team current = board.getEntryTeam(target.getName());
            if (current != team) {
                if (current != null && isPluginTeam(current.getName())) {
                    current.removeEntry(target.getName());
                }
                team.addEntry(target.getName());
            }
            assigned.add(target.getName());
        }

        removeUnassigned(board, assigned);
    }

    private Relation relationOf(Player viewer, String viewerClan, Player target, Map<String, String> clanSnapshot) {
        if (viewer.equals(target)) return Relation.SAME;

        String targetClan = clanSnapshot.get(target.getName());
        if (viewerClan != null && viewerClan.equalsIgnoreCase(targetClan)) {
            return Relation.SAME;
        }

        if (targetClan == null || targetClan.isEmpty()) {
            return Relation.NO_CLAN;
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
        Team.OptionStatus visibility = switch (relation) {
            case SAME -> visibilitySame;
            case ENEMY -> visibilityEnemy;
            case NO_CLAN -> visibilityNoClan;
        };

        // If legacy obfuscate mode, hide nametags for configured relations (can't obfuscate the name itself)
        if ("obfuscate".equals(legacyMode) && obfuscateEnabled && obfuscateRelations.contains(relation)) {
            visibility = Team.OptionStatus.NEVER;
        }
        String prefix = "";
        String suffix = ChatColor.RESET.toString();

        if (shouldObfuscate(relation, visibility)) {
            prefix = obfuscatePrefix;
            suffix = obfuscateSuffix;
        }

        team.setOption(Team.Option.NAME_TAG_VISIBILITY, visibility);
        team.setPrefix(prefix);
        team.setSuffix(suffix);
    }

    private boolean hasBypass(Player player) {
        return player != null
            && bypassPermission != null
            && !bypassPermission.isEmpty()
            && player.hasPermission(bypassPermission);
    }

    private Team.OptionStatus parseVisibility(ConfigurationSection section, String key, Team.OptionStatus fallback) {
        if (section == null) return fallback;

        String raw = section.getString(key);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        String normalized = raw.trim()
            .toLowerCase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_");

        // Map common string variants to Bukkit constants
        if (normalized.equals("hideforotherteams") || normalized.equals("hide_for_other_teams")) {
            return Team.OptionStatus.FOR_OTHER_TEAMS;
        }
        if (normalized.equals("hideforownteam") || normalized.equals("hide_for_own_team")) {
            return Team.OptionStatus.FOR_OWN_TEAM;
        }

        normalized = normalized.toUpperCase(Locale.ROOT);

        try {
            return Team.OptionStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid nametag visibility for " + key + ": " + raw + " (using " + fallback + ")");
            return fallback;
        }
    }

    private void parseObfuscation(FileConfiguration config, boolean legacyObfuscate) {
        obfuscateRelations.clear();
        ConfigurationSection section = config.getConfigurationSection("nametag-privacy.obfuscate");

        boolean enabledBySection = section != null && section.getBoolean("enabled", legacyObfuscate);
        this.obfuscateEnabled = legacyObfuscate || enabledBySection;

        Set<Relation> fallback = legacyObfuscate ? EnumSet.of(Relation.ENEMY) : EnumSet.noneOf(Relation.class);
        List<String> rawRelations = section != null ? section.getStringList("relations") : null;
        obfuscateRelations.addAll(parseRelations(rawRelations, fallback));

        String prefixRaw = section != null ? section.getString("prefix", "&k######") : "&k######";
        String resetRaw = section != null ? section.getString("reset", "&r") : "&r";
        this.obfuscatePrefix = color(prefixRaw);
        this.obfuscateSuffix = color(resetRaw);
    }

    private Set<Relation> parseRelations(List<String> raw, Set<Relation> fallback) {
        EnumSet<Relation> result = EnumSet.noneOf(Relation.class);
        if (raw != null) {
            for (String entry : raw) {
                Relation relation = relationFromKey(entry);
                if (relation != null) {
                    result.add(relation);
                } else {
                    plugin.getLogger().warning("Relacion de obfuscacion invalida: " + entry);
                }
            }
        }
        if (result.isEmpty() && fallback != null) {
            result.addAll(fallback);
        }
        return result;
    }

    private Relation relationFromKey(String key) {
        if (key == null) return null;

        String normalized = key.trim()
            .toLowerCase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_");

        return switch (normalized) {
            case "same", "same_clan" -> Relation.SAME;
            case "enemy", "enemy_clan" -> Relation.ENEMY;
            case "no_clan", "no-clan", "noclan" -> Relation.NO_CLAN;
            default -> null;
        };
    }

    private boolean shouldObfuscate(Relation relation, Team.OptionStatus visibility) {
        return obfuscateEnabled
            && visibility == Team.OptionStatus.ALWAYS
            && obfuscateRelations.contains(relation);
    }

    private String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private Provider parseProvider(String raw) {
        if (raw == null) return Provider.INTERNAL;

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("tab")) {
            return Provider.TAB;
        }
        return Provider.INTERNAL;
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

        if (provider == Provider.INTERNAL) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
        playerBoards.clear();
    }

    private boolean isPluginTeam(String name) {
        return name != null && name.startsWith("vc_");
    }

    private void logConfigSummary(boolean configEnabled) {
        String providerName = provider.name();
        String relations = obfuscateRelations.isEmpty() ? "none" : obfuscateRelations.toString();
        plugin.getLogger().info("[Nametag] provider=" + providerName
            + " enabled=" + enabled + " (config=" + configEnabled + ")"
            + " obfuscate=" + obfuscateEnabled + " relations=" + relations
            + " visibilitySame=" + visibilitySame
            + " visibilityEnemy=" + visibilityEnemy
            + " visibilityNoClan=" + visibilityNoClan
            + " refreshTicks=" + refreshIntervalTicks
            + " forceOverride=" + forceOverride
            + " bypassPerm=" + bypassPermission);
    }
}
