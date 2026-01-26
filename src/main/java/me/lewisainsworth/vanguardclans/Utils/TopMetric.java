package me.lewisainsworth.vanguardclans.Utils;

import java.util.Locale;
import java.util.Optional;

public enum TopMetric {
    KDA("kda"),
    POINTS("points"),
    MONEY("money"),
    MEMBERS("members");

    private final String key;

    TopMetric(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static Optional<TopMetric> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (TopMetric metric : values()) {
            if (metric.key.equalsIgnoreCase(normalized)) {
                return Optional.of(metric);
            }
        }
        return Optional.empty();
    }
}
