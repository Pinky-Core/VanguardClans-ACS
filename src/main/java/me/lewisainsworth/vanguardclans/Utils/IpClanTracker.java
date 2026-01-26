package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IpClanTracker {

    private static final String ROOT_KEY = "ip-clans";
    private static final String CLAN_MAP_KEY = "clan-ip";
    private static final String IP_LIST_KEY = "ip-data";

    private final VanguardClan plugin;
    private final FileHandler fileHandler;
    private final Map<String, String> clanToIp = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> ipToClans = new ConcurrentHashMap<>();

    public IpClanTracker(VanguardClan plugin) {
        this.plugin = plugin;
        this.fileHandler = plugin.getFH();
        reload();
    }

    public synchronized void reload() {
        clanToIp.clear();
        ipToClans.clear();

        FileConfiguration data = fileHandler.getData();
        ConfigurationSection root = data.getConfigurationSection(ROOT_KEY);
        if (root == null) {
            return;
        }

        ConfigurationSection clanSection = root.getConfigurationSection(CLAN_MAP_KEY);
        if (clanSection != null) {
            for (String clan : clanSection.getKeys(false)) {
                String ip = clanSection.getString(clan);
                if (ip == null || ip.trim().isEmpty()) {
                    continue;
                }
                String normalizedIp = normalizeIp(ip);
                clanToIp.put(clan, normalizedIp);
                ipToClans.computeIfAbsent(normalizedIp, key -> ConcurrentHashMap.newKeySet()).add(clan);
            }
        }
    }

    public synchronized void addClan(String ip, String clan) {
        if (clan == null || clan.isEmpty()) {
            return;
        }
        String normalizedIp = normalizeIp(ip);
        String previousIp = clanToIp.put(clan, normalizedIp);
        if (previousIp != null && !previousIp.equals(normalizedIp)) {
            removeFromIpSet(previousIp, clan);
        }
        ipToClans.computeIfAbsent(normalizedIp, key -> ConcurrentHashMap.newKeySet()).add(clan);
        persist();
    }

    public synchronized void removeClan(String clan) {
        if (clan == null || clan.isEmpty()) {
            return;
        }
        String ip = clanToIp.remove(clan);
        if (ip != null) {
            removeFromIpSet(ip, clan);
            persist();
        }
    }

    public synchronized void renameClan(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }
        String ip = clanToIp.remove(oldName);
        if (ip == null) {
            return;
        }
        clanToIp.put(newName, ip);
        Set<String> set = ipToClans.computeIfAbsent(ip, key -> ConcurrentHashMap.newKeySet());
        set.remove(oldName);
        set.add(newName);
        persist();
    }

    public synchronized int getClanCountForIp(String ip) {
        return ipToClans.getOrDefault(normalizeIp(ip), Collections.emptySet()).size();
    }

    public synchronized Optional<String> getIpForClan(String clan) {
        if (clan == null || clan.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(clanToIp.get(clan));
    }

    private void removeFromIpSet(String ip, String clan) {
        Set<String> set = ipToClans.get(ip);
        if (set != null) {
            set.remove(clan);
            if (set.isEmpty()) {
                ipToClans.remove(ip);
            }
        }
    }

    private void persist() {
        if (Bukkit.isPrimaryThread()) {
            saveData();
        } else {
            Bukkit.getScheduler().runTask(plugin, this::saveData);
        }
    }

    private void saveData() {
        FileConfiguration data = fileHandler.getData();
        data.set(ROOT_KEY, null);
        ConfigurationSection root = data.createSection(ROOT_KEY);

        ConfigurationSection clanSection = root.createSection(CLAN_MAP_KEY);
        ConfigurationSection ipSection = root.createSection(IP_LIST_KEY);
        clanToIp.forEach(clanSection::set);
        ipToClans.forEach((ip, clans) -> ipSection.set(ip, new ArrayList<>(clans)));

        fileHandler.saveData();
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return "unknown";
        }
        return ip.trim();
    }
}
