package me.lewisainsworth.vanguardclans.Utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.lewisainsworth.vanguardclans.VanguardClan;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.Bukkit.getServer;
import static me.lewisainsworth.vanguardclans.VanguardClan.prefix;

public class Econo {

    private final VanguardClan plugin;
    private String system;

    private Object vaultProvider;
    private Class<?> vaultEconomyClass;
    private Method vaultGetBalance;
    private Method vaultHas;
    private Method vaultDeposit;
    private Method vaultWithdraw;
    private final Map<UUID, Double> internalBalances = new HashMap<>();
    private File balanceFile;
    private FileConfiguration balanceCfg;

    public Econo(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public String getSystem() {
        if (system == null) {
            FileConfiguration cfg = plugin.getConfig();
            system = cfg.getString("economy.system", "Vault").toLowerCase();
        }
        return system;
    }

    public String reload() {
        if (system == null) {
            FileConfiguration cfg = plugin.getConfig();
            system = cfg.getString("economy.system", "Vault").toLowerCase();
        }
        return system;
    }

    public boolean setupEconomy() {
        String selected = getSystem();
        if ("vault".equalsIgnoreCase(selected)) {
            if (setupVault()) {
                return true;
            }
            Bukkit.getConsoleSender().sendMessage(MSG.color(prefix + "&cVault no disponible, usando economia interna."));
            system = "internal";
            loadInternal();
            return true;
        }
        if ("internal".equalsIgnoreCase(selected)) {
            loadInternal();
            return true;
        }

        Bukkit.getConsoleSender().sendMessage(MSG.color(prefix + "&cEconomy system is null!: " + system));
        return false;
    }

    public double getBalance(OfflinePlayer player) {
        String selected = getSystem();
        if ("vault".equalsIgnoreCase(selected)) {
            return getVaultBalance(player);
        }
        if ("internal".equalsIgnoreCase(selected)) {
            return internalBalances.getOrDefault(player.getUniqueId(), 0.0);
        }
        return 0;
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (amount < 0) return;
        String selected = getSystem();
        if ("vault".equalsIgnoreCase(selected)) {
            depositVault(player, amount);
            return;
        }
        if ("internal".equalsIgnoreCase(selected)) {
            internalBalances.merge(player.getUniqueId(), amount, Double::sum);
            saveInternal();
        }
    }

    public void withdraw(OfflinePlayer player, double amount) {
        if (amount < 0) return;
        String selected = getSystem();
        if ("vault".equalsIgnoreCase(selected)) {
            withdrawVault(player, amount);
            return;
        }
        if ("internal".equalsIgnoreCase(selected)) {
            UUID id = player.getUniqueId();
            double current = internalBalances.getOrDefault(id, 0.0);
            if (current < amount) return;
            internalBalances.put(id, current - amount);
            saveInternal();
        }
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (amount < 0) return false;
        String selected = getSystem();
        if ("vault".equalsIgnoreCase(selected)) {
            return hasVault(player, amount);
        }
        if ("internal".equalsIgnoreCase(selected)) {
            return internalBalances.getOrDefault(player.getUniqueId(), 0.0) >= amount;
        }
        return false;
    }

    private boolean setupVault() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return false;
        }
        try {
            vaultEconomyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = getServer()
                .getServicesManager()
                .getRegistration(vaultEconomyClass);
            vaultProvider = (provider != null) ? provider.getProvider() : null;
            if (vaultProvider == null) {
                return false;
            }
            cacheVaultMethods();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void cacheVaultMethods() throws NoSuchMethodException {
        vaultGetBalance = vaultEconomyClass.getMethod("getBalance", OfflinePlayer.class);
        vaultHas = vaultEconomyClass.getMethod("has", OfflinePlayer.class, double.class);
        vaultDeposit = vaultEconomyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        vaultWithdraw = vaultEconomyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
    }

    private double getVaultBalance(OfflinePlayer player) {
        if (vaultProvider == null || vaultGetBalance == null) {
            return 0.0;
        }
        try {
            Object result = vaultGetBalance.invoke(vaultProvider, player);
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private boolean hasVault(OfflinePlayer player, double amount) {
        if (vaultProvider == null || vaultHas == null) {
            return false;
        }
        try {
            Object result = vaultHas.invoke(vaultProvider, player, amount);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void depositVault(OfflinePlayer player, double amount) {
        if (vaultProvider == null || vaultDeposit == null) {
            return;
        }
        try {
            vaultDeposit.invoke(vaultProvider, player, amount);
        } catch (Exception ignored) {
        }
    }

    private void withdrawVault(OfflinePlayer player, double amount) {
        if (vaultProvider == null || vaultWithdraw == null) {
            return;
        }
        try {
            vaultWithdraw.invoke(vaultProvider, player, amount);
        } catch (Exception ignored) {
        }
    }

    private void loadInternal() {
        balanceFile = new File(plugin.getDataFolder(), "balances.yml");
        if (!balanceFile.exists()) {
            balanceFile.getParentFile().mkdirs();
            try {
                balanceFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        balanceCfg = YamlConfiguration.loadConfiguration(balanceFile);

        for (String key : balanceCfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                double money = balanceCfg.getDouble(key + ".Money", 0.0);
                internalBalances.put(id, money);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveInternal() {
        if (balanceCfg == null) return;

        for (String key : balanceCfg.getKeys(false)) {
            balanceCfg.set(key, null);
        }

        for (Map.Entry<UUID, Double> entry : internalBalances.entrySet()) {
            UUID uuid = entry.getKey();
            double bal = entry.getValue();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "Unknown";

            balanceCfg.set(uuid + ".Name", name);
            balanceCfg.set(uuid + ".Money", bal);
        }

        try {
            balanceCfg.save(balanceFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
