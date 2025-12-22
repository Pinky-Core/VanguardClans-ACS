package me.lewisainsworth.vanguardclans.Utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import me.lewisainsworth.vanguardclans.VanguardClan;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PAPI extends PlaceholderExpansion implements Relational {

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

    /**
     * Relational placeholders for nametag obfuscation when TAB is the provider.
     * Use %rel_vanguardclans_nametag_obfuscate_prefix% and %rel_vanguardclans_nametag_obfuscate_reset%.
     */
    @Override
    public String onPlaceholderRequest(Player viewer, Player target, @NotNull String identifier) {
        if (viewer == null || target == null) return "";

        String normalized = identifier.toLowerCase(Locale.ROOT);
        FileConfiguration config = plugin.getFH().getConfig();

        if (!normalized.startsWith("nametag_obfuscate")) {
            if (normalized.equals("nametag")) {
                return buildFullNametag(viewer, target, config);
            }
            return null;
        }

        if (!config.getBoolean("nametag-privacy.enabled", false)) {
            return "";
        }

        String bypass = config.getString("nametag-privacy.bypass-permission", "vanguardclans.bypass.nametag");
        if (bypass != null && !bypass.trim().isEmpty() && viewer.hasPermission(bypass.trim())) {
            return "";
        }

        String legacyMode = config.getString("nametag-privacy.mode", "hide");
        ConfigurationSection section = config.getConfigurationSection("nametag-privacy.obfuscate");
        boolean obfuscateEnabled = isObfuscationEnabled(section, legacyMode);
        if (!obfuscateEnabled) {
            return "";
        }

        Set<Relation> relations = parseRelations(section, obfuscateEnabled && isLegacyObfuscate(legacyMode));
        Relation relation = relationOf(viewer, target);
        if (relation == null || !relations.contains(relation)) {
            return "";
        }

        if (!isVisibilityAlways(config, legacyMode, relation)) {
            return "";
        }

        String prefix = color(section != null ? section.getString("prefix", "&k######") : "&k######");
        String reset = color(section != null ? section.getString("reset", "&r") : "&r");

        if (normalized.equals("nametag_obfuscate_prefix")) {
            return prefix;
        }
        if (normalized.equals("nametag_obfuscate_reset") || normalized.equals("nametag_obfuscate_suffix")) {
            return reset;
        }
        if (normalized.equals("nametag_obfuscate")) {
            return prefix + reset;
        }
        return "";
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

    private boolean isObfuscationEnabled(ConfigurationSection section, String legacyMode) {
        boolean legacy = isLegacyObfuscate(legacyMode);
        boolean sectionEnabled = section != null && section.getBoolean("enabled", false);
        return legacy || sectionEnabled;
    }

    private boolean isLegacyObfuscate(String legacyMode) {
        return "obfuscate".equalsIgnoreCase(legacyMode);
    }

    private Set<Relation> parseRelations(ConfigurationSection section, boolean legacyObfuscate) {
        EnumSet<Relation> result = EnumSet.noneOf(Relation.class);
        List<String> rawList = section != null ? section.getStringList("relations") : null;
        if (rawList == null || rawList.isEmpty()) {
            if (legacyObfuscate) {
                result.add(Relation.ENEMY);
            }
            return result;
        }

        for (String entry : rawList) {
            Relation relation = relationFromKey(entry);
            if (relation != null) {
                result.add(relation);
            } else {
                plugin.getLogger().warning("Relacion de obfuscacion invalida en PlaceholderAPI: " + entry);
            }
        }
        return result;
    }

    private Relation relationFromKey(String key) {
        if (key == null) return null;
        String normalized = key.trim()
            .toLowerCase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_");

        switch (normalized) {
            case "same":
            case "same_clan":
                return Relation.SAME;
            case "enemy":
            case "enemy_clan":
                return Relation.ENEMY;
            case "no_clan":
            case "no-clan":
            case "noclan":
                return Relation.NO_CLAN;
            default:
                return null;
        }
    }

    private Relation relationOf(Player viewer, Player target) {
        if (viewer.equals(target)) {
            return Relation.SAME;
        }
        String viewerClan = getPlayerClan(viewer.getName());
        String targetClan = getPlayerClan(target.getName());

        if (viewerClan != null && viewerClan.equalsIgnoreCase(targetClan)) {
            return Relation.SAME;
        }
        if (targetClan == null || targetClan.isEmpty()) {
            return Relation.NO_CLAN;
        }
        return Relation.ENEMY;
    }

    private boolean isVisibilityAlways(FileConfiguration config, String legacyMode, Relation relation) {
        boolean showNoClan = config.getBoolean("nametag-privacy.show-no-clan-names", true);
        String fallbackEnemy = isLegacyObfuscate(legacyMode) || "reset".equalsIgnoreCase(legacyMode) ? "always" : "never";
        String fallbackNoClan = showNoClan ? "always" : fallbackEnemy;

        String raw = switch (relation) {
            case SAME -> config.getString("nametag-privacy.visibility.same-clan", "always");
            case ENEMY -> config.getString("nametag-privacy.visibility.enemy-clan", fallbackEnemy);
            case NO_CLAN -> config.getString("nametag-privacy.visibility.no-clan", fallbackNoClan);
        };
        if (raw == null) raw = "never";

        String normalized = raw.trim()
            .toLowerCase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "");

        return normalized.equals("always");
    }

    private String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String buildFullNametag(Player viewer, Player target, FileConfiguration config) {
        if (target == null) return "";

        if (!config.getBoolean("nametag-privacy.enabled", false)) {
            return target.getName();
        }

        String bypass = config.getString("nametag-privacy.bypass-permission", "vanguardclans.bypass.nametag");
        if (bypass != null && !bypass.trim().isEmpty() && viewer != null && viewer.hasPermission(bypass.trim())) {
            return target.getName();
        }

        String legacyMode = config.getString("nametag-privacy.mode", "hide");
        ConfigurationSection section = config.getConfigurationSection("nametag-privacy.obfuscate");
        boolean obfuscateEnabled = isObfuscationEnabled(section, legacyMode);
        Set<Relation> relations = parseRelations(section, obfuscateEnabled && isLegacyObfuscate(legacyMode));

        Relation relation = viewer != null ? relationOf(viewer, target) : Relation.NO_CLAN;
        boolean visibilityAlways = isVisibilityAlways(config, legacyMode, relation);
        if (!visibilityAlways) {
            return "";
        }

        boolean shouldObfuscate = obfuscateEnabled && relations.contains(relation);
        String prefix = color(section != null ? section.getString("prefix", "&k######") : "&k######");
        String reset = color(section != null ? section.getString("reset", "&r") : "&r");

        if (shouldObfuscate) {
            return prefix + target.getName() + reset;
        }

        if ("reset".equalsIgnoreCase(legacyMode)) {
            return reset + target.getName() + reset;
        }

        return target.getName();
    }

    private enum Relation {
        SAME,
        ENEMY,
        NO_CLAN
    }
}
