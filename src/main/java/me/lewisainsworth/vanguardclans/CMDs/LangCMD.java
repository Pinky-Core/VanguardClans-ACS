package me.lewisainsworth.vanguardclans.CMDs;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;

public class LangCMD implements CommandExecutor {

    private final VanguardClan plugin;
    private final LangManager langManager;

    // Mapa para mostrar nombre legible de idiomas
    private final Map<String, String> languageNames = Map.of(
            "es", "Español",
            "en", "English"
            // Podés agregar más idiomas aquí
    );

    public LangCMD(VanguardClan plugin) {
        this.plugin = plugin;
        this.langManager = plugin.getLangManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MSG.color(langManager.getMessage("lang.lang_not_found")));
            return true;
        }
        Player p = (Player) sender;

        if (!p.hasPermission("vanguardclans.admin")) {
            p.sendMessage(MSG.color(langManager.getMessage("lang.no_permission")));
            return true;
        }

        showLanguageMenu(p);
        return true;
    }

    public void showLanguageMenu(Player player) {
        player.sendMessage(MSG.color(langManager.getMessage("lang.menu_header")));

        String currentLang = langManager.getCurrentLang();
        String langDisplay = currentLang.toUpperCase() + " - " +
                languageNames.getOrDefault(currentLang, "Custom");
        String langDisplayName = plugin.getLangCMD().getLanguageDisplayName(currentLang);

        player.sendMessage(MSG.color(
                langManager.getMessage("lang.current_lang")
                        .replace("{lang}", langDisplay)
                        .replace("{lang_name}", langDisplayName)
        ));

        player.sendMessage(MSG.color(langManager.getMessage("lang.lang_list_title")));

        for (String lang : langManager.getAvailableLangs()) {
            String displayName = languageNames.getOrDefault(lang, lang.toUpperCase());
            boolean isSelected = lang.equalsIgnoreCase(currentLang);

            TextComponent line = new TextComponent("» ");
            line.setColor(net.md_5.bungee.api.ChatColor.GOLD);

            TextComponent langText = new TextComponent(lang.toUpperCase() + " - " + displayName);
            langText.setColor(isSelected ? net.md_5.bungee.api.ChatColor.GREEN : net.md_5.bungee.api.ChatColor.GRAY);
            if (isSelected) langText.setBold(true);

            langText.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponent[]{
                            new TextComponent(MSG.color(
                                    langManager.getMessage(isSelected
                                            ? "lang.actual_lang"
                                            : "lang.select_lang")
                            ))
                    }));

            langText.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/clanadmin lang select " + lang));

            line.addExtra(langText);

            if (isSelected) {
                TextComponent check = new TextComponent(" ✓");
                check.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                line.addExtra(check);
            }

            player.spigot().sendMessage(line);
        }

        player.sendMessage(MSG.color(langManager.getMessage("lang.menu_footer")));
    }

    // Este método debe llamarse desde el main o desde ACMD al detectar /clanadmin lang select <lang>
    public void setLanguageCommand(Player player, String lang) {
        if (!langManager.getAvailableLangs().contains(lang)) {
            player.sendMessage(MSG.color(langManager.getMessage("lang.lang_not_found")));
            return;
        }
        langManager.setCurrentLang(lang);
        
        // Recargar el plugin para actualizar todos los mensajes
        langManager.reload();
        
        // Recargar las helpLines en CCMD
        plugin.getCommand("clan").getExecutor();
        if (plugin.getCommand("clan").getExecutor() instanceof CCMD) {
            ((CCMD) plugin.getCommand("clan").getExecutor()).reloadHelpLines();
        }

        String display = lang.toUpperCase() + " - " + languageNames.getOrDefault(lang, "Custom");
        player.sendMessage(MSG.color(
                langManager.getMessage("lang.lang_changed")
                        .replace("{lang}", display)
        ));
    }

    public String getLanguageDisplayName(String code) {
        return languageNames.getOrDefault(code, "Custom");
    }
}
