package me.lewisainsworth.vanguardclans.gui;

import me.lewisainsworth.vanguardclans.Utils.TopMetric;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ClanGuiHolder implements InventoryHolder {

    private final ClanGuiType type;
    private final String clanName;
    private final TopMetric metric;
    private final int page;
    private final String targetName;
    private final String roleName;
    private Inventory inventory;

    public ClanGuiHolder(ClanGuiType type, String clanName, TopMetric metric, int page, String targetName, String roleName) {
        this.type = type;
        this.clanName = clanName;
        this.metric = metric;
        this.page = page;
        this.targetName = targetName;
        this.roleName = roleName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public ClanGuiType getType() {
        return type;
    }

    public String getClanName() {
        return clanName;
    }

    public TopMetric getMetric() {
        return metric;
    }

    public int getPage() {
        return page;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getRoleName() {
        return roleName;
    }
}
