package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class LangManager {

    private final VanguardClan plugin;
    private final Map<String, YamlConfiguration> loadedLangs = new HashMap<>();
    private String currentLang;

    public LangManager(VanguardClan plugin) {
        this.plugin = plugin;
        this.currentLang = plugin.getConfig().getString("lang", "es").toLowerCase(Locale.ROOT);
        loadLangs();
    }

    private void loadLangs() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta /lang/");
            return;
        }

        loadedLangs.clear();

        File[] files = langFolder.listFiles(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No se encontraron archivos de idioma en /lang/");
            return;
        }

        for (File file : files) {
            String langName = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);
            plugin.getLogger().info("Cargando archivo de idioma: " + file.getName() + " como clave '" + langName + "'");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            loadedLangs.put(langName, config);
        }

        // Fallback
        if (!loadedLangs.containsKey(currentLang)) {
            plugin.getLogger().warning("Idioma configurado '" + currentLang + "' no encontrado.");
            currentLang = "es";
            plugin.getConfig().set("lang", currentLang);
            plugin.saveConfig();
            plugin.getLogger().info("Se usará el idioma por defecto: " + currentLang);
        }
    }

    public String getMessage(String path) {
        YamlConfiguration config = loadedLangs.getOrDefault(currentLang, loadedLangs.get("es"));
        if (config == null) {
            return "&c[LangManager] Archivo de idioma no encontrado.";
        }

        String msg = config.getString(path);
        if (msg == null) {
            plugin.getLogger().warning("Mensaje no encontrado en " + currentLang + ": " + path);
            return "&c¡Mensaje no encontrado! [" + path + "]";
        }

        return msg;
    }

    public String getMessageWithPrefix(String path) {
        String prefix = plugin.getConfig().getString("prefix", "&7[VanguardClans]");
        return prefix + " " + getMessage(path);
    }

    public List<String> getMessageList(String path) {
        YamlConfiguration config = loadedLangs.getOrDefault(currentLang, loadedLangs.get("es"));
        if (config == null) {
            return Collections.singletonList("&c[LangManager] Idioma no cargado.");
        }

        List<String> list = config.getStringList(path);
        if (list == null || list.isEmpty()) {
            return List.of("&c¡Mensaje de lista no encontrado! [" + path + "]");
        }

        return list;
    }

    public void setCurrentLang(String lang) {
        lang = lang.toLowerCase(Locale.ROOT);
        if (!loadedLangs.containsKey(lang)) return;
        this.currentLang = lang;
        plugin.getConfig().set("lang", lang);
        plugin.saveConfig();
        plugin.getLogger().info("Idioma cambiado a: " + lang);
    }

    public String getCurrentLang() {
        return currentLang;
    }

    public Set<String> getAvailableLangs() {
        return Collections.unmodifiableSet(loadedLangs.keySet());
    }

    public void reload() {
        loadLangs();
    }
}
