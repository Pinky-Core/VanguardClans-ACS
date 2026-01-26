package me.lewisainsworth.vanguardclans.Utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ClanPermission {
    INVITE("invite", "Invite players"),
    KICK("kick", "Kick members"),
    SETHOME("sethome", "Set clan home"),
    DELHOME("delhome", "Delete clan home"),
    EDIT_NAME("edit_name", "Edit clan name"),
    EDIT_TAG("edit_tag", "Edit clan tag"),
    EDIT_PRIVACY("edit_privacy", "Edit clan privacy"),
    ALLY("ally", "Manage alliances"),
    ALLY_FF("ally_ff", "Toggle ally friendly fire"),
    FF("ff", "Toggle friendly fire"),
    BANK_DEPOSIT("bank_deposit", "Deposit clan money"),
    BANK_WITHDRAW("bank_withdraw", "Withdraw clan money"),
    SLOTS_UPGRADE("slots_upgrade", "Buy slot upgrades"),
    RANK_MANAGE("rank_manage", "Manage clan ranks"),
    DISBAND("disband", "Disband clan");

    private final String key;
    private final String label;

    ClanPermission(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<ClanPermission> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(p -> p.key.equalsIgnoreCase(normalized))
            .findFirst();
    }
}
