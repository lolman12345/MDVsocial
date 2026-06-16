package com.mdvcraft.mdvsocial;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MDVSocialPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<String, TitleDef> titles = new HashMap<>();
    private final Map<String, RankDef> ranks = new HashMap<>();
    private final Map<String, CustomMenuDef> customMenus = new HashMap<>();
    private final List<Integer> listSlots = new ArrayList<>();

    private File dataFile;
    private YamlConfiguration data;
    private Economy economy;

    private org.bukkit.NamespacedKey keyAction;
    private org.bukkit.NamespacedKey keyTitle;
    private org.bukkit.NamespacedKey keyMenu;
    private org.bukkit.NamespacedKey keyTargetMenu;
    private org.bukkit.NamespacedKey keyCommands;
    private org.bukkit.NamespacedKey keyCloseOnClick;

    @Override
    public void onEnable() {
        keyAction = new org.bukkit.NamespacedKey(this, "action");
        keyTitle = new org.bukkit.NamespacedKey(this, "title_id");
        keyMenu = new org.bukkit.NamespacedKey(this, "menu");
        keyTargetMenu = new org.bukkit.NamespacedKey(this, "target_menu");
        keyCommands = new org.bukkit.NamespacedKey(this, "commands");
        keyCloseOnClick = new org.bukkit.NamespacedKey(this, "close_on_click");

        saveDefaultConfig();
        loadAll();
        setupEconomy();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("social").setExecutor(this);
        getCommand("titulos").setExecutor(this);
        getCommand("mdvsocial").setExecutor(this);
        getCommand("mdvsocial").setTabCompleter(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MDVSocialExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado. Placeholders registrados.");
        }

        getLogger().info("MDVSocial 1.1.0 habilitado.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadAll() {
        reloadConfig();
        loadData();
        loadTitles();
        loadRanks();
        loadListSlots();
        ensureDefaultMenus();
        loadCustomMenus();
    }

    private void loadData() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        dataFile = new File(getDataFolder(), "player-data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("No se pudo crear player-data.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("No se pudo guardar player-data.yml: " + e.getMessage());
        }
    }

    private void loadTitles() {
        titles.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("titles");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null) continue;
            String cleanId = normalize(id);
            TitleDef def = new TitleDef(
                    cleanId,
                    t.getString("display", cleanId),
                    t.getString("prefix", ""),
                    t.getString("material", "NAME_TAG"),
                    t.getString("head-owner", ""),
                    t.getBoolean("purchasable", false),
                    t.getDouble("price", 0),
                    t.getString("unlock-permission", ""),
                    t.getStringList("lore")
            );
            titles.put(cleanId, def);
        }
    }

    private void loadRanks() {
        ranks.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("ranks");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(id);
            if (r == null) continue;
            String cleanId = normalize(id);
            RankDef def = new RankDef(
                    cleanId,
                    r.getString("display", cleanId),
                    r.getString("material", "PAPER"),
                    r.getString("permission", ""),
                    r.getStringList("lore")
            );
            ranks.put(cleanId, def);
        }
    }

    private void loadListSlots() {
        listSlots.clear();
        List<Integer> configSlots = getConfig().getIntegerList("list-slots");
        if (configSlots.isEmpty()) {
            listSlots.addAll(Arrays.asList(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43));
        } else {
            listSlots.addAll(configSlots);
        }
    }

    private boolean setupEconomy() {
        if (!getConfig().getBoolean("settings.use-vault-economy", true)) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("social")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            if (!player.hasPermission("mdvsocial.use")) {
                msg(player, "no-permission");
                return true;
            }
            openSocialStart(player);
            return true;
        }

        if (cmd.equals("titulos")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            if (!player.hasPermission("mdvsocial.use")) {
                msg(player, "no-permission");
                return true;
            }
            handlePlayerTitleCommand(player, args);
            return true;
        }

        if (cmd.equals("mdvsocial")) {
            return handleAdminCommand(sender, args);
        }
        return false;
    }

    private void handlePlayerTitleCommand(Player player, String[] args) {
        if (args.length == 0) {
            openTitlesHome(player);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("quitar") || sub.equals("clear") || sub.equals("remover")) {
            clearActiveTitle(player);
            return;
        }
        if ((sub.equals("poner") || sub.equals("set") || sub.equals("equipar")) && args.length >= 2) {
            equipTitle(player, normalize(args[1]));
            return;
        }
        openTitlesHome(player);
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&6MDVSocial &7comandos: reload, title"));
            return true;
        }
        if (!sender.hasPermission("mdvsocial.admin")) {
            msg(sender, "no-permission");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            loadAll();
            setupEconomy();
            msg(sender, "reloaded");
            return true;
        }

        if (!sub.equals("title")) {
            sender.sendMessage(color("&cUso: /mdvsocial reload | /mdvsocial title ..."));
            return true;
        }

        if (args.length < 2) {
            sendTitleHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("give") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = normalize(args[3]);
            if (!titles.containsKey(titleId)) {
                msg(sender, "title-not-found");
                return true;
            }
            giveTitle(target.getUniqueId(), target.getName(), titleId);
            msg(sender, "given-title");
            if (target.isOnline()) msg(target.getPlayer(), "given-title");
            return true;
        }

        if (action.equals("remove") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = normalize(args[3]);
            removeTitle(target.getUniqueId(), titleId);
            msg(sender, "removed-title");
            return true;
        }

        if (action.equals("set") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = normalize(args[3]);
            if (!titles.containsKey(titleId)) {
                msg(sender, "title-not-found");
                return true;
            }
            giveTitle(target.getUniqueId(), target.getName(), titleId);
            setActiveTitle(target.getUniqueId(), titleId);
            if (target.isOnline()) runEquipCommands(target.getPlayer(), titleId);
            msg(sender, "given-title");
            return true;
        }

        if (action.equals("clear") && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            setActiveTitle(target.getUniqueId(), "");
            if (target.isOnline()) runClearCommands(target.getPlayer());
            msg(sender, "removed-title");
            return true;
        }

        if (action.equals("give-radius") && args.length >= 4) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            double radius = parseDouble(args[2], -1);
            String titleId = normalize(args[3]);
            if (radius <= 0 || !titles.containsKey(titleId)) {
                sendTitleHelp(sender);
                return true;
            }
            int amount = giveNear(player.getLocation(), radius, titleId);
            msg(sender, "boss-radius-given", Map.of("amount", String.valueOf(amount)));
            return true;
        }

        if (action.equals("give-near") && args.length >= 8) {
            World world = Bukkit.getWorld(args[2]);
            double x = parseDouble(args[3], Double.NaN);
            double y = parseDouble(args[4], Double.NaN);
            double z = parseDouble(args[5], Double.NaN);
            double radius = parseDouble(args[6], -1);
            String titleId = normalize(args[7]);
            if (world == null || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || radius <= 0 || !titles.containsKey(titleId)) {
                sendTitleHelp(sender);
                return true;
            }
            int amount = giveNear(new Location(world, x, y, z), radius, titleId);
            msg(sender, "boss-radius-given", Map.of("amount", String.valueOf(amount)));
            return true;
        }

        sendTitleHelp(sender);
        return true;
    }


    private void ensureDefaultMenus() {
        File folder = new File(getDataFolder(), "Menus");
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("No se pudo crear la carpeta Menus.");
            return;
        }
        File main = new File(folder, "main.yml");
        if (!main.exists()) {
            try {
                Files.writeString(main.toPath(), defaultMainMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/main.yml: " + e.getMessage());
            }
        }
        File clan = new File(folder, "clan.yml");
        if (!clan.exists()) {
            try {
                Files.writeString(clan.toPath(), defaultClanMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/clan.yml: " + e.getMessage());
            }
        }
        File ayuda = new File(folder, "ayuda.yml");
        if (!ayuda.exists()) {
            try {
                Files.writeString(ayuda.toPath(), defaultAyudaMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/ayuda.yml: " + e.getMessage());
            }
        }
    }

    private String defaultMainMenuYaml() {
        return """
# ==========================================================
# MDVSocial - Menu modular principal
# Acciones disponibles:
# OPEN_MENU / COMMAND_PLAYER / BACK / CLOSE / PREVIOUS_PAGE / NEXT_PAGE / OPEN_TITLES
# ==========================================================
title: '&8MDVSocial'
size: 27
items:
  titulos:
    slot: 11
    material: NAME_TAG
    name: '&eTitulos y rangos'
    lore:
      - '&7Compra, equipa o revisa titulos.'
      - '&eClick para abrir.'
    action: OPEN_TITLES

  clan:
    slot: 13
    material: SHIELD
    name: '&aClan'
    lore:
      - '&7Abre el menu de clan.'
      - '&eClick para abrir.'
    action: OPEN_MENU
    target-menu: clan

  ayuda:
    slot: 15
    material: BOOK
    name: '&bAyuda social'
    lore:
      - '&7Comandos sociales utiles.'
      - '&eClick para abrir.'
    action: OPEN_MENU
    target-menu: ayuda

  cerrar:
    slot: 26
    material: BARRIER
    name: '&cCerrar'
    action: CLOSE
""";
    }

    private String defaultClanMenuYaml() {
        return """
title: '&8Clan'
size: 27
items:
  info:
    slot: 11
    material: PAPER
    name: '&eInformacion del clan'
    lore:
      - '&7Ejecuta /clan info como jugador.'
    action: COMMAND_PLAYER
    commands:
      - 'clan info'

  crear:
    slot: 13
    material: EMERALD
    name: '&aCrear clan'
    lore:
      - '&7Ejecuta /clan create como jugador.'
      - '&8Despues puedes cambiarlo por otro submenu.'
    action: COMMAND_PLAYER
    commands:
      - 'clan create'

  volver:
    slot: 18
    material: ARROW
    name: '&eVolver'
    action: BACK

  cerrar:
    slot: 26
    material: BARRIER
    name: '&cCerrar'
    action: CLOSE
""";
    }

    private String defaultAyudaMenuYaml() {
        return """
title: '&8Ayuda social'
size: 27
items:
  amigos:
    slot: 10
    material: PLAYER_HEAD
    head-owner: '{player}'
    name: '&bAmigos'
    lore:
      - '&7Ejecuta /friends como jugador.'
    action: COMMAND_PLAYER
    commands:
      - 'friends'

  correo:
    slot: 12
    material: WRITABLE_BOOK
    name: '&eCorreo'
    lore:
      - '&7Ejecuta /mail read como jugador.'
    action: COMMAND_PLAYER
    commands:
      - 'mail read'

  ejemplo_paginas:
    slot: 14
    material: MAP
    name: '&dEjemplo de paginas'
    lore:
      - '&7Este mismo menu puede tener pages: 1, 2, 3...'
      - '&7Usa NEXT_PAGE y PREVIOUS_PAGE.'

  volver:
    slot: 18
    material: ARROW
    name: '&eVolver'
    action: BACK

  cerrar:
    slot: 26
    material: BARRIER
    name: '&cCerrar'
    action: CLOSE
""";
    }

    private void loadCustomMenus() {
        customMenus.clear();
        File folder = new File(getDataFolder(), "Menus");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null) return;
        for (File file : files) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String id = normalize(dot > 0 ? fileName.substring(0, dot) : fileName);
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                CustomMenuDef def = parseCustomMenu(id, yaml);
                customMenus.put(id, def);
            } catch (Exception e) {
                getLogger().warning("No se pudo cargar menu " + file.getName() + ": " + e.getMessage());
            }
        }
        getLogger().info("Menus modulares cargados: " + customMenus.size());
    }

    private CustomMenuDef parseCustomMenu(String id, YamlConfiguration yaml) {
        String title = yaml.getString("title", "&8" + id);
        int size = normalizeMenuSize(yaml.getInt("size", 27));
        CustomMenuDef def = new CustomMenuDef(id, title, size);

        ConfigurationSection pagesSec = yaml.getConfigurationSection("pages");
        if (pagesSec != null) {
            for (String pageKey : pagesSec.getKeys(false)) {
                int page = parsePage(pageKey);
                ConfigurationSection items = pagesSec.getConfigurationSection(pageKey + ".items");
                if (items == null) items = pagesSec.getConfigurationSection(pageKey);
                loadCustomMenuItems(def, page, items);
            }
        } else {
            loadCustomMenuItems(def, 1, yaml.getConfigurationSection("items"));
        }
        if (def.pages.isEmpty()) def.pages.put(1, new ArrayList<>());
        return def;
    }

    private int parsePage(String key) {
        try { return Math.max(1, Integer.parseInt(key)); } catch (Exception e) { return 1; }
    }

    private int normalizeMenuSize(int size) {
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return size;
    }

    private void loadCustomMenuItems(CustomMenuDef def, int page, ConfigurationSection itemsSec) {
        List<CustomMenuItem> items = def.pages.computeIfAbsent(page, k -> new ArrayList<>());
        if (itemsSec == null) return;
        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection sec = itemsSec.getConfigurationSection(key);
            if (sec == null) continue;
            int slot = sec.getInt("slot", -1);
            if (slot < 0 || slot >= def.size) {
                getLogger().warning("Slot invalido en menu " + def.id + " item " + key + ": " + slot);
                continue;
            }
            String action = normalizeAction(sec.getString("action", ""));
            String target = normalize(sec.getString("target-menu", sec.getString("menu", "")));
            List<String> commands = new ArrayList<>(sec.getStringList("commands"));
            String singleCommand = sec.getString("command", "");
            if (commands.isEmpty() && singleCommand != null && !singleCommand.isBlank()) commands.add(singleCommand);
            CustomMenuItem item = new CustomMenuItem(
                    key,
                    slot,
                    sec.getString("material", "PAPER"),
                    sec.getInt("amount", 1),
                    sec.getString("name", sec.getString("display", "")),
                    sec.getStringList("lore"),
                    sec.getString("head-owner", ""),
                    action,
                    target,
                    commands,
                    sec.getBoolean("close-on-click", true)
            );
            items.add(item);
        }
    }

    private String normalizeAction(String action) {
        if (action == null) return "";
        String a = action.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (a) {
            case "COMMAND", "RUN_COMMAND", "PLAYER_COMMAND", "COMMAND_PLAYER" -> "COMMAND_PLAYER";
            case "OPEN", "OPENMENU", "OPEN_MENU" -> "OPEN_MENU";
            case "PREV_PAGE", "PREVIOUS", "PREVIOUS_PAGE" -> "PREVIOUS_PAGE";
            case "NEXT", "NEXT_PAGE" -> "NEXT_PAGE";
            case "OPEN_TITLE", "OPEN_TITLES", "TITLES" -> "OPEN_TITLES";
            default -> a;
        };
    }

    private void openSocialStart(Player player) {
        String start = normalize(getConfig().getString("settings.start-menu", "main"));
        if (customMenus.containsKey(start)) openCustomMenu(player, start, 1, "", 1);
        else if (customMenus.containsKey("main")) openCustomMenu(player, "main", 1, "", 1);
        else openMain(player);
    }

    private void openCustomMenu(Player player, String menuId, int page, String previousMenu, int previousPage) {
        menuId = normalize(menuId);
        CustomMenuDef def = customMenus.get(menuId);
        if (def == null) {
            player.sendMessage(color(getPrefix() + "&cEse menu no existe: &e" + menuId));
            return;
        }
        int maxPage = def.maxPage();
        page = Math.max(1, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder("CUSTOM_MENU", page, menuId, previousMenu == null ? "" : normalize(previousMenu), previousPage <= 0 ? 1 : previousPage);
        Inventory inv = Bukkit.createInventory(holder, def.size, color(def.title.replace("{page}", String.valueOf(page)).replace("{max_page}", String.valueOf(maxPage))));
        holder.inventory = inv;
        fill(inv);

        List<CustomMenuItem> items = def.pages.getOrDefault(page, Collections.emptyList());
        for (CustomMenuItem menuItem : items) {
            if (menuItem.slot >= 0 && menuItem.slot < inv.getSize()) inv.setItem(menuItem.slot, customMenuItemStack(player, menuItem));
        }
        player.openInventory(inv);
    }

    private ItemStack customMenuItemStack(Player player, CustomMenuItem def) {
        Material mat = Material.matchMaterial(def.material.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.PAPER;
        int amount = Math.max(1, Math.min(64, def.amount));
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta skull && def.headOwner != null && !def.headOwner.isBlank()) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(def.headOwner.replace("{player}", player.getName()));
            skull.setOwningPlayer(owner);
            meta = skull;
        }
        if (meta == null) return item;
        if (def.name != null && !def.name.isBlank()) meta.setDisplayName(color(applyPlayerPlaceholders(def.name, player)));
        List<String> lore = new ArrayList<>();
        for (String line : def.lore) lore.add(color(applyPlayerPlaceholders(line, player)));
        if (!lore.isEmpty()) meta.setLore(lore);
        if (def.action != null && !def.action.isBlank()) meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, def.action);
        if (def.targetMenu != null && !def.targetMenu.isBlank()) meta.getPersistentDataContainer().set(keyTargetMenu, PersistentDataType.STRING, def.targetMenu);
        if (!def.commands.isEmpty()) meta.getPersistentDataContainer().set(keyCommands, PersistentDataType.STRING, String.join("\n", def.commands));
        meta.getPersistentDataContainer().set(keyCloseOnClick, PersistentDataType.STRING, String.valueOf(def.closeOnClick));
        item.setItemMeta(meta);
        return item;
    }

    private String applyPlayerPlaceholders(String input, Player player) {
        if (input == null) return "";
        return input.replace("{player}", player.getName());
    }

    private void sendTitleHelp(CommandSender sender) {
        sender.sendMessage(color("&6MDVSocial title:"));
        sender.sendMessage(color("&e/mdvsocial title give <jugador> <titulo>"));
        sender.sendMessage(color("&e/mdvsocial title remove <jugador> <titulo>"));
        sender.sendMessage(color("&e/mdvsocial title set <jugador> <titulo>"));
        sender.sendMessage(color("&e/mdvsocial title clear <jugador>"));
        sender.sendMessage(color("&e/mdvsocial title give-radius <radio> <titulo> &7(jugador)"));
        sender.sendMessage(color("&e/mdvsocial title give-near <world> <x> <y> <z> <radio> <titulo>"));
    }

    private int giveNear(Location center, double radius, String titleId) {
        int count = 0;
        double radiusSq = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= radiusSq) {
                giveTitle(p.getUniqueId(), p.getName(), titleId);
                msg(p, "given-title");
                count++;
            }
        }
        saveData();
        return count;
    }

    private void openMain(Player player) {
        Inventory inv = createMenu("MAIN", getMenuSize("main"), getMenuTitle("main"), 0);
        fill(inv);
        placeConfiguredMainButton(inv, "main-menu.titles", "OPEN_TITLES");
        placeConfiguredMainButton(inv, "main-menu.clan", "COMMANDS");
        placeConfiguredMainButton(inv, "main-menu.social", "COMMANDS");
        inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private void openTitlesHome(Player player) {
        Inventory inv = createMenu("TITLES_HOME", getMenuSize("titles"), getMenuTitle("titles"), 0);
        fill(inv);
        inv.setItem(getConfig().getInt("titles-menu.my-titles.slot", 10), simpleItemFromPath("titles-menu.my-titles", "OPEN_MY_TITLES"));
        inv.setItem(getConfig().getInt("titles-menu.shop.slot", 12), simpleItemFromPath("titles-menu.shop", "OPEN_SHOP"));
        inv.setItem(getConfig().getInt("titles-menu.locked.slot", 14), simpleItemFromPath("titles-menu.locked", "OPEN_LOCKED"));
        inv.setItem(getConfig().getInt("titles-menu.ranks.slot", 16), simpleItemFromPath("titles-menu.ranks", "OPEN_RANKS"));
        int clearSlot = getConfig().getInt("titles-menu.clear.slot", 22);
        inv.setItem(clearSlot, navItem("clear-title", "CLEAR_TITLE"));
        inv.setItem(inv.getSize() - 5, navItem("back", "OPEN_MAIN"));
        inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private void openTitleList(Player player, String type, int page) {
        String menuKey = switch (type) {
            case "MY_TITLES" -> "my-titles";
            case "SHOP" -> "title-shop";
            case "LOCKED" -> "locked-titles";
            default -> "my-titles";
        };
        Inventory inv = createMenu(type, getMenuSize(menuKey), getMenuTitle(menuKey), page);
        fill(inv);

        List<TitleDef> list = filteredTitles(player, type);
        list.sort(Comparator.comparing(t -> stripColor(t.display)));
        int perPage = listSlots.size();
        int maxPage = Math.max(0, (int) Math.ceil(list.size() / (double) perPage) - 1);
        page = Math.max(0, Math.min(page, maxPage));
        ((MenuHolder) inv.getHolder()).page = page;

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= list.size()) break;
            int slot = listSlots.get(i);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, titleItem(player, list.get(index), type));
            }
        }

        if (page > 0) inv.setItem(inv.getSize() - 9, navItem("previous-page", "PREV_PAGE"));
        inv.setItem(inv.getSize() - 5, navItem("back", "OPEN_TITLES_HOME"));
        if (page < maxPage) inv.setItem(inv.getSize() - 1, navItem("next-page", "NEXT_PAGE"));
        else inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private void openRanks(Player player, int page) {
        Inventory inv = createMenu("RANKS", getMenuSize("ranks"), getMenuTitle("ranks"), page);
        fill(inv);
        List<RankDef> list = new ArrayList<>(ranks.values());
        list.sort(Comparator.comparing(r -> stripColor(r.display)));
        int perPage = listSlots.size();
        int maxPage = Math.max(0, (int) Math.ceil(list.size() / (double) perPage) - 1);
        page = Math.max(0, Math.min(page, maxPage));
        ((MenuHolder) inv.getHolder()).page = page;

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= list.size()) break;
            int slot = listSlots.get(i);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, rankItem(player, list.get(index)));
        }
        if (page > 0) inv.setItem(inv.getSize() - 9, navItem("previous-page", "PREV_PAGE"));
        inv.setItem(inv.getSize() - 5, navItem("back", "OPEN_TITLES_HOME"));
        if (page < maxPage) inv.setItem(inv.getSize() - 1, navItem("next-page", "NEXT_PAGE"));
        else inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private Inventory createMenu(String type, int size, String title, int page) {
        MenuHolder holder = new MenuHolder(type, page);
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.inventory = inv;
        return inv;
    }

    private int getMenuSize(String key) {
        int size = getConfig().getInt("menus." + key + ".size", 54);
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return size;
    }

    private String getMenuTitle(String key) {
        return getConfig().getString("menus." + key + ".title", "&8MDVSocial");
    }

    private void fill(Inventory inv) {
        ItemStack filler = itemFromSection(getConfig().getConfigurationSection("items.filler"), "", null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void placeConfiguredMainButton(Inventory inv, String path, String action) {
        int slot = getConfig().getInt(path + ".slot", -1);
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, simpleItemFromPath(path, action));
    }

    private ItemStack simpleItemFromPath(String path, String action) {
        ConfigurationSection sec = getConfig().getConfigurationSection(path);
        ItemStack item = itemFromSection(sec, action, null);
        if (action.equals("COMMANDS") && sec != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(keyMenu, PersistentDataType.STRING, path);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navItem(String itemKey, String action) {
        return itemFromSection(getConfig().getConfigurationSection("items." + itemKey), action, null);
    }

    private ItemStack itemFromSection(ConfigurationSection sec, String action, String titleId) {
        String matName = sec != null ? sec.getString("material", "PAPER") : "PAPER";
        Material mat = Material.matchMaterial(matName == null ? "PAPER" : matName.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (sec != null) {
            String name = sec.getString("name", sec.getString("display", ""));
            if (name != null && !name.isEmpty()) meta.setDisplayName(color(name));
            List<String> lore = sec.getStringList("lore").stream().map(this::color).collect(Collectors.toList());
            if (!lore.isEmpty()) meta.setLore(lore);
        }
        if (action != null && !action.isEmpty()) meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (titleId != null && !titleId.isEmpty()) meta.getPersistentDataContainer().set(keyTitle, PersistentDataType.STRING, titleId);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack titleItem(Player player, TitleDef title, String menuType) {
        Material mat = Material.matchMaterial(title.material.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.NAME_TAG;
        ItemStack item = new ItemStack(mat);

        if (mat == Material.PLAYER_HEAD && title.headOwner != null && !title.headOwner.isBlank()) {
            SkullMeta skull = (SkullMeta) item.getItemMeta();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(title.headOwner.replace("{player}", player.getName()));
            skull.setOwningPlayer(owner);
            item.setItemMeta(skull);
        }

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(title.display));
        List<String> lore = new ArrayList<>();
        if (!title.lore.isEmpty()) {
            for (String line : title.lore) lore.add(color(line));
        }
        lore.add("");
        boolean owned = hasTitle(player, title.id);
        if (menuType.equals("MY_TITLES")) {
            String active = getActiveTitleId(player.getUniqueId());
            lore.add(color(active.equals(title.id) ? "&aEstado: equipado" : "&eClick para equipar."));
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "EQUIP_TITLE");
        } else if (menuType.equals("SHOP")) {
            lore.add(color("&ePrecio: &6" + formatPrice(title.price) + " monedas"));
            lore.add(color("&eClick para comprar."));
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "BUY_TITLE");
        } else {
            lore.add(color(owned ? "&aDesbloqueado" : "&cBloqueado"));
            if (title.unlockPermission != null && !title.unlockPermission.isBlank()) {
                lore.add(color("&7Requiere: &e" + title.unlockPermission));
            }
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "LOCKED_TITLE");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keyTitle, PersistentDataType.STRING, title.id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rankItem(Player player, RankDef rank) {
        Material mat = Material.matchMaterial(rank.material.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        boolean owned = rank.permission == null || rank.permission.isBlank() || player.hasPermission(rank.permission);
        meta.setDisplayName(color(rank.display));
        List<String> lore = new ArrayList<>();
        for (String line : rank.lore) {
            lore.add(color(line.replace("{status}", owned ? "&aObtenido" : "&cNo obtenido")));
        }
        if (lore.isEmpty()) lore.add(color(owned ? "&aObtenido" : "&cNo obtenido"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<TitleDef> filteredTitles(Player player, String type) {
        List<TitleDef> out = new ArrayList<>();
        for (TitleDef title : titles.values()) {
            boolean owned = hasTitle(player, title.id);
            if (type.equals("MY_TITLES") && owned) out.add(title);
            else if (type.equals("SHOP") && title.purchasable && !owned) out.add(title);
            else if (type.equals("LOCKED") && !owned && !title.purchasable) out.add(title);
        }
        return out;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(keyAction, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;

        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "OPEN_MAIN" -> openSocialStart(player);
            case "OPEN_TITLES", "OPEN_TITLES_HOME" -> openTitlesHome(player);
            case "OPEN_MY_TITLES" -> openTitleList(player, "MY_TITLES", 0);
            case "OPEN_SHOP" -> openTitleList(player, "SHOP", 0);
            case "OPEN_LOCKED" -> openTitleList(player, "LOCKED", 0);
            case "OPEN_RANKS" -> openRanks(player, 0);
            case "OPEN_MENU" -> {
                String target = pdc.get(keyTargetMenu, PersistentDataType.STRING);
                openCustomMenu(player, target, 1, holder.menuId, holder.page);
            }
            case "BACK" -> {
                if (holder.previousMenu != null && !holder.previousMenu.isBlank()) openCustomMenu(player, holder.previousMenu, holder.previousPage, "", 1);
                else openSocialStart(player);
            }
            case "COMMAND_PLAYER" -> runPlayerCommandsFromPdc(player, pdc);
            case "CLEAR_TITLE" -> {
                clearActiveTitle(player);
                openTitlesHome(player);
            }
            case "PREVIOUS_PAGE", "PREV_PAGE" -> {
                if (holder.type.equals("CUSTOM_MENU")) openCustomMenu(player, holder.menuId, holder.page - 1, holder.previousMenu, holder.previousPage);
                else openSamePagedMenu(player, holder.type, holder.page - 1);
            }
            case "NEXT_PAGE" -> {
                if (holder.type.equals("CUSTOM_MENU")) openCustomMenu(player, holder.menuId, holder.page + 1, holder.previousMenu, holder.previousPage);
                else openSamePagedMenu(player, holder.type, holder.page + 1);
            }
            case "EQUIP_TITLE" -> {
                String titleId = pdc.get(keyTitle, PersistentDataType.STRING);
                equipTitle(player, titleId);
                openTitleList(player, "MY_TITLES", holder.page);
            }
            case "BUY_TITLE" -> {
                String titleId = pdc.get(keyTitle, PersistentDataType.STRING);
                buyTitle(player, titleId);
                openTitleList(player, "SHOP", holder.page);
            }
            case "LOCKED_TITLE" -> msg(player, "title-locked");
            case "COMMANDS" -> runConfiguredCommands(player, pdc.get(keyMenu, PersistentDataType.STRING));
            default -> { }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // reservado para futuras sesiones de menu
    }

    private void openSamePagedMenu(Player player, String type, int page) {
        if (type.equals("RANKS")) openRanks(player, page);
        else if (type.equals("MY_TITLES") || type.equals("SHOP") || type.equals("LOCKED")) openTitleList(player, type, page);
    }


    private void runPlayerCommandsFromPdc(Player player, PersistentDataContainer pdc) {
        String raw = pdc.get(keyCommands, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return;
        String close = pdc.get(keyCloseOnClick, PersistentDataType.STRING);
        if (close == null || Boolean.parseBoolean(close)) player.closeInventory();
        for (String line : raw.split("\\n")) {
            String cmd = applyPlayerPlaceholders(line, player).trim();
            if (cmd.isBlank()) continue;
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }

    private void runConfiguredCommands(Player player, String path) {
        if (path == null || path.isBlank()) return;
        List<String> commands = getConfig().getStringList(path + ".commands");
        player.closeInventory();
        for (String raw : commands) {
            String cmd = raw.replace("{player}", player.getName());
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }

    private void buyTitle(Player player, String titleId) {
        titleId = normalize(titleId);
        TitleDef title = titles.get(titleId);
        if (title == null) {
            msg(player, "title-not-found");
            return;
        }
        if (hasTitle(player, titleId)) {
            msg(player, "already-owned");
            return;
        }
        if (!title.purchasable) {
            msg(player, "title-locked");
            return;
        }
        if (getConfig().getBoolean("settings.use-vault-economy", true)) {
            if (economy == null) {
                player.sendMessage(color(getPrefix() + "&cVault/Economy no esta disponible."));
                return;
            }
            if (economy.getBalance(player) < title.price) {
                msg(player, "not-enough-money", Map.of("price", formatPrice(title.price)));
                return;
            }
            economy.withdrawPlayer(player, title.price);
        }
        giveTitle(player.getUniqueId(), player.getName(), titleId);
        saveData();
        msg(player, "title-bought", Map.of("title", color(title.display)));
    }

    private void equipTitle(Player player, String titleId) {
        titleId = normalize(titleId);
        TitleDef title = titles.get(titleId);
        if (title == null) {
            msg(player, "title-not-found");
            return;
        }
        if (!hasTitle(player, titleId)) {
            msg(player, "title-locked");
            return;
        }
        setActiveTitle(player.getUniqueId(), titleId);
        saveData();
        runEquipCommands(player, titleId);
        msg(player, "title-equipped", Map.of("title", color(title.display)));
    }

    private void clearActiveTitle(Player player) {
        setActiveTitle(player.getUniqueId(), "");
        saveData();
        runClearCommands(player);
        msg(player, "title-cleared");
    }

    private void runEquipCommands(Player player, String titleId) {
        TitleDef title = titles.get(normalize(titleId));
        if (title == null) return;
        for (String raw : getConfig().getStringList("settings.commands-on-title-equip")) {
            String cmd = raw
                    .replace("{player}", player.getName())
                    .replace("{title_id}", title.id)
                    .replace("{title_display}", color(title.display))
                    .replace("{title_prefix}", color(title.prefix));
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private void runClearCommands(Player player) {
        for (String raw : getConfig().getStringList("settings.commands-on-title-clear")) {
            String cmd = raw.replace("{player}", player.getName());
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    public boolean hasTitle(Player player, String titleId) {
        titleId = normalize(titleId);
        if (player.hasPermission("mdvsocial.admin")) return true;
        TitleDef title = titles.get(titleId);
        if (title == null) return false;
        if (getUnlockedTitles(player.getUniqueId()).contains(titleId)) return true;
        return title.unlockPermission != null && !title.unlockPermission.isBlank() && player.hasPermission(title.unlockPermission);
    }

    private void giveTitle(UUID uuid, String name, String titleId) {
        titleId = normalize(titleId);
        Set<String> set = getUnlockedTitles(uuid);
        set.add(titleId);
        data.set(path(uuid, "unlocked"), new ArrayList<>(set));
        if (name != null) data.set(path(uuid, "last-name"), name);
        saveData();
    }

    private void removeTitle(UUID uuid, String titleId) {
        titleId = normalize(titleId);
        Set<String> set = getUnlockedTitles(uuid);
        set.remove(titleId);
        data.set(path(uuid, "unlocked"), new ArrayList<>(set));
        if (getActiveTitleId(uuid).equals(titleId)) data.set(path(uuid, "active"), "");
        saveData();
    }

    private Set<String> getUnlockedTitles(UUID uuid) {
        List<String> list = data.getStringList(path(uuid, "unlocked"));
        return list.stream().map(this::normalize).collect(Collectors.toCollection(HashSet::new));
    }

    public String getActiveTitleId(UUID uuid) {
        return normalize(data.getString(path(uuid, "active"), ""));
    }

    private void setActiveTitle(UUID uuid, String titleId) {
        data.set(path(uuid, "active"), normalize(titleId));
    }

    public TitleDef getActiveTitle(Player player) {
        String id = getActiveTitleId(player.getUniqueId());
        if (id.isBlank()) return null;
        TitleDef title = titles.get(id);
        if (title == null) return null;
        if (getConfig().getBoolean("settings.hide-invalid-active-title", true) && !hasTitle(player, id)) return null;
        return title;
    }

    public int countUnlocked(UUID uuid) {
        return getUnlockedTitles(uuid).size();
    }

    private String path(UUID uuid, String child) {
        return "players." + uuid + "." + child;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String stripColor(String s) {
        return ChatColor.stripColor(color(s));
    }

    private String getPrefix() {
        return color(getConfig().getString("messages.prefix", "&6[MDVSocial] &r"));
    }

    private void msg(CommandSender sender, String key) {
        msg(sender, key, Collections.emptyMap());
    }

    private void msg(CommandSender sender, String key, Map<String, String> replacements) {
        String raw = getConfig().getString("messages." + key, "&cMensaje faltante: " + key);
        for (Map.Entry<String, String> e : replacements.entrySet()) raw = raw.replace("{" + e.getKey() + "}", e.getValue());
        sender.sendMessage(color(getPrefix() + raw));
    }

    private String formatPrice(double price) {
        if (Math.floor(price) == price) return String.valueOf((long) price);
        return String.format(Locale.US, "%.2f", price);
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("mdvsocial")) return Collections.emptyList();
        if (args.length == 1) return partial(args[0], Arrays.asList("reload", "title"));
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) {
            return partial(args[1], Arrays.asList("give", "remove", "set", "clear", "give-radius", "give-near"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("title") && Arrays.asList("give", "remove", "set").contains(args[1].toLowerCase(Locale.ROOT))) {
            return partial(args[3], new ArrayList<>(titles.keySet()));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("title") && args[1].equalsIgnoreCase("give-radius")) {
            return partial(args[3], new ArrayList<>(titles.keySet()));
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("title") && args[1].equalsIgnoreCase("give-near")) {
            return partial(args[7], new ArrayList<>(titles.keySet()));
        }
        return Collections.emptyList();
    }

    private List<String> partial(String token, List<String> values) {
        String low = token.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(low)).sorted().collect(Collectors.toList());
    }

    static final class MenuHolder implements InventoryHolder {
        final String type;
        int page;
        final String menuId;
        final String previousMenu;
        final int previousPage;
        Inventory inventory;

        MenuHolder(String type, int page) {
            this(type, page, "", "", 1);
        }

        MenuHolder(String type, int page, String menuId, String previousMenu, int previousPage) {
            this.type = type;
            this.page = page;
            this.menuId = menuId == null ? "" : menuId;
            this.previousMenu = previousMenu == null ? "" : previousMenu;
            this.previousPage = previousPage <= 0 ? 1 : previousPage;
        }
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class CustomMenuDef {
        final String id;
        final String title;
        final int size;
        final Map<Integer, List<CustomMenuItem>> pages = new HashMap<>();
        CustomMenuDef(String id, String title, int size) {
            this.id = id;
            this.title = title;
            this.size = size;
        }
        int maxPage() {
            if (pages.isEmpty()) return 1;
            return pages.keySet().stream().max(Integer::compareTo).orElse(1);
        }
    }

    static final class CustomMenuItem {
        final String id;
        final int slot;
        final String material;
        final int amount;
        final String name;
        final List<String> lore;
        final String headOwner;
        final String action;
        final String targetMenu;
        final List<String> commands;
        final boolean closeOnClick;

        CustomMenuItem(String id, int slot, String material, int amount, String name, List<String> lore, String headOwner, String action, String targetMenu, List<String> commands, boolean closeOnClick) {
            this.id = id;
            this.slot = slot;
            this.material = material == null ? "PAPER" : material;
            this.amount = amount;
            this.name = name == null ? "" : name;
            this.lore = lore == null ? Collections.emptyList() : lore;
            this.headOwner = headOwner == null ? "" : headOwner;
            this.action = action == null ? "" : action;
            this.targetMenu = targetMenu == null ? "" : targetMenu;
            this.commands = commands == null ? Collections.emptyList() : commands;
            this.closeOnClick = closeOnClick;
        }
    }

    public static final class TitleDef {
        public final String id;
        public final String display;
        public final String prefix;
        public final String material;
        public final String headOwner;
        public final boolean purchasable;
        public final double price;
        public final String unlockPermission;
        public final List<String> lore;

        TitleDef(String id, String display, String prefix, String material, String headOwner, boolean purchasable, double price, String unlockPermission, List<String> lore) {
            this.id = id;
            this.display = display;
            this.prefix = prefix;
            this.material = material;
            this.headOwner = headOwner;
            this.purchasable = purchasable;
            this.price = price;
            this.unlockPermission = unlockPermission;
            this.lore = lore == null ? Collections.emptyList() : lore;
        }
    }

    static final class RankDef {
        final String id;
        final String display;
        final String material;
        final String permission;
        final List<String> lore;
        RankDef(String id, String display, String material, String permission, List<String> lore) {
            this.id = id;
            this.display = display;
            this.material = material;
            this.permission = permission;
            this.lore = lore == null ? Collections.emptyList() : lore;
        }
    }

    public static final class MDVSocialExpansion extends PlaceholderExpansion {
        private final MDVSocialPlugin plugin;
        MDVSocialExpansion(MDVSocialPlugin plugin) { this.plugin = plugin; }
        @Override public String getIdentifier() { return "mdvsocial"; }
        @Override public String getAuthor() { return "MDVCRAFT"; }
        @Override public String getVersion() { return plugin.getDescription().getVersion(); }
        @Override public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            if (player == null) return "";
            String p = params.toLowerCase(Locale.ROOT);
            TitleDef active = plugin.getActiveTitle(player);
            return switch (p) {
                case "title" -> active == null ? "" : ChatColor.stripColor(plugin.color(active.display));
                case "title_colored" -> active == null ? "" : plugin.color(active.display);
                case "title_prefix" -> active == null ? "" : plugin.color(active.prefix);
                case "active_title" -> active == null ? "" : active.id;
                case "unlocked_titles" -> String.valueOf(plugin.countUnlocked(player.getUniqueId()));
                default -> "";
            };
        }
    }
}
