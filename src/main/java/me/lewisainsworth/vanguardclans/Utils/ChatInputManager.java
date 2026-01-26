package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class ChatInputManager {

    private final VanguardClan plugin;
    private final Map<UUID, ChatInputRequest> pending = new ConcurrentHashMap<>();

    public ChatInputManager(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public void requestInput(Player player, String prompt, BiConsumer<Player, String> handler) {
        if (player == null || handler == null) {
            return;
        }
        pending.put(player.getUniqueId(), new ChatInputRequest(handler));
        if (prompt != null && !prompt.isEmpty()) {
            player.sendMessage(MSG.color(prompt));
        }
    }

    public boolean handleChat(Player player, String message) {
        if (player == null) {
            return false;
        }
        ChatInputRequest request = pending.remove(player.getUniqueId());
        if (request == null) {
            return false;
        }
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(MSG.color(plugin.getLangManager().getMessageWithPrefix("user.input_cancelled")));
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> request.handler.accept(player, input));
        return true;
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        pending.remove(player.getUniqueId());
    }

    public boolean hasPending(Player player) {
        if (player == null) {
            return false;
        }
        return pending.containsKey(player.getUniqueId());
    }

    private static class ChatInputRequest {
        private final BiConsumer<Player, String> handler;

        private ChatInputRequest(BiConsumer<Player, String> handler) {
            this.handler = handler;
        }
    }
}
