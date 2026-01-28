package me.lewisainsworth.vanguardclans.gui;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.ClanPermission;
import me.lewisainsworth.vanguardclans.Utils.ClanRoleManager;
import me.lewisainsworth.vanguardclans.Utils.ClanTopCalculator;
import me.lewisainsworth.vanguardclans.Utils.ClanTopEntry;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import me.lewisainsworth.vanguardclans.Utils.TopMetric;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClanGuiManager implements Listener {

    private static final int SMALL_SIZE = 27;
    private static final int LARGE_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int MEMBERS_SLOT_DEPOSIT = 40;
    private static final int MEMBERS_SLOT_WITHDRAW = 42;
    private static final int MEMBERS_SLOT_ADD = 44;
    private static final int MEMBERS_SLOT_REMOVE = 52;

    private final VanguardClan plugin;
    private final LangManager lang;
    private final ClanTopCalculator topCalculator;
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private final NamespacedKey valueKey;
    private final NamespacedKey roleKey;

    public ClanGuiManager(VanguardClan plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLangManager();
        this.topCalculator = new ClanTopCalculator(plugin);
        this.valueKey = new NamespacedKey(plugin, "gui_value");
        this.roleKey = new NamespacedKey(plugin, "gui_role");
    }

    public void openMainMenu(Player player) {
        if (player == null) {
            return;
        }
        String clan = plugin.getStorageProvider().getCachedPlayerClan(player.getName());
        if (clan == null || clan.isEmpty()) {
            openNoClanMenu(player);
        } else {
            openClanMenu(player, clan);
        }
    }

    public void openTopSelect(Player player) {
        if (player == null) {
            return;
        }
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.TOP_SELECT, null, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.top_select_title"));

        inv.setItem(10, createItem(Material.IRON_SWORD, lang.getMessage("gui.top_kda"), Arrays.asList(
            lang.getMessage("gui.top_kda_lore")
        )));
        inv.setItem(12, createItem(Material.EXPERIENCE_BOTTLE, lang.getMessage("gui.top_points"), null));
        inv.setItem(14, createItem(Material.GOLD_INGOT, lang.getMessage("gui.top_money"), null));
        inv.setItem(16, createItem(Material.PLAYER_HEAD, lang.getMessage("gui.top_members"), null));
        inv.setItem(22, createItem(Material.ARROW, lang.getMessage("gui.item_back"), null));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_close"), null));

        fill(inv);
        player.openInventory(inv);
    }

    public void openTopList(Player player, TopMetric metric, int page) {
        if (player == null || metric == null) {
            return;
        }
        List<ClanTopEntry> entries = topCalculator.getTopEntries(metric);
        int totalPages = getTotalPages(entries.size(), PAGE_SIZE);
        int safePage = clampPage(page, totalPages);

        String title = lang.getMessage("gui.top_list_title")
            .replace("{metric}", metric.getKey().toUpperCase(Locale.ROOT))
            .replace("{page}", String.valueOf(safePage))
            .replace("{pages}", String.valueOf(totalPages));
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.TOP_LIST, null, metric, safePage, null, null);
        Inventory inv = createInventory(holder, LARGE_SIZE, title);

        int start = (safePage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        for (int i = start; i < end; i++) {
            ClanTopEntry entry = entries.get(i);
            int slot = i - start;
            inv.setItem(slot, createTopItem(entry, i + 1));
        }

        inv.setItem(45, createItem(Material.ARROW, lang.getMessage("gui.item_prev"), null));
        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.ARROW, lang.getMessage("gui.item_next"), null));

        fill(inv);
        player.openInventory(inv);
    }

    public void openRolesMenu(Player player) {
        if (player == null) {
            return;
        }
        String clan = plugin.getStorageProvider().getCachedPlayerClan(player.getName());
        if (clan == null || clan.isEmpty()) {
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.no_clan")));
            return;
        }

        ClanRoleManager roleManager = plugin.getRoleManager();
        Set<String> roleSet = roleManager != null ? roleManager.getAssignableRoles(clan) : Collections.emptySet();
        List<String> roles = new ArrayList<>(roleSet);
        roles.remove(ClanRoleManager.ROLE_LEADER);
        Collections.sort(roles);

        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.ROLES, clan, null, 1, null, null);
        Inventory inv = createInventory(holder, LARGE_SIZE, lang.getMessage("gui.roles_title"));

        int index = 0;
        for (String role : roles) {
            if (index >= PAGE_SIZE) {
                break;
            }
            inv.setItem(index, createRoleItem(clan, role));
            index++;
        }

        inv.setItem(45, createItem(Material.ANVIL, lang.getMessage("gui.item_role_create"), null));
        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.LAVA_BUCKET, lang.getMessage("gui.item_role_delete"), null));

        fill(inv);
        player.openInventory(inv);
    }
    private void openClanMenu(Player player, String clan) {
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.MAIN, clan, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.main_title"));

        inv.setItem(10, createItem(Material.BOOK, lang.getMessage("gui.item_info"), null));
        inv.setItem(11, createItem(Material.PLAYER_HEAD, lang.getMessage("gui.item_members"), null));
        inv.setItem(12, createItem(Material.PAPER, lang.getMessage("gui.item_invites"), null));
        inv.setItem(13, createItem(Material.FIREWORK_ROCKET, lang.getMessage("gui.item_allies"), null));
        inv.setItem(14, createItem(Material.GOLD_INGOT, lang.getMessage("gui.item_bank"), null));
        inv.setItem(15, createItem(Material.NAME_TAG, lang.getMessage("gui.item_edit"), null));
        inv.setItem(16, createItem(Material.ENDER_PEARL, lang.getMessage("gui.item_home"), null));
        inv.setItem(22, createItem(Material.NETHER_STAR, lang.getMessage("gui.item_top"), null));
        inv.setItem(23, createItem(Material.ANVIL, lang.getMessage("gui.item_ranks"), null));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_close"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openNoClanMenu(Player player) {
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.NO_CLAN, null, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.no_clan_title"));

        inv.setItem(11, createItem(Material.EMERALD_BLOCK, lang.getMessage("gui.item_create"), null));
        inv.setItem(13, createItem(Material.PAPER, lang.getMessage("gui.item_invites"), null));
        inv.setItem(15, createItem(Material.BOOK, lang.getMessage("gui.item_list"), null));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_close"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openInfoMenu(Player player, String clan) {
        ClanTopEntry stats = buildClanStats(clan);
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.INFO, clan, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.info_title"));

        List<String> lore = new ArrayList<>();
        lore.add(lang.getMessage("gui.info_leader").replace("{leader}", safeValue(plugin.getStorageProvider().getClanLeader(clan))));
        lore.add(lang.getMessage("gui.info_founder").replace("{founder}", safeValue(plugin.getStorageProvider().getClanFounder(clan))));
        lore.add(lang.getMessage("gui.info_privacy").replace("{privacy}", safeValue(plugin.getStorageProvider().getClanPrivacy(clan))));
        lore.add(lang.getMessage("gui.info_members").replace("{members}", String.valueOf(stats.getMembers())));
        lore.add(lang.getMessage("gui.info_points").replace("{points}", String.valueOf(stats.getPoints())));
        lore.add(lang.getMessage("gui.info_money").replace("{money}", numberFormat.format(stats.getMoney())));
        lore.add(lang.getMessage("gui.info_kda_total").replace("{kda}", formatDecimal(stats.getTotalKda())));
        lore.add(lang.getMessage("gui.info_kda_avg").replace("{kda}", formatDecimal(stats.getAverageKda())));

        String displayName = formatClanDisplayName(player, clan, "clan-name-template");
        inv.setItem(13, createItem(Material.BOOK, displayName, lore));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openMembersMenu(Player player, String clan, int page) {
        List<String> members = plugin.getStorageProvider().getClanMembers(clan);
        int totalPages = getTotalPages(members.size(), PAGE_SIZE);
        int safePage = clampPage(page, totalPages);

        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.MEMBERS, clan, null, safePage, null, null);
        Inventory inv = createInventory(holder, LARGE_SIZE, lang.getMessage("gui.members_title")
            .replace("{page}", String.valueOf(safePage))
            .replace("{pages}", String.valueOf(totalPages)));

        int start = (safePage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, members.size());
        for (int i = start; i < end; i++) {
            String member = members.get(i);
            inv.setItem(i - start, createMemberItem(clan, member));
        }

        inv.setItem(45, createItem(Material.ARROW, lang.getMessage("gui.item_prev"), null));
        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.ARROW, lang.getMessage("gui.item_next"), null));

        fill(inv);
        player.openInventory(inv);
    }

    public void openMembersView(Player player, String clan) {
        if (player == null) {
            return;
        }
        if (clan == null || clan.trim().isEmpty()) {
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.info_clan_not_found")));
            return;
        }
        String resolved = resolveClanName(clan);
        if (!plugin.getStorageProvider().clanExists(resolved)) {
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.info_clan_not_found")
                .replace("{clan}", resolved)));
            return;
        }
        openMembersMenu(player, resolved, 1);
    }

    private void openInvitesMenu(Player player, String clan, int page) {
        boolean clanInvites = clan != null && !clan.isEmpty();
        plugin.getStorageProvider().cleanupExpiredInvites();
        List<String> invites = clanInvites
            ? plugin.getStorageProvider().getClanInvites(clan)
            : plugin.getStorageProvider().getPlayerInvites(player.getName());
        int totalPages = getTotalPages(invites.size(), PAGE_SIZE);
        int safePage = clampPage(page, totalPages);

        String titleKey = clanInvites ? "gui.invites_title" : "gui.player_invites_title";
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.INVITES, clan, null, safePage, null, null);
        Inventory inv = createInventory(holder, LARGE_SIZE, lang.getMessage(titleKey)
            .replace("{page}", String.valueOf(safePage))
            .replace("{pages}", String.valueOf(totalPages)));

        int start = (safePage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, invites.size());
        for (int i = start; i < end; i++) {
            String value = invites.get(i);
            inv.setItem(i - start, createInviteItem(value, clanInvites));
        }

        inv.setItem(45, createItem(Material.ARROW, lang.getMessage("gui.item_prev"), null));
        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.ARROW, lang.getMessage("gui.item_next"), null));

        fill(inv);
        player.openInventory(inv);
    }
    private void openAlliesMenu(Player player, String clan, int page) {
        List<String> allies = plugin.getStorageProvider().getClanAlliances(clan);
        List<String> pending = plugin.getStorageProvider().getPendingAlliances(clan);
        List<ItemStack> items = new ArrayList<>();

        for (String ally : allies) {
            items.add(createAllyItem(ally, false));
        }
        for (String requester : pending) {
            items.add(createAllyItem(requester, true));
        }

        int totalPages = getTotalPages(items.size(), PAGE_SIZE);
        int safePage = clampPage(page, totalPages);
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.ALLIES, clan, null, safePage, null, null);
        Inventory inv = createInventory(holder, LARGE_SIZE, lang.getMessage("gui.allies_title")
            .replace("{page}", String.valueOf(safePage))
            .replace("{pages}", String.valueOf(totalPages)));

        int start = (safePage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, items.get(i));
        }

        inv.setItem(45, createItem(Material.ARROW, lang.getMessage("gui.item_prev"), null));
        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.ARROW, lang.getMessage("gui.item_next"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openBankMenu(Player player, String clan) {
        double money = plugin.getStorageProvider().getClanMoney(clan);
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.BANK, clan, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.bank_title"));

        inv.setItem(11, createItem(Material.EMERALD, lang.getMessage("gui.item_deposit"), null));
        inv.setItem(13, createItem(Material.GOLD_BLOCK, lang.getMessage("gui.bank_balance")
            .replace("{money}", numberFormat.format(money)), null));
        inv.setItem(15, createItem(Material.REDSTONE, lang.getMessage("gui.item_withdraw"), null));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openEditMenu(Player player, String clan) {
        String privacy = plugin.getStorageProvider().getClanPrivacy(clan);
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.EDIT, clan, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.edit_title"));

        inv.setItem(11, createItem(Material.NAME_TAG, lang.getMessage("gui.item_edit_name"), null));
        inv.setItem(13, createItem(Material.PAPER, lang.getMessage("gui.item_edit_tag"), null));
        inv.setItem(15, createItem(Material.LEVER, lang.getMessage("gui.item_edit_privacy")
            .replace("{privacy}", safeValue(privacy)), null));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openHomeMenu(Player player, String clan) {
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.HOME, clan, null, 1, null, null);
        Inventory inv = createInventory(holder, SMALL_SIZE, lang.getMessage("gui.home_title"));

        inv.setItem(11, createItem(Material.ENDER_PEARL, lang.getMessage("gui.item_teleport"), null));
        inv.setItem(13, createItem(Material.RED_BED, lang.getMessage("gui.item_sethome"), null));
        inv.setItem(15, createItem(Material.BARRIER, lang.getMessage("gui.item_delhome"), null));
        inv.setItem(26, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openRolePermsMenu(Player player, String clan, String role) {
        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.ROLE_PERMS, clan, null, 1, null, role);
        Inventory inv = createInventory(holder, LARGE_SIZE, lang.getMessage("gui.role_perms_title")
            .replace("{role}", formatRole(role)));

        Map<String, Set<ClanPermission>> rolePerms = plugin.getRoleManager().getClanRolePermissions(clan);
        Set<ClanPermission> perms = rolePerms.getOrDefault(role.toLowerCase(Locale.ROOT), Collections.<ClanPermission>emptySet());

        int index = 0;
        for (ClanPermission permission : ClanPermission.values()) {
            if (index >= PAGE_SIZE) {
                break;
            }
            boolean enabled = perms.contains(permission);
            inv.setItem(index, createPermissionItem(permission, enabled));
            index++;
        }

        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.BUCKET, lang.getMessage("gui.item_clear"), null));

        fill(inv);
        player.openInventory(inv);
    }

    private void openRoleAssignMenu(Player player, String clan, String role, int page) {
        List<String> members = plugin.getStorageProvider().getClanMembers(clan);
        int totalPages = getTotalPages(members.size(), PAGE_SIZE);
        int safePage = clampPage(page, totalPages);

        ClanGuiHolder holder = new ClanGuiHolder(ClanGuiType.ROLE_ASSIGN, clan, null, safePage, null, role);
        Inventory inv = createInventory(holder, LARGE_SIZE, lang.getMessage("gui.role_assign_title")
            .replace("{role}", formatRole(role))
            .replace("{page}", String.valueOf(safePage))
            .replace("{pages}", String.valueOf(totalPages)));

        int start = (safePage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, members.size());
        for (int i = start; i < end; i++) {
            String member = members.get(i);
            inv.setItem(i - start, createMemberAssignItem(clan, member));
        }

        inv.setItem(45, createItem(Material.ARROW, lang.getMessage("gui.item_prev"), null));
        inv.setItem(49, createItem(Material.BARRIER, lang.getMessage("gui.item_back"), null));
        inv.setItem(53, createItem(Material.ARROW, lang.getMessage("gui.item_next"), null));

        fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ClanGuiHolder)) {
            return;
        }

        ClanGuiHolder holder = (ClanGuiHolder) event.getInventory().getHolder();
        Inventory top = event.getInventory();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int slot = event.getRawSlot();
        String clan = holder.getClanName();

        switch (holder.getType()) {
            case MAIN:
                handleMainClick(player, clan, slot);
                break;
            case NO_CLAN:
                handleNoClanClick(player, slot);
                break;
            case TOP_SELECT:
                handleTopSelectClick(player, slot);
                break;
            case TOP_LIST:
                handleTopListClick(player, holder, slot);
                break;
            case INFO:
                if (slot == 26) {
                    openClanMenu(player, clan);
                }
                break;
            case MEMBERS:
                handleMembersClick(player, holder, slot);
                break;
            case INVITES:
                handleInvitesClick(player, holder, clicked, slot);
                break;
            case ALLIES:
                handleAlliesClick(player, holder, clicked, slot, event.isLeftClick(), event.isRightClick());
                break;
            case BANK:
                handleBankClick(player, clan, slot);
                break;
            case EDIT:
                handleEditClick(player, clan, slot);
                break;
            case HOME:
                handleHomeClick(player, clan, slot);
                break;
            case ROLES:
                handleRolesClick(player, holder, clicked, slot, event.isRightClick());
                break;
            case ROLE_PERMS:
                handleRolePermsClick(player, holder, clicked, slot);
                break;
            case ROLE_ASSIGN:
                handleRoleAssignClick(player, holder, clicked, slot);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ClanGuiHolder)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (plugin.getChatInputManager() != null && plugin.getChatInputManager().hasPending(player)) {
            return;
        }
        if (plugin.getChatInputManager() != null) {
            plugin.getChatInputManager().clear(player);
        }
    }
    private void handleMainClick(Player player, String clan, int slot) {
        if (slot == 10) {
            openInfoMenu(player, clan);
            return;
        }
        if (slot == 11) {
            openMembersMenu(player, clan, 1);
            return;
        }
        if (slot == 12) {
            openInvitesMenu(player, clan, 1);
            return;
        }
        if (slot == 13) {
            openAlliesMenu(player, clan, 1);
            return;
        }
        if (slot == 14) {
            openBankMenu(player, clan);
            return;
        }
        if (slot == 15) {
            openEditMenu(player, clan);
            return;
        }
        if (slot == 16) {
            openHomeMenu(player, clan);
            return;
        }
        if (slot == 22) {
            if (!player.hasPermission("vanguardclans.user.top")) {
                player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.no_permission")));
                return;
            }
            openTopSelect(player);
            return;
        }
        if (slot == 23) {
            if (!player.hasPermission("vanguardclans.user.rank")) {
                player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.no_permission")));
                return;
            }
            openRolesMenu(player);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
        }
    }

    private void handleNoClanClick(Player player, int slot) {
        if (slot == 11) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_create_clan"), "clan create");
            player.closeInventory();
            return;
        }
        if (slot == 13) {
            openInvitesMenu(player, null, 1);
            return;
        }
        if (slot == 15) {
            player.performCommand("clan list");
            player.closeInventory();
            return;
        }
        if (slot == 26) {
            player.closeInventory();
        }
    }

    private void handleTopSelectClick(Player player, int slot) {
        if (slot == 10) {
            openTopList(player, TopMetric.KDA, 1);
            return;
        }
        if (slot == 12) {
            openTopList(player, TopMetric.POINTS, 1);
            return;
        }
        if (slot == 14) {
            openTopList(player, TopMetric.MONEY, 1);
            return;
        }
        if (slot == 16) {
            openTopList(player, TopMetric.MEMBERS, 1);
            return;
        }
        if (slot == 22) {
            openMainMenu(player);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
        }
    }

    private void handleTopListClick(Player player, ClanGuiHolder holder, int slot) {
        if (slot == 45) {
            openTopList(player, holder.getMetric(), holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            openTopSelect(player);
            return;
        }
        if (slot == 53) {
            openTopList(player, holder.getMetric(), holder.getPage() + 1);
        }
    }

    private void handleMembersClick(Player player, ClanGuiHolder holder, int slot) {
        if (slot == 45) {
            openMembersMenu(player, holder.getClanName(), holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            openClanMenu(player, holder.getClanName());
            return;
        }
        if (slot == 53) {
            openMembersMenu(player, holder.getClanName(), holder.getPage() + 1);
        }
    }

    private void handleInvitesClick(Player player, ClanGuiHolder holder, ItemStack clicked, int slot) {
        String clan = holder.getClanName();
        if (slot == 45) {
            openInvitesMenu(player, clan, holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            if (clan == null || clan.isEmpty()) {
                openNoClanMenu(player);
            } else {
                openClanMenu(player, clan);
            }
            return;
        }
        if (slot == 53) {
            openInvitesMenu(player, clan, holder.getPage() + 1);
            return;
        }

        String value = getValue(clicked);
        if (value == null || value.isEmpty()) {
            return;
        }

        if (clan == null || clan.isEmpty()) {
            player.performCommand("clan join " + value);
            player.closeInventory();
            return;
        }

        if (!hasClanPermission(player, clan, ClanPermission.INVITE)) {
            return;
        }
        plugin.getStorageProvider().removeClanInvite(clan, value);
        player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.invite_removed")
            .replace("{player}", value)));
        openInvitesMenu(player, clan, holder.getPage());
    }

    private void handleAlliesClick(Player player, ClanGuiHolder holder, ItemStack clicked, int slot, boolean leftClick, boolean rightClick) {
        if (slot == 45) {
            openAlliesMenu(player, holder.getClanName(), holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            openClanMenu(player, holder.getClanName());
            return;
        }
        if (slot == 53) {
            openAlliesMenu(player, holder.getClanName(), holder.getPage() + 1);
            return;
        }

        String clan = holder.getClanName();
        String target = getValue(clicked);
        String type = getRole(clicked);
        if (target == null || target.isEmpty()) {
            return;
        }

        if ("pending".equalsIgnoreCase(type)) {
            if (!hasClanPermission(player, clan, ClanPermission.ALLY)) {
                return;
            }
            if (leftClick) {
                player.performCommand("clan ally accept " + target);
                openAlliesMenu(player, clan, holder.getPage());
                return;
            }
            if (rightClick) {
                player.performCommand("clan ally decline " + target);
                openAlliesMenu(player, clan, holder.getPage());
            }
        }
    }

    private void handleBankClick(Player player, String clan, int slot) {
        if (slot == 11) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_bank_deposit"), "clan economy deposit");
            player.closeInventory();
            return;
        }
        if (slot == 15) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_bank_withdraw"), "clan economy withdraw");
            player.closeInventory();
            return;
        }
        if (slot == 26) {
            openClanMenu(player, clan);
        }
    }

    private void handleEditClick(Player player, String clan, int slot) {
        if (slot == 11) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_edit_name"), "clan edit name");
            player.closeInventory();
            return;
        }
        if (slot == 13) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_edit_tag"), "clan edit tag");
            player.closeInventory();
            return;
        }
        if (slot == 15) {
            if (!hasClanPermission(player, clan, ClanPermission.EDIT_PRIVACY)) {
                return;
            }
            String current = plugin.getStorageProvider().getClanPrivacy(clan);
            String next = "public".equalsIgnoreCase(current) ? "private" : "public";
            player.closeInventory();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getStorageProvider().setClanPrivacy(clan, next);
                    plugin.getStorageProvider().reloadCache();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.edit_privacy_success")
                            .replace("{privacy}", next)));
                        openEditMenu(player, clan);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.edit_error")))
                    );
                }
            });
            return;
        }
        if (slot == 26) {
            openClanMenu(player, clan);
        }
    }

    private void handleHomeClick(Player player, String clan, int slot) {
        if (slot == 11) {
            player.performCommand("clan home");
            player.closeInventory();
            return;
        }
        if (slot == 13) {
            player.performCommand("clan sethome");
            player.closeInventory();
            return;
        }
        if (slot == 15) {
            player.performCommand("clan delhome");
            player.closeInventory();
            return;
        }
        if (slot == 26) {
            openClanMenu(player, clan);
        }
    }

    private void handleRolesClick(Player player, ClanGuiHolder holder, ItemStack clicked, int slot, boolean rightClick) {
        if (slot == 45) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_role_create"), "clan rank create");
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            openClanMenu(player, holder.getClanName());
            return;
        }
        if (slot == 53) {
            requestChatCommand(player, lang.getMessageWithPrefix("user.prompt_role_delete"), "clan rank delete");
            player.closeInventory();
            return;
        }

        String role = getValue(clicked);
        if (role == null || role.isEmpty()) {
            return;
        }

        if (rightClick) {
            openRoleAssignMenu(player, holder.getClanName(), role, 1);
        } else {
            openRolePermsMenu(player, holder.getClanName(), role);
        }
    }

    private void handleRolePermsClick(Player player, ClanGuiHolder holder, ItemStack clicked, int slot) {
        if (slot == 49) {
            openRolesMenu(player);
            return;
        }
        if (slot == 53) {
            if (!hasClanPermission(player, holder.getClanName(), ClanPermission.RANK_MANAGE)) {
                return;
            }
            String role = holder.getRoleName();
            if (isReservedRole(role)) {
                player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.rank_cant_edit_role")));
                return;
            }
            plugin.getRoleManager().setRolePermissions(holder.getClanName(), role, Collections.<ClanPermission>emptySet());
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.rank_permissions_cleared")
                .replace("{role}", formatRole(role))));
            openRolePermsMenu(player, holder.getClanName(), role);
            return;
        }

        ClanPermission permission = getPermission(clicked);
        if (permission == null) {
            return;
        }
        if (!hasClanPermission(player, holder.getClanName(), ClanPermission.RANK_MANAGE)) {
            return;
        }
        String role = holder.getRoleName();
        if (isReservedRole(role)) {
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.rank_cant_edit_role")));
            return;
        }

        Map<String, Set<ClanPermission>> rolePerms = plugin.getRoleManager().getClanRolePermissions(holder.getClanName());
        Set<ClanPermission> perms = new HashSet<>(rolePerms.getOrDefault(role.toLowerCase(Locale.ROOT), Collections.<ClanPermission>emptySet()));
        if (perms.contains(permission)) {
            perms.remove(permission);
        } else {
            perms.add(permission);
        }
        plugin.getRoleManager().setRolePermissions(holder.getClanName(), role, perms);
        openRolePermsMenu(player, holder.getClanName(), role);
    }

    private void handleRoleAssignClick(Player player, ClanGuiHolder holder, ItemStack clicked, int slot) {
        if (slot == 45) {
            openRoleAssignMenu(player, holder.getClanName(), holder.getRoleName(), holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            openRolesMenu(player);
            return;
        }
        if (slot == 53) {
            openRoleAssignMenu(player, holder.getClanName(), holder.getRoleName(), holder.getPage() + 1);
            return;
        }

        String target = getValue(clicked);
        if (target == null || target.isEmpty()) {
            return;
        }
        if (!hasClanPermission(player, holder.getClanName(), ClanPermission.RANK_MANAGE)) {
            return;
        }
        String role = holder.getRoleName();
        if (ClanRoleManager.ROLE_LEADER.equalsIgnoreCase(role)) {
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.rank_cant_edit_leader")));
            return;
        }
        String leader = plugin.getStorageProvider().getClanLeader(holder.getClanName());
        if (leader != null && leader.equalsIgnoreCase(target)) {
            player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.rank_cant_edit_leader")));
            return;
        }

        plugin.getRoleManager().setPlayerRole(holder.getClanName(), target, role);
        player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.rank_role_set")
            .replace("{player}", target)
            .replace("{role}", formatRole(role))));
        openRoleAssignMenu(player, holder.getClanName(), role, holder.getPage());
    }
    private boolean hasClanPermission(Player player, String clanName, ClanPermission permission) {
        if (player == null || clanName == null || clanName.isEmpty()) {
            return false;
        }
        ClanRoleManager roleManager = plugin.getRoleManager();
        if (roleManager != null && roleManager.hasPermission(player, clanName, permission)) {
            return true;
        }
        player.sendMessage(MSG.color(lang.getMessageWithPrefix("user.role_no_permission")));
        return false;
    }

    private void requestChatCommand(Player player, String prompt, String commandPrefix) {
        plugin.getChatInputManager().requestInput(player, prompt, (p, input) -> {
            if (input == null || input.trim().isEmpty()) {
                return;
            }
            p.performCommand(commandPrefix + " " + input.trim());
        });
    }

    private ClanTopEntry buildClanStats(String clan) {
        int members = plugin.getStorageProvider().getClanMemberCount(clan);
        int points = plugin.getStorageProvider().getClanPoints(clan);
        double money = plugin.getStorageProvider().getClanMoney(clan);

        int kills = 0;
        int deaths = 0;
        double kdaSum = 0.0;
        int memberCount = 0;

        List<String> clanMembers = plugin.getStorageProvider().getClanMembers(clan);
        for (String member : clanMembers) {
            int playerKills = plugin.getStorageProvider().getPlayerKills(member);
            int playerDeaths = plugin.getStorageProvider().getPlayerDeaths(member);
            kills += playerKills;
            deaths += playerDeaths;
            double playerKda = playerDeaths == 0 ? playerKills : (double) playerKills / playerDeaths;
            kdaSum += playerKda;
            memberCount++;
        }

        double totalKda = deaths == 0 ? kills : (double) kills / deaths;
        double averageKda = memberCount == 0 ? 0.0 : kdaSum / memberCount;

        String coloredName = plugin.getStorageProvider().getClanColoredName(clan);
        return new ClanTopEntry(clan, coloredName, members, points, money, kills, deaths, totalKda, averageKda);
    }

    private ItemStack createTopItem(ClanTopEntry entry, int position) {
        List<String> lore = new ArrayList<>();
        lore.add(lang.getMessage("gui.top_line_members").replace("{value}", String.valueOf(entry.getMembers())));
        lore.add(lang.getMessage("gui.top_line_points").replace("{value}", String.valueOf(entry.getPoints())));
        lore.add(lang.getMessage("gui.top_line_money").replace("{value}", numberFormat.format(entry.getMoney())));
        lore.add(lang.getMessage("gui.top_line_kda_total").replace("{value}", formatDecimal(entry.getTotalKda())));
        lore.add(lang.getMessage("gui.top_line_kda_avg").replace("{value}", formatDecimal(entry.getAverageKda())));

        String name = lang.getMessage("gui.top_entry_title")
            .replace("{pos}", String.valueOf(position))
            .replace("{clan}", MSG.color(entry.getColoredName()));
        return createItem(Material.PAPER, name, lore);
    }

    private ItemStack createMemberItem(String clan, String member) {
        ClanRoleManager roleManager = plugin.getRoleManager();
        String role = roleManager != null ? roleManager.getPlayerRole(clan, member) : ClanRoleManager.ROLE_MEMBER;
        List<String> lore = new ArrayList<>();
        lore.add(lang.getMessage("gui.member_role").replace("{role}", formatRole(role)));

        int kills = plugin.getStorageProvider().getPlayerKills(member);
        int deaths = plugin.getStorageProvider().getPlayerDeaths(member);
        double kda = plugin.getStorageProvider().getKillDeathRatio(member);
        lore.add(lang.getMessage("gui.member_kills").replace("{value}", String.valueOf(kills)));
        lore.add(lang.getMessage("gui.member_deaths").replace("{value}", String.valueOf(deaths)));
        lore.add(lang.getMessage("gui.member_kda").replace("{value}", formatDecimal(kda)));

        boolean online = Bukkit.getPlayerExact(member) != null;
        String name = (online ? "&a" : "&7") + member;

        return createSkullItem(member, name, lore);
    }

    private ItemStack createMemberAssignItem(String clan, String member) {
        ClanRoleManager roleManager = plugin.getRoleManager();
        String role = roleManager != null ? roleManager.getPlayerRole(clan, member) : ClanRoleManager.ROLE_MEMBER;
        List<String> lore = new ArrayList<>();
        lore.add(lang.getMessage("gui.member_role").replace("{role}", formatRole(role)));
        lore.add(lang.getMessage("gui.role_assign_click"));

        ItemStack item = createSkullItem(member, "&e" + member, lore);
        setValue(item, member);
        return item;
    }

    private ItemStack createInviteItem(String value, boolean clanInvites) {
        List<String> lore = new ArrayList<>();
        if (clanInvites) {
            lore.add(lang.getMessage("gui.invite_click_remove"));
            ItemStack item = createSkullItem(value, "&e" + value, lore);
            setValue(item, value);
            return item;
        }
        lore.add(lang.getMessage("gui.invite_click_join"));
        ItemStack item = createItem(Material.EMERALD, "&a" + value, lore);
        setValue(item, value);
        return item;
    }

    private ItemStack createAllyItem(String clan, boolean pending) {
        List<String> lore = new ArrayList<>();
        if (pending) {
            lore.add(lang.getMessage("gui.ally_pending"));
            lore.add(lang.getMessage("gui.ally_click_accept"));
            lore.add(lang.getMessage("gui.ally_click_decline"));
            ItemStack item = createItem(Material.PAPER, "&e" + clan, lore);
            setValue(item, clan);
            setRole(item, "pending");
            return item;
        }
        lore.add(lang.getMessage("gui.ally_active"));
        ItemStack item = createItem(Material.FIREWORK_ROCKET, "&a" + clan, lore);
        setValue(item, clan);
        setRole(item, "ally");
        return item;
    }

    private ItemStack createRoleItem(String clan, String role) {
        Map<String, Set<ClanPermission>> rolePerms = plugin.getRoleManager().getClanRolePermissions(clan);
        Set<ClanPermission> perms = rolePerms.getOrDefault(role.toLowerCase(Locale.ROOT), Collections.<ClanPermission>emptySet());

        List<String> lore = new ArrayList<>();
        lore.add(lang.getMessage("gui.role_perm_count")
            .replace("{count}", String.valueOf(perms.size())));
        lore.add(lang.getMessage("gui.role_left_click"));
        lore.add(lang.getMessage("gui.role_right_click"));

        Material material = Material.NAME_TAG;
        if (ClanRoleManager.ROLE_CO_LEADER.equalsIgnoreCase(role)) {
            material = Material.DIAMOND_HELMET;
        } else if (ClanRoleManager.ROLE_MEMBER.equalsIgnoreCase(role)) {
            material = Material.LEATHER_HELMET;
        }

        ItemStack item = createItem(material, "&e" + formatRole(role), lore);
        setValue(item, role);
        return item;
    }

    private ItemStack createPermissionItem(ClanPermission permission, boolean enabled) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        List<String> lore = new ArrayList<>();
        lore.add(lang.getMessage("gui.permission_key").replace("{key}", permission.getKey()));
        lore.add(enabled ? lang.getMessage("gui.permission_enabled") : lang.getMessage("gui.permission_disabled"));
        ItemStack item = createItem(material, "&e" + permission.getLabel(), lore);
        setValue(item, permission.getKey());
        return item;
    }

    private ClanPermission getPermission(ItemStack item) {
        String key = getValue(item);
        if (key == null) {
            return null;
        }
        return ClanPermission.fromKey(key).orElse(null);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MSG.color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(MSG.colorList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSkullItem(String owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = item.getItemMeta();
        if (baseMeta instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) baseMeta;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
            meta.setOwningPlayer(offline);
            meta.setDisplayName(MSG.color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(MSG.colorList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Inventory createInventory(ClanGuiHolder holder, int size, String title) {
        Inventory inv = Bukkit.createInventory(holder, size, MSG.color(title));
        holder.setInventory(inv);
        return inv;
    }

    private void fill(Inventory inv) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private int getTotalPages(int totalEntries, int pageSize) {
        if (totalEntries <= 0) {
            return 1;
        }
        return (int) Math.ceil(totalEntries / (double) pageSize);
    }

    private int clampPage(int page, int maxPages) {
        if (page < 1) {
            return 1;
        }
        return Math.min(page, maxPages);
    }

    private String formatDecimal(double value) {
        return numberFormat.format(value);
    }

    private String resolveClanName(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return clanName;
        }
        for (String stored : plugin.getStorageProvider().getAllClans()) {
            if (stored.equalsIgnoreCase(clanName)) {
                return stored;
            }
        }
        return clanName;
    }

    private String safeValue(String value) {
        return value == null ? "N/A" : value;
    }

    private String formatRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ClanRoleManager.ROLE_MEMBER;
        }
        String[] parts = role.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
            builder.append(" ");
        }
        return builder.toString().trim();
    }

    private boolean isReservedRole(String role) {
        if (role == null) {
            return false;
        }
        return ClanRoleManager.ROLE_LEADER.equalsIgnoreCase(role)
            || ClanRoleManager.ROLE_CO_LEADER.equalsIgnoreCase(role);
    }

    private void setValue(ItemStack item, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(valueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    private void setRole(ItemStack item, String role) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(roleKey, PersistentDataType.STRING, role);
        item.setItemMeta(meta);
    }

    private String getValue(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
    }

    private String getRole(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
    }
}
