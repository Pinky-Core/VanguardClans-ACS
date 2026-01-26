package me.lewisainsworth.vanguardclans.integration;

import me.lewisainsworth.vanguardclans.Utils.ClanTopCalculator;
import me.lewisainsworth.vanguardclans.Utils.ClanTopEntry;
import me.lewisainsworth.vanguardclans.Utils.TopMetric;
import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;

public class DiscordNotifier {

    private final VanguardClan plugin;
    private final ClanTopCalculator topCalculator;
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private final Map<TopMetric, String> lastLeaders = new EnumMap<>(TopMetric.class);
    private int watcherTaskId = -1;

    public DiscordNotifier(VanguardClan plugin) {
        this.plugin = plugin;
        this.topCalculator = new ClanTopCalculator(plugin);
    }

    public void reload() {
        stopWatcher();
        lastLeaders.clear();
        if (isTopLeaderEnabled()) {
            for (TopMetric metric : getWatchedMetrics()) {
                ClanTopEntry entry = getTopEntry(metric);
                lastLeaders.put(metric, entry == null ? null : entry.getClanName());
            }
        }
        startWatcher();
    }

    public void startWatcher() {
        if (!isTopLeaderEnabled()) {
            return;
        }
        long intervalSeconds = Math.max(1, getConfig().getLong("discord.webhooks.top-leader.interval-seconds", 60));
        long ticks = Math.max(20L, intervalSeconds * 20L);
        stopWatcher();
        watcherTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkTopLeaders, 20L, ticks).getTaskId();
    }

    public void stopWatcher() {
        if (watcherTaskId != -1) {
            Bukkit.getScheduler().cancelTask(watcherTaskId);
            watcherTaskId = -1;
        }
    }

    public void onClanCreated(String clan, String leader, String ip) {
        ConfigurationSection section = getConfig().getConfigurationSection("discord.webhooks.new-clan");
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }
        String webhookUrl = section.getString("url");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        int color = section.getInt("color", 3066993);
        String template = section.getString("message", "**New clan created:** {clan}");
        Map<String, String> tokens = Map.of(
            "clan", clan,
            "leader", leader == null ? "unknown" : leader,
            "ip", ip == null ? "unknown" : ip
        );
        String description = format(template, tokens);
        sendEmbed(webhookUrl, "Clan Created", description, color);
    }

    private void checkTopLeaders() {
        if (!isTopLeaderEnabled()) {
            stopWatcher();
            return;
        }

        for (TopMetric metric : getWatchedMetrics()) {
            ClanTopEntry entry = getTopEntry(metric);
            String leader = entry == null ? null : entry.getClanName();
            String previous = lastLeaders.get(metric);
            if (leader != null && !leader.equalsIgnoreCase(previous != null ? previous : "")) {
                lastLeaders.put(metric, leader);
                sendTopLeaderWebhook(entry, metric);
            } else if (leader == null) {
                lastLeaders.put(metric, null);
            }
        }
    }

    private void sendTopLeaderWebhook(ClanTopEntry entry, TopMetric metric) {
        if (entry == null) {
            return;
        }
        ConfigurationSection section = getConfig().getConfigurationSection("discord.webhooks.top-leader");
        if (section == null) {
            return;
        }
        String webhookUrl = section.getString("url");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        int color = section.getInt("color", 3447003);
        String template = section.getString("message", "**{clan}** is now #1 in {metric} with {value}.");
        Map<String, String> tokens = new HashMap<>();
        tokens.put("clan", entry.getClanName());
        tokens.put("metric", metric.getKey().toUpperCase(Locale.ROOT));
        tokens.put("value", formatMetricValue(entry, metric));
        tokens.put("leader", plugin.getStorageProvider().getClanLeader(entry.getClanName()));
        String description = format(template, tokens);
        sendEmbed(webhookUrl, "Top " + metric.getKey(), description, color);
    }

    private String formatMetricValue(ClanTopEntry entry, TopMetric metric) {
        if (entry == null || metric == null) {
            return "N/A";
        }
        double value = entry.getSortValue(metric);
        return metric == TopMetric.KDA ? formatDecimal(value) : numberFormat.format(value);
    }

    private String formatDecimal(double value) {
        return numberFormat.format(value);
    }

    private boolean isTopLeaderEnabled() {
        ConfigurationSection section = getConfig().getConfigurationSection("discord.webhooks.top-leader");
        return section != null && section.getBoolean("enabled", false) && section.getString("url") != null && !section.getString("url").isBlank();
    }

    private List<TopMetric> getWatchedMetrics() {
        List<TopMetric> metrics = new ArrayList<>();
        List<String> configured = getConfig().getStringList("discord.webhooks.top-leader.metrics");
        if (configured.isEmpty()) {
            metrics.add(TopMetric.POINTS);
            metrics.add(TopMetric.KDA);
            metrics.add(TopMetric.MONEY);
            metrics.add(TopMetric.MEMBERS);
            return metrics;
        }
        for (String key : configured) {
            TopMetric.fromKey(key).ifPresent(metrics::add);
        }
        if (metrics.isEmpty()) {
            metrics.add(TopMetric.POINTS);
        }
        return metrics;
    }

    private ClanTopEntry getTopEntry(TopMetric metric) {
        List<ClanTopEntry> entries = topCalculator.getTopEntries(metric);
        return entries.isEmpty() ? null : entries.get(0);
    }

    private void sendEmbed(String webhookUrl, String title, String description, int color) {
        try {
            String timestamp = Instant.now().toString().replace("+00:00", "Z");
            String payload = "{\"embeds\":[{\"title\":\"" + escapeJson(title) + "\",\"description\":\""
                + escapeJson(description) + "\",\"color\":" + color + ",\"timestamp\":\"" + timestamp + "\"}]}";
            HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "VanguardClans/" + plugin.getDescription().getVersion());
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            try (InputStream ignored = connection.getInputStream()) {
                // Consume response to avoid leaks
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private ConfigurationSection getConfig() {
        return plugin.getFH().getConfig();
    }

    private String format(String template, Map<String, String> tokens) {
        String result = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
