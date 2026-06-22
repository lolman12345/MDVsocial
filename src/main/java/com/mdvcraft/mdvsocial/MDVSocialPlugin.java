package com.mdvcraft.mdvsocial;

import me.clip.placeholderapi.PlaceholderAPI;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class MDVSocialPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<String, TitleDef> titles = new HashMap<>();
    private final Map<String, RankDef> ranks = new HashMap<>();
    private final Map<String, CustomMenuDef> customMenus = new HashMap<>();
    private final List<ExternalGuiAction> externalGuiActions = new ArrayList<>();
    private final List<Integer> listSlots = new ArrayList<>();
    private final Map<UUID, MailComposeSession> mailSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> scoreboardPartyAttachments = new ConcurrentHashMap<>();

    private File dataFile;
    private YamlConfiguration data;
    private File mailFile;
    private YamlConfiguration mailData;
    private Economy economy;

    private org.bukkit.NamespacedKey keyAction;
    private org.bukkit.NamespacedKey keyTitle;
    private org.bukkit.NamespacedKey keyMenu;
    private org.bukkit.NamespacedKey keyTargetMenu;
    private org.bukkit.NamespacedKey keyCommands;
    private org.bukkit.NamespacedKey keyCloseOnClick;
    private org.bukkit.NamespacedKey keyConditionPlaceholder;
    private org.bukkit.NamespacedKey keyConditionEquals;
    private org.bukkit.NamespacedKey keyTrueMenu;
    private org.bukkit.NamespacedKey keyFalseMenu;
    private org.bukkit.NamespacedKey keyClansMenu;
    private org.bukkit.NamespacedKey keyMailId;
    private org.bukkit.NamespacedKey keyMailSender;
    private org.bukkit.NamespacedKey keyFriendTargetUuid;
    private org.bukkit.NamespacedKey keyFriendTargetName;
    private org.bukkit.NamespacedKey keyFriendTargetOnline;

    @Override
    public void onEnable() {
        keyAction = new org.bukkit.NamespacedKey(this, "action");
        keyTitle = new org.bukkit.NamespacedKey(this, "title_id");
        keyMenu = new org.bukkit.NamespacedKey(this, "menu");
        keyTargetMenu = new org.bukkit.NamespacedKey(this, "target_menu");
        keyCommands = new org.bukkit.NamespacedKey(this, "commands");
        keyCloseOnClick = new org.bukkit.NamespacedKey(this, "close_on_click");
        keyConditionPlaceholder = new org.bukkit.NamespacedKey(this, "condition_placeholder");
        keyConditionEquals = new org.bukkit.NamespacedKey(this, "condition_equals");
        keyTrueMenu = new org.bukkit.NamespacedKey(this, "true_menu");
        keyFalseMenu = new org.bukkit.NamespacedKey(this, "false_menu");
        keyClansMenu = new org.bukkit.NamespacedKey(this, "clans_menu");
        keyMailId = new org.bukkit.NamespacedKey(this, "mail_id");
        keyMailSender = new org.bukkit.NamespacedKey(this, "mail_sender");
        keyFriendTargetUuid = new org.bukkit.NamespacedKey(this, "friend_target_uuid");
        keyFriendTargetName = new org.bukkit.NamespacedKey(this, "friend_target_name");
        keyFriendTargetOnline = new org.bukkit.NamespacedKey(this, "friend_target_online");

        saveDefaultConfig();
        loadAll();
        setupEconomy();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("social").setExecutor(this);
        getCommand("social").setTabCompleter(this);
        getCommand("titulos").setExecutor(this);
        getCommand("correo").setExecutor(this);
        getCommand("correo").setTabCompleter(this);
        getCommand("carta").setExecutor(this);
        getCommand("carta").setTabCompleter(this);
        getCommand("mdvsocial").setExecutor(this);
        getCommand("mdvsocial").setTabCompleter(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MDVSocialExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado. Placeholders registrados.");
        }

        long cleanupMinutes = Math.max(5L, getConfig().getLong("mail.cleanup-interval-minutes", 30L));
        Bukkit.getScheduler().runTaskTimer(this, this::cleanupExpiredMail, 20L * 60L, cleanupMinutes * 60L * 20L);

        if (getConfig().getBoolean("scoreboard-party-permission.enabled", true)) {
            long interval = Math.max(10L, getConfig().getLong("scoreboard-party-permission.sync-interval-ticks", 20L));
            Bukkit.getScheduler().runTaskTimer(this, this::syncAllScoreboardPartyPermissions, 20L, interval);
            Bukkit.getScheduler().runTaskLater(this, this::resetAllScoreboardPartyPermissions, 5L);
        }

        getLogger().info("MDVSocial 1.2.8 habilitado.");
    }

    @Override
    public void onDisable() {
        resetAllScoreboardPartyPermissions();
        for (PermissionAttachment attachment : scoreboardPartyAttachments.values()) {
            try {
                attachment.remove();
            } catch (Throwable ignored) { }
        }
        scoreboardPartyAttachments.clear();
        saveData();
        saveMailData();
    }

    private void loadAll() {
        reloadConfig();
        loadData();
        loadMailData();
        cleanupExpiredMail();
        loadTitles();
        loadRanks();
        loadListSlots();
        ensureDefaultMenus();
        loadCustomMenus();
        loadExternalGuiActions();
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

    private void loadMailData() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        mailFile = new File(getDataFolder(), "mail-data.yml");
        if (!mailFile.exists()) {
            try {
                mailFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("No se pudo crear mail-data.yml: " + e.getMessage());
            }
        }
        mailData = YamlConfiguration.loadConfiguration(mailFile);
    }

    private void saveMailData() {
        if (mailData == null || mailFile == null) return;
        try {
            mailData.save(mailFile);
        } catch (IOException e) {
            getLogger().severe("No se pudo guardar mail-data.yml: " + e.getMessage());
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
                    readTexture(t),
                    t.getBoolean("purchasable", false),
                    t.getDouble("price", 0),
                    t.getString("unlock-permission", ""),
                    t.getBoolean("hidden", false),
                    t.getBoolean("player-equippable", !t.getBoolean("hidden", false)),
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
            if (args.length >= 1) {
                openRequestedSocialMenu(player, args[0], args.length >= 2 ? parsePage(args[1]) : 1);
            } else {
                openSocialStart(player);
            }
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

        if (cmd.equals("correo") || cmd.equals("carta")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            handleMailCommand(player, args);
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
            sender.sendMessage(color("&6MDVSocial &7comandos: reload, open, title, mail"));
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

        if (sub.equals("open")) {
            if (args.length < 3) {
                sender.sendMessage(color("&cUso: /mdvsocial open <jugador> <menu> [pagina]"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "player-not-found");
                return true;
            }
            openRequestedSocialMenu(target, args[2], args.length >= 4 ? parsePage(args[3]) : 1);
            return true;
        }

        if (sub.equals("mail") || sub.equals("correo") || sub.equals("cartas")) {
            return handleAdminMailCommand(sender, args);
        }

        if (!sub.equals("title")) {
            sender.sendMessage(color("&cUso: /mdvsocial reload | /mdvsocial open <jugador> <menu> [pagina] | /mdvsocial title ... | /mdvsocial mail ..."));
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
            setActiveTitle(target.getUniqueId(), getClearTargetTitleId());
            if (target.isOnline()) runClearCommands(target.getPlayer());
            msg(sender, isMandatoryTitle() ? "title-reset-default" : "removed-title");
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


    private boolean handleAdminMailCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendAdminMailHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("sendall") || action.equals("broadcast")) {
            if (args.length < 3) {
                sendAdminMailHelp(sender);
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            sendServerMailAll(sender, message, getConfig().getLong("mail.server-mail-expire-days", 30L));
            return true;
        }

        if (action.equals("sendall-never") || action.equals("broadcast-never")) {
            if (args.length < 3) {
                sendAdminMailHelp(sender);
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            sendServerMailAll(sender, message, -1L);
            return true;
        }

        if (action.equals("sendall-days") || action.equals("broadcast-days")) {
            if (args.length < 4) {
                sendAdminMailHelp(sender);
                return true;
            }
            long days;
            try {
                days = Long.parseLong(args[2]);
            } catch (Exception e) {
                sender.sendMessage(color("&cLos dias deben ser un numero. Usa -1 para que no expire."));
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            sendServerMailAll(sender, message, days);
            return true;
        }

        sendAdminMailHelp(sender);
        return true;
    }

    private void sendAdminMailHelp(CommandSender sender) {
        sender.sendMessage(color("&6MDVSocial mail:"));
        sender.sendMessage(color("&e/mdvsocial mail sendall <mensaje> &7(envia como MDVCRAFT, duracion por config)"));
        sender.sendMessage(color("&e/mdvsocial mail sendall-days <dias> <mensaje> &7(-1 = no expira)"));
        sender.sendMessage(color("&e/mdvsocial mail sendall-never <mensaje>"));
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
        File clanConClan = new File(folder, "clan_con_clan.yml");
        if (!clanConClan.exists()) {
            try {
                Files.writeString(clanConClan.toPath(), defaultClanConClanMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/clan_con_clan.yml: " + e.getMessage());
            }
        }
        File clanSinClan = new File(folder, "clan_sin_clan.yml");
        if (!clanSinClan.exists()) {
            try {
                Files.writeString(clanSinClan.toPath(), defaultClanSinClanMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/clan_sin_clan.yml: " + e.getMessage());
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
        File correo = new File(folder, "correo.yml");
        if (!correo.exists()) {
            try {
                Files.writeString(correo.toPath(), defaultCorreoMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/correo.yml: " + e.getMessage());
            }
        }
        File amigoOpciones = new File(folder, "amigo_opciones.yml");
        if (!amigoOpciones.exists()) {
            try {
                Files.writeString(amigoOpciones.toPath(), defaultAmigoOpcionesMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/amigo_opciones.yml: " + e.getMessage());
            }
        }
    }

    private String defaultMainMenuYaml() {
        return """
# ==========================================================
# MDVSocial - Menu modular principal
# Acciones disponibles:
# OPEN_MENU / OPEN_CONDITIONAL_MENU / MDVCLANS_OPEN / COMMAND_PLAYER / BACK / CLOSE / PREVIOUS_PAGE / NEXT_PAGE / OPEN_TITLES
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
      - '&8Detecta si tienes clan o no.'
      - '&eClick para abrir.'
    action: OPEN_CONDITIONAL_MENU
    condition-placeholder: '%mdvclans_is_in_clan%'
    condition-equals: 'true'
    true-menu: clan_con_clan
    false-menu: clan_sin_clan

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
  detectar:
    slot: 13
    material: SHIELD
    name: '&a&lClanes'
    lore:
      - ''
      - '&7Este menu puente detecta'
      - '&7si tienes clan o no.'
      - ''
      - '&eClick para continuar.'
    action: OPEN_CONDITIONAL_MENU
    condition-placeholder: '%mdvclans_is_in_clan%'
    condition-equals: 'true'
    true-menu: clan_con_clan
    false-menu: clan_sin_clan

  volver:
    slot: 22
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

    private String defaultClanConClanMenuYaml() {
        return """
title: '&8&lClan'
size: 27
items:
  gestion:
    slot: 10
    material: SHIELD
    name: '&a&lGestion del clan'
    lore:
      - ''
      - '&7Abre la interfaz dinamica'
      - '&7principal de tu clan.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: gestion

  miembros:
    slot: 11
    material: PLAYER_HEAD
    head-owner: '{player}'
    name: '&b&lMiembros'
    lore:
      - ''
      - '&7Lista dinamica con cabezas,'
      - '&7rangos y opciones por permiso.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: miembros

  info:
    slot: 12
    material: WRITABLE_BOOK
    name: '&e&lTablero e informacion'
    lore:
      - ''
      - '&7Banner, tablero, buzon,'
      - '&7solicitudes y logs.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: info

  relaciones:
    slot: 13
    material: MAP
    name: '&9&lRelaciones'
    lore:
      - ''
      - '&7Aliados, enemigos,'
      - '&7bajas y ranking.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: relaciones

  recursos:
    slot: 14
    material: CHEST
    name: '&6&lBanco y almacen'
    lore:
      - ''
      - '&7Accede al banco y al'
      - '&7almacen del clan.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: almacen

  lista:
    slot: 15
    material: WHITE_BANNER
    name: '&f&lLista de clanes'
    lore:
      - ''
      - '&7Explora otros clanes'
      - '&7de MDVCRAFT.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: lista

  ajustes:
    slot: 16
    material: REDSTONE_TORCH
    name: '&c&lAjustes del clan'
    lore:
      - ''
      - '&7Opciones de administracion'
      - '&7para rangos altos.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: ajustes

  base:
    slot: 22
    material: ENDER_PEARL
    name: '&b&lIr a la base'
    lore:
      - ''
      - '&7Teletranspórtate a la base'
      - '&7definida por el clan.'
      - ''
      - '&eClick para viajar.'
    action: COMMAND_PLAYER
    commands:
      - 'clan base'

  volver:
    slot: 18
    material: ARROW
    name: '&6&lVolver'
    action: BACK

  cerrar:
    slot: 26
    material: BARRIER
    name: '&c&lCerrar'
    action: CLOSE
""";
    }

    private String defaultClanSinClanMenuYaml() {
        return """
title: '&8&lClanes'
size: 27
items:
  lista:
    slot: 11
    material: WHITE_BANNER
    name: '&f&lLista de clanes'
    lore:
      - ''
      - '&7Mira los clanes existentes.'
      - '&7Si uno esta abierto, puedes unirte.'
      - '&7Si esta cerrado, puedes solicitar ingreso.'
      - ''
      - '&eClick para abrir.'
    action: MDVCLANS_OPEN
    clans-menu: lista_sinclan

  crear:
    slot: 15
    material: EMERALD
    name: '&a&lCrear clan'
    lore:
      - ''
      - '&7Inicia la creacion de un clan.'
      - '&7El chat te pedira ID y nombre.'
      - ''
      - '&eClick para comenzar.'
    action: COMMAND_PLAYER
    commands:
      - 'clan crear'

  volver:
    slot: 22
    material: ARROW
    name: '&6&lVolver'
    action: BACK

  cerrar:
    slot: 26
    material: BARRIER
    name: '&c&lCerrar'
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


    private String defaultCorreoMenuYaml() {
        return """
title: '&8&lCorreo'
size: 27
items:
  buzon:
    slot: 11
    material: CHEST
    name: '&6&lBuzon'
    lore:
      - ''
      - '&7Revisa las cartas que'
      - '&7otros jugadores te enviaron.'
      - ''
      - '&eClick para abrir.'
    action: OPEN_MAILBOX

  enviar:
    slot: 13
    material: WRITABLE_BOOK
    name: '&e&lEnviar carta'
    lore:
      - ''
      - '&7Escribe una carta a otro'
      - '&7jugador, incluso si no esta conectado.'
      - ''
      - '&eClick para comenzar.'
    action: START_MAIL_SEND
    close-on-click: true

  bloquear:
    slot: 15
    material: RED_DYE
    name: '&c&lBloquear cartas'
    lore:
      - ''
      - '&7Bloquea a un jugador para'
      - '&7que no pueda enviarte cartas.'
      - ''
      - '&eClick para escribir su nombre.'
    action: START_MAIL_BLOCK
    close-on-click: true

  desbloquear:
    slot: 16
    material: LIME_DYE
    name: '&a&lDesbloquear jugador'
    lore:
      - ''
      - '&7Permite que un jugador bloqueado'
      - '&7vuelva a enviarte cartas.'
      - ''
      - '&eClick para escribir su nombre.'
    action: START_MAIL_UNBLOCK
    close-on-click: true

  volver:
    slot: 22
    material: PLAYER_HEAD
    texture: 'eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ=='
    name: '&6&lVolver'
    lore:
      - '&7Regresa al menu social.'
    action: OPEN_MENU
    target-menu: menuamigos
""";
    }


    private String defaultAmigoOpcionesMenuYaml() {
        return """
title: '&8&lOpciones de {target}'
size: 27
items:
  perfil:
    slot: 10
    material: PLAYER_HEAD
    head-owner: '{target}'
    name: '&e&l{target}'
    lore:
      - ''
      - '&7Estado: {target_status}'
      - '&7UUID: &8{target_uuid}'
      - ''
      - '&7Compañero registrado'
      - '&7en tu libreta social.'
      - ''
      - '&8Usa las opciones cercanas'
      - '&8para interactuar.'

  carta:
    slot: 12
    material: WRITABLE_BOOK
    name: '&6&lEnviar carta'
    lore:
      - ''
      - '&7Escribe una carta para &e{target}&7.'
      - '&7No tendrás que volver a escribir su nombre.'
      - ''
      - '&eClick para escribir el mensaje.'
    action: START_MAIL_SEND_TARGET
    close-on-click: true

  party:
    slot: 14
    material: TOTEM_OF_UNDYING
    name: '&d&lInvitar al grupo'
    lore:
      - ''
      - '&7Invita a &e{target} &7a tu'
      - '&7Grupo de Aventura.'
      - ''
      - '&8Si no tienes party, el comportamiento'
      - '&8se controla desde config.yml.'
      - ''
      - '&eClick para invitar.'
    action: INVITE_PARTY_TARGET
    visible-when: online
    close-on-click: true

  tpa:
    slot: 16
    material: ENDER_PEARL
    name: '&a&lSolicitar viaje'
    lore:
      - ''
      - '&7Envía una solicitud de TPA'
      - '&7a &e{target}&7.'
      - ''
      - '&eClick para solicitar.'
    action: COMMAND_PLAYER
    visible-when: online
    commands:
      - 'tpa {target}'

  offline_info:
    slot: 14
    material: GRAY_DYE
    name: '&8&lCompañero desconectado'
    lore:
      - ''
      - '&7Este jugador no está conectado.'
      - '&7Puedes enviarle una carta,'
      - '&7pero no invitarlo a party ni TPA.'
    visible-when: offline

  volver:
    slot: 22
    material: PLAYER_HEAD
    texture: 'eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ=='
    name: '&6&lVolver'
    lore:
      - '&7Regresa al menú social.'
    action: COMMAND_PLAYER
    commands:
      - 'friends'
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

    private void loadExternalGuiActions() {
        externalGuiActions.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("external-gui-actions");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection actionSec = sec.getConfigurationSection(id);
            if (actionSec == null || !actionSec.getBoolean("enabled", true)) continue;

            List<Integer> slots = new ArrayList<>(actionSec.getIntegerList("slots"));
            int singleSlot = actionSec.getInt("slot", Integer.MIN_VALUE);
            if (singleSlot != Integer.MIN_VALUE) slots.add(singleSlot);
            slots = slots.stream().filter(i -> i >= 0 && i < 54).distinct().collect(Collectors.toList());
            if (slots.isEmpty()) {
                getLogger().warning("external-gui-actions." + id + " no tiene slot/slots validos.");
                continue;
            }

            List<String> commands = new ArrayList<>(actionSec.getStringList("commands"));
            String singleCommand = actionSec.getString("command", "");
            if (commands.isEmpty() && singleCommand != null && !singleCommand.isBlank()) commands.add(singleCommand);
            List<String> consoleCommands = new ArrayList<>(actionSec.getStringList("console-commands"));

            ExternalGuiAction def = new ExternalGuiAction(
                    id,
                    actionSec.getString("title", actionSec.getString("title-equals", "")),
                    actionSec.getString("title-contains", ""),
                    slots,
                    commands,
                    consoleCommands,
                    actionSec.getBoolean("close-on-click", true),
                    actionSec.getBoolean("cancel-event", true)
            );
            externalGuiActions.add(def);
        }
        if (!externalGuiActions.isEmpty()) getLogger().info("Puentes de GUIs externas cargados: " + externalGuiActions.size());
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

    private void openRequestedSocialMenu(Player player, String rawMenu, int page) {
        String menu = normalize(rawMenu);
        if (menu.isBlank() || menu.equals("main") || menu.equals("inicio")) {
            if (customMenus.containsKey("main")) openCustomMenu(player, "main", Math.max(1, page), "", 1);
            else openMain(player);
            return;
        }
        if (menu.equals("titulos") || menu.equals("titles") || menu.equals("titulo")) {
            openTitlesHome(player);
            return;
        }
        if (menu.equals("correo") || menu.equals("mail") || menu.equals("cartas") || menu.equals("carta")) {
            if (customMenus.containsKey("correo")) openCustomMenu(player, "correo", 1, "menuamigos", 1);
            else openMailbox(player, 0);
            return;
        }
        if (menu.equals("mis_titulos") || menu.equals("my_titles") || menu.equals("my-titles")) {
            openTitleList(player, "MY_TITLES", Math.max(0, page - 1));
            return;
        }
        if (menu.equals("tienda") || menu.equals("shop") || menu.equals("title_shop") || menu.equals("title-shop")) {
            openTitleList(player, "SHOP", Math.max(0, page - 1));
            return;
        }
        if (menu.equals("rangos") || menu.equals("ranks")) {
            openRanks(player, Math.max(0, page - 1));
            return;
        }
        if (customMenus.containsKey(menu)) {
            openCustomMenu(player, menu, Math.max(1, page), "", 1);
            return;
        }
        player.sendMessage(color(getPrefix() + "&cEse menu no existe: &e" + rawMenu));
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
                    readTexture(sec),
                    action,
                    target,
                    commands,
                    sec.getBoolean("close-on-click", true),
                    sec.getString("visible-when", sec.getString("show-when", "always")),
                    sec.getString("condition-placeholder", sec.getString("placeholder", "")),
                    sec.getString("condition-equals", sec.getString("equals", "true")),
                    normalize(sec.getString("true-menu", sec.getString("menu-true", ""))),
                    normalize(sec.getString("false-menu", sec.getString("menu-false", ""))),
                    normalize(sec.getString("clans-menu", sec.getString("mdvclans-menu", target))),
                    sec.getBoolean("use-clan-banner", sec.getBoolean("dynamic-clan-banner", false))
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
            case "OPEN_CONDITIONAL", "OPEN_CONDITIONAL_MENU", "CONDITIONAL_MENU", "MENU_CONDICIONAL" -> "OPEN_CONDITIONAL_MENU";
            case "MDVCLANS", "MDVCLANS_OPEN", "OPEN_MDVCLANS", "CLAN_DYNAMIC", "CLANES_DINAMICO" -> "MDVCLANS_OPEN";
            case "PREV_PAGE", "PREVIOUS", "PREVIOUS_PAGE" -> "PREVIOUS_PAGE";
            case "NEXT", "NEXT_PAGE" -> "NEXT_PAGE";
            case "OPEN_TITLE", "OPEN_TITLES", "TITLES" -> "OPEN_TITLES";
            case "OPEN_MAIL", "OPEN_MAILBOX", "MAILBOX", "BUZON" -> "OPEN_MAILBOX";
            case "START_MAIL", "START_MAIL_SEND", "SEND_MAIL", "ENVIAR_CARTA" -> "START_MAIL_SEND";
            case "START_MAIL_TARGET", "START_MAIL_SEND_TARGET", "SEND_MAIL_TARGET", "ENVIAR_CARTA_TARGET", "ENVIAR_CARTA_AMIGO" -> "START_MAIL_SEND_TARGET";
            case "INVITE_PARTY_TARGET", "PARTY_INVITE_TARGET", "INVITAR_PARTY", "INVITAR_GRUPO", "INVITE_FRIEND_PARTY" -> "INVITE_PARTY_TARGET";
            case "START_MAIL_BLOCK", "BLOCK_MAIL", "BLOQUEAR_CARTAS" -> "START_MAIL_BLOCK";
            case "START_MAIL_UNBLOCK", "UNBLOCK_MAIL", "DESBLOQUEAR_CARTAS" -> "START_MAIL_UNBLOCK";
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
        openCustomMenu(player, menuId, page, previousMenu, previousPage, null, "", false);
    }

    private void openCustomMenu(Player player, String menuId, int page, String previousMenu, int previousPage, UUID targetUuid, String targetName, boolean targetOnline) {
        menuId = normalize(menuId);
        CustomMenuDef def = customMenus.get(menuId);
        if (def == null) {
            player.sendMessage(color(getPrefix() + "&cEse menu no existe: &e" + menuId));
            return;
        }
        int maxPage = def.maxPage();
        page = Math.max(1, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder("CUSTOM_MENU", page, menuId, previousMenu == null ? "" : normalize(previousMenu), previousPage <= 0 ? 1 : previousPage, targetUuid, targetName, targetOnline);
        Inventory inv = Bukkit.createInventory(holder, def.size, color(applyTargetPlaceholders(def.title.replace("{page}", String.valueOf(page)).replace("{max_page}", String.valueOf(maxPage)), player, targetUuid, targetName, targetOnline)));
        holder.inventory = inv;
        fill(inv);

        List<CustomMenuItem> items = def.pages.getOrDefault(page, Collections.emptyList());
        for (CustomMenuItem menuItem : items) {
            if (!menuItem.isVisible(targetUuid, targetOnline)) continue;
            if (menuItem.slot >= 0 && menuItem.slot < inv.getSize()) inv.setItem(menuItem.slot, customMenuItemStack(player, menuItem, targetUuid, targetName, targetOnline));
        }
        player.openInventory(inv);
    }

    private ItemStack customMenuItemStack(Player player, CustomMenuItem def, UUID targetUuid, String targetName, boolean targetOnline) {
        String clanBannerData = def.useClanBanner ? getPlayerClanBannerData(player.getUniqueId()) : null;
        boolean usingClanBanner = def.useClanBanner && clanBannerData != null;
        Material mat = Material.matchMaterial(def.material.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.PAPER;
        int amount = Math.max(1, Math.min(64, def.amount));
        ItemStack item = usingClanBanner ? bannerFromSerializedData(clanBannerData) : new ItemStack(mat, amount);
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (usingClanBanner) {
            hideBannerTooltip(meta);
        }
        if (!usingClanBanner && mat == Material.PLAYER_HEAD && meta instanceof SkullMeta skull) {
            String texture = applyTargetPlaceholders(def.texture, player, targetUuid, targetName, targetOnline);
            if (texture != null && !texture.isBlank()) {
                applySkullTexture(skull, texture);
            } else if (def.headOwner != null && !def.headOwner.isBlank()) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(applyTargetPlaceholders(def.headOwner, player, targetUuid, targetName, targetOnline));
                skull.setOwningPlayer(owner);
            }
            meta = skull;
        }
        if (def.name != null && !def.name.isBlank()) meta.setDisplayName(color(applyTargetPlaceholders(def.name, player, targetUuid, targetName, targetOnline)));
        List<String> lore = new ArrayList<>();
        for (String line : def.lore) lore.add(color(applyTargetPlaceholders(line, player, targetUuid, targetName, targetOnline)));
        if (!lore.isEmpty()) meta.setLore(lore);
        if (def.action != null && !def.action.isBlank()) meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, def.action);
        if (def.targetMenu != null && !def.targetMenu.isBlank()) meta.getPersistentDataContainer().set(keyTargetMenu, PersistentDataType.STRING, def.targetMenu);
        if (def.conditionPlaceholder != null && !def.conditionPlaceholder.isBlank()) meta.getPersistentDataContainer().set(keyConditionPlaceholder, PersistentDataType.STRING, def.conditionPlaceholder);
        if (def.conditionEquals != null && !def.conditionEquals.isBlank()) meta.getPersistentDataContainer().set(keyConditionEquals, PersistentDataType.STRING, def.conditionEquals);
        if (def.trueMenu != null && !def.trueMenu.isBlank()) meta.getPersistentDataContainer().set(keyTrueMenu, PersistentDataType.STRING, def.trueMenu);
        if (def.falseMenu != null && !def.falseMenu.isBlank()) meta.getPersistentDataContainer().set(keyFalseMenu, PersistentDataType.STRING, def.falseMenu);
        if (def.clansMenu != null && !def.clansMenu.isBlank()) meta.getPersistentDataContainer().set(keyClansMenu, PersistentDataType.STRING, def.clansMenu);
        if (targetUuid != null) meta.getPersistentDataContainer().set(keyFriendTargetUuid, PersistentDataType.STRING, targetUuid.toString());
        if (targetName != null && !targetName.isBlank()) meta.getPersistentDataContainer().set(keyFriendTargetName, PersistentDataType.STRING, targetName);
        meta.getPersistentDataContainer().set(keyFriendTargetOnline, PersistentDataType.STRING, String.valueOf(targetOnline));
        if (!def.commands.isEmpty()) meta.getPersistentDataContainer().set(keyCommands, PersistentDataType.STRING, String.join("\n", def.commands));
        meta.getPersistentDataContainer().set(keyCloseOnClick, PersistentDataType.STRING, String.valueOf(def.closeOnClick));
        item.setItemMeta(meta);
        return item;
    }

    private String readTexture(ConfigurationSection sec) {
        if (sec == null) return "";
        String texture = sec.getString("custom-head-texture", "");
        if (texture == null || texture.isBlank()) texture = sec.getString("texture", "");
        if (texture == null || texture.isBlank()) texture = sec.getString("head-texture", "");
        if (texture == null || texture.isBlank()) texture = sec.getString("skull-texture", "");
        if (texture == null || texture.isBlank()) texture = sec.getString("texture-base64", "");
        return texture == null ? "" : texture.trim();
    }

    /**
     * Extrae la URL real desde una textura Base64 de Minecraft Heads.
     * Tambien acepta una URL directa http/https por comodidad.
     */
    private String extractTextureUrl(String textureValue) {
        if (textureValue == null) return "";
        String value = textureValue.trim();
        if (value.isBlank()) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) return value;

        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            int urlKey = decoded.indexOf("\"url\"");
            if (urlKey < 0) return "";
            int colon = decoded.indexOf(':', urlKey);
            if (colon < 0) return "";
            int firstQuote = decoded.indexOf('\"', colon);
            if (firstQuote < 0) return "";
            int secondQuote = decoded.indexOf('\"', firstQuote + 1);
            if (secondQuote < 0) return "";
            return decoded.substring(firstQuote + 1, secondQuote).replace("\\/", "/");
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Aplica texturas custom a cabezas usando la API publica de Bukkit/Paper.
     *
     * Version 1.1.6:
     * - Sin reflexion.
     * - Sin tocar campos internos del SkullMeta.
     * - Evita IllegalAccessException/IllegalArgumentException en Paper/Purpur 1.21+.
     */
    private void applySkullTexture(SkullMeta skull, String textureValue) {
        if (skull == null || textureValue == null || textureValue.isBlank()) return;

        String textureUrl = extractTextureUrl(textureValue.trim());
        if (textureUrl == null || textureUrl.isBlank()) {
            getLogger().warning("No se pudo aplicar textura custom de cabeza: textura invalida o Base64 sin URL.");
            return;
        }

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "MDVSocial");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            skull.setOwnerProfile(profile);
        } catch (Throwable ex) {
            getLogger().warning("No se pudo aplicar textura custom de cabeza con API publica: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    private String getPlayerClanBannerData(UUID playerUuid) {
        if (playerUuid == null) return null;
        try {
            org.bukkit.plugin.Plugin clans = Bukkit.getPluginManager().getPlugin("MDVClans");
            if (clans == null || !clans.isEnabled()) return null;
            Method method = clans.getClass().getMethod("getPlayerClanBannerData", UUID.class);
            Object result = method.invoke(clans, playerUuid);
            return result == null ? null : String.valueOf(result);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack bannerFromSerializedData(String data) {
        if (data != null && !data.isBlank()) {
            try {
                return itemStackFromBase64(data);
            } catch (Throwable ignored) {
                return new ItemStack(Material.WHITE_BANNER);
            }
        }
        return new ItemStack(Material.WHITE_BANNER);
    }

    private ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            Object object = dataInput.readObject();
            return object instanceof ItemStack stack ? stack : new ItemStack(Material.WHITE_BANNER);
        }
    }

    private void hideBannerTooltip(ItemMeta meta) {
        if (meta == null) return;
        try {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        } catch (Throwable ignored) {
            // Compatibilidad con builds donde el flag no exista.
        }
    }

    private String applyPlayerPlaceholders(String input, Player player) {
        if (input == null) return "";
        String out = input.replace("{player}", player.getName());

        // Atajos internos para menus de MDVSocial.
        // Estos usan PlaceholderAPI si esta disponible, por ejemplo MMOCore.
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String level = papi(player, "%mmocore_level%");
            String exp = papi(player, "%mmocore_experience%");
            String next = papi(player, "%mmocore_next_level%");
            String percent = stripPercent(papi(player, "%mmocore_level_percent%"));
            String clazz = papi(player, "%mmocore_class%");
            String classId = papi(player, "%mmocore_class_id%");
            String attributePoints = papi(player, "%mmocore_attribute_points%");

            out = out
                    .replace("{level}", level)
                    .replace("{exp}", exp)
                    .replace("{experience}", exp)
                    .replace("{next_level}", next)
                    .replace("{percent}", percent)
                    .replace("{progress}", progressBar(percent))
                    .replace("{class}", clazz)
                    .replace("{class_id}", classId)
                    .replace("{attribute_points}", attributePoints);

            // Permite usar cualquier placeholder normal, por ejemplo:
            // %mmocore_level%, %vault_eco_balance%, %player_name%, etc.
            out = PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
    }

    private String applyTargetPlaceholders(String input, Player player, UUID targetUuid, String targetName, boolean targetOnline) {
        String out = applyPlayerPlaceholders(input, player);
        String safeName = targetName == null || targetName.isBlank() ? "jugador" : targetName;
        String uuidText = targetUuid == null ? "" : targetUuid.toString();
        out = out
                .replace("{target}", safeName)
                .replace("{target_name}", safeName)
                .replace("{friend}", safeName)
                .replace("{friend_name}", safeName)
                .replace("{target_uuid}", uuidText)
                .replace("{friend_uuid}", uuidText)
                .replace("{target_online}", targetOnline ? "true" : "false")
                .replace("{friend_online}", targetOnline ? "true" : "false")
                .replace("{target_status}", targetOnline ? "&aEn línea" : "&7Desconectado")
                .replace("{friend_status}", targetOnline ? "&aEn línea" : "&7Desconectado");
        return out;
    }

    private String papi(Player player, String placeholder) {
        try {
            String value = PlaceholderAPI.setPlaceholders(player, placeholder);
            if (value == null || value.equalsIgnoreCase(placeholder)) return "";
            return value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String stripPercent(String value) {
        if (value == null) return "0";
        return value.replace("%", "").trim();
    }

    private String progressBar(String percentText) {
        double percent;
        try {
            percent = Double.parseDouble(stripPercent(percentText).replace(",", "."));
        } catch (Exception ignored) {
            percent = 0;
        }
        percent = Math.max(0, Math.min(100, percent));
        int total = 10;
        int filled = (int) Math.round((percent / 100.0) * total);
        StringBuilder bar = new StringBuilder();
        bar.append("&e");
        for (int i = 0; i < filled; i++) bar.append("|");
        bar.append("&7");
        for (int i = filled; i < total; i++) bar.append("|");
        return bar.toString();
    }


    private boolean mailEnabled() {
        return getConfig().getBoolean("mail.enabled", true);
    }

    private void handleMailCommand(Player player, String[] args) {
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.use")) {
            msg(player, "no-permission");
            return;
        }
        if (args.length == 0) {
            if (customMenus.containsKey("correo")) openCustomMenu(player, "correo", 1, "menuamigos", 1);
            else openMailbox(player, 0);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "buzon", "mailbox", "recibidas" -> openMailbox(player, 0);
            case "enviar", "send" -> {
                if (!player.hasPermission("mdvsocial.mail.send")) {
                    msg(player, "no-permission");
                    return;
                }
                if (args.length >= 3) {
                    String targetName = args[1];
                    String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    sendMailByName(player, targetName, message);
                } else {
                    startMailRecipientPrompt(player);
                }
            }
            case "bloquear", "block" -> {
                if (args.length >= 2) blockMailByName(player, args[1]);
                else startMailBlockPrompt(player, true);
            }
            case "desbloquear", "unblock" -> {
                if (args.length >= 2) unblockMailByName(player, args[1]);
                else startMailBlockPrompt(player, false);
            }
            case "bloqueados", "blocked" -> sendBlockedList(player);
            case "cancelar", "cancel" -> {
                MailComposeSession session = mailSessions.remove(player.getUniqueId());
                msg(player, "mail-cancelled");
                returnToMailSessionMenu(player, session);
            }
            default -> {
                player.sendMessage(color(getPrefix() + "&e/correo &7- abre el menu de correo"));
                player.sendMessage(color(getPrefix() + "&e/carta enviar <jugador> <mensaje>"));
                player.sendMessage(color(getPrefix() + "&e/carta bloquear <jugador>"));
                player.sendMessage(color(getPrefix() + "&e/carta desbloquear <jugador>"));
            }
        }
    }

    private void startMailRecipientPrompt(Player player) {
        startMailRecipientPrompt(player, "correo", 1);
    }

    private void startMailRecipientPrompt(Player player, String returnMenu, int returnPage) {
        if (!mailEnabled()) { msg(player, "mail-disabled"); return; }
        if (!player.hasPermission("mdvsocial.mail.send")) { msg(player, "no-permission"); return; }
        mailSessions.put(player.getUniqueId(), new MailComposeSession(MailStage.RECIPIENT, null, returnMenu, returnPage));
        msg(player, "mail-recipient-prompt");
    }

    private void startMailMessagePromptToTarget(Player player, UUID targetUuid, String fallbackName, String returnMenu, int returnPage) {
        if (!mailEnabled()) { msg(player, "mail-disabled"); return; }
        if (!player.hasPermission("mdvsocial.mail.send")) { msg(player, "no-permission"); return; }
        if (targetUuid == null) { msg(player, "social-target-not-found"); return; }
        if (targetUuid.equals(player.getUniqueId())) { msg(player, "mail-self"); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null || target.getName().isBlank() ? fallbackName : target.getName();
        if (targetName == null || targetName.isBlank()) targetName = "jugador";
        mailSessions.put(player.getUniqueId(), new MailComposeSession(MailStage.MESSAGE, targetName, targetUuid, returnMenu, returnPage));
        msg(player, "mail-message-prompt", Map.of("target", targetName, "max", String.valueOf(getMaxMailLength())));
    }

    private void startMailBlockPrompt(Player player, boolean block) {
        startMailBlockPrompt(player, block, "correo", 1);
    }

    private void startMailBlockPrompt(Player player, boolean block, String returnMenu, int returnPage) {
        mailSessions.put(player.getUniqueId(), new MailComposeSession(block ? MailStage.BLOCK : MailStage.UNBLOCK, null, returnMenu, returnPage));
        msg(player, block ? "mail-block-prompt" : "mail-unblock-prompt");
    }

    private void startMailReplyFromMail(Player player, String mailId, int returnPage) {
        if (!mailEnabled()) { msg(player, "mail-disabled"); return; }
        if (!player.hasPermission("mdvsocial.mail.send")) { msg(player, "no-permission"); return; }
        if (mailId == null || mailId.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + mailId))) {
            msg(player, "mail-not-found");
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + mailId);
        String fromUuidText = mailData.getString(base + ".from-uuid", "");
        String fromName = mailData.getString(base + ".from-name", getConfig().getString("mail.server-author-name", "MDVCRAFT"));
        if (fromUuidText == null || fromUuidText.isBlank()) {
            msg(player, "mail-cannot-reply-server");
            return;
        }
        try {
            UUID targetUuid = UUID.fromString(fromUuidText);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = target.getName() == null || target.getName().isBlank() ? fromName : target.getName();
            player.closeInventory();
            mailSessions.put(player.getUniqueId(), new MailComposeSession(MailStage.MESSAGE, targetName, targetUuid, "MAILBOX", Math.max(0, returnPage)));
            msg(player, "mail-reply-prompt", Map.of("target", targetName, "max", String.valueOf(getMaxMailLength())));
        } catch (Exception ignored) {
            msg(player, "mail-cannot-reply-server");
        }
    }

    @EventHandler
    public void onMailChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MailComposeSession session = mailSessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        String text = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> handleMailChatInput(player, text));
    }

    private void handleMailChatInput(Player player, String text) {
        MailComposeSession session = mailSessions.get(player.getUniqueId());
        if (session == null) return;
        if (text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
            mailSessions.remove(player.getUniqueId());
            msg(player, "mail-cancelled");
            returnToMailSessionMenu(player, session);
            return;
        }
        if (session.stage == MailStage.RECIPIENT) {
            OfflinePlayer target = findKnownOfflinePlayer(text);
            if (target == null) {
                sendPlayerNotFound(player, text);
                return;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                msg(player, "mail-self");
                return;
            }
            mailSessions.put(player.getUniqueId(), new MailComposeSession(MailStage.MESSAGE, target.getName() == null ? text : target.getName(), session.returnMenu, session.returnPage));
            msg(player, "mail-message-prompt", Map.of("target", target.getName() == null ? text : target.getName(), "max", String.valueOf(getMaxMailLength())));
            return;
        }
        if (session.stage == MailStage.MESSAGE) {
            String targetName = session.targetName;
            UUID targetUuid = session.targetUuid;
            mailSessions.remove(player.getUniqueId());
            if (targetUuid != null) sendMailByUuid(player, targetUuid, targetName, text);
            else sendMailByName(player, targetName, text);
            return;
        }
        if (session.stage == MailStage.BLOCK) {
            mailSessions.remove(player.getUniqueId());
            blockMailByName(player, text);
            return;
        }
        if (session.stage == MailStage.UNBLOCK) {
            mailSessions.remove(player.getUniqueId());
            unblockMailByName(player, text);
        }
    }

    private OfflinePlayer findKnownOfflinePlayer(String name) {
        if (name == null || name.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        boolean allowUnknown = getConfig().getBoolean("mail.allow-unknown-targets", false);
        if (allowUnknown || off.hasPlayedBefore()) return off;
        return null;
    }

    private void sendMailByName(Player sender, String targetName, String message) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) {
            sendPlayerNotFound(sender, targetName);
            return;
        }
        sendMail(sender, target, message);
    }

    private void sendMailByUuid(Player sender, UUID targetUuid, String fallbackName, String message) {
        if (targetUuid == null) {
            sendMailByName(sender, fallbackName, message);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline() && !getConfig().getBoolean("mail.allow-unknown-targets", false))) {
            msg(sender, "mail-player-not-found");
            return;
        }
        sendMail(sender, target, message);
    }

    private void sendMail(Player sender, OfflinePlayer target, String message) {
        if (!mailEnabled()) { msg(sender, "mail-disabled"); return; }
        if (!sender.hasPermission("mdvsocial.mail.send")) { msg(sender, "no-permission"); return; }
        if (target.getUniqueId().equals(sender.getUniqueId())) { msg(sender, "mail-self"); return; }
        String clean = sanitizeMailMessage(message);
        if (clean.isBlank()) {
            msg(sender, "mail-empty");
            return;
        }
        int max = getMaxMailLength();
        if (clean.length() > max) {
            msg(sender, "mail-too-long", Map.of("max", String.valueOf(max)));
            return;
        }
        if (isMailBlocked(target.getUniqueId(), sender.getUniqueId())) {
            msg(sender, "mail-blocked-by-target", Map.of("target", target.getName() == null ? "ese jugador" : target.getName()));
            return;
        }
        int limit = getMailboxLimit(target);
        int count = getMailIds(target.getUniqueId()).size();
        if (count >= limit) {
            msg(sender, "mail-full", Map.of("target", target.getName() == null ? "ese jugador" : target.getName(), "limit", String.valueOf(limit)));
            return;
        }
        long expiresAt = System.currentTimeMillis() + getMailExpireMillis();
        storeMail(target.getUniqueId(), target.getName() == null ? "jugador" : target.getName(), sender.getUniqueId().toString(), sender.getName(), clean, expiresAt);
        saveMailData();
        msg(sender, "mail-sent", Map.of("target", target.getName() == null ? "jugador" : target.getName()));
    }

    private void sendServerMailAll(CommandSender sender, String message, long expireDays) {
        if (!mailEnabled()) { msg(sender, "mail-disabled"); return; }
        String clean = sanitizeMailMessage(message);
        if (clean.isBlank()) {
            msg(sender, "mail-empty");
            return;
        }
        int max = getMaxMailLength();
        if (clean.length() > max) {
            msg(sender, "mail-too-long", Map.of("max", String.valueOf(max)));
            return;
        }

        long expiresAt = expireDays <= 0 ? 0L : System.currentTimeMillis() + (expireDays * 24L * 60L * 60L * 1000L);
        String author = getConfig().getString("mail.server-author-name", "MDVCRAFT");
        boolean ignoreLimit = getConfig().getBoolean("mail.server-mail-ignore-mailbox-limit", true);
        int sent = 0;
        int skipped = 0;
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (target == null || target.getUniqueId() == null || !target.hasPlayedBefore()) continue;
            if (!ignoreLimit) {
                int limit = getMailboxLimit(target);
                int count = getMailIds(target.getUniqueId()).size();
                if (count >= limit) {
                    skipped++;
                    continue;
                }
            }
            storeMail(target.getUniqueId(), target.getName() == null ? "jugador" : target.getName(), "", author, clean, expiresAt);
            sent++;
        }
        saveMailData();
        msg(sender, "mail-broadcast-sent", Map.of("sent", String.valueOf(sent), "skipped", String.valueOf(skipped)));
    }

    private String storeMail(UUID targetUuid, String toName, String fromUuid, String fromName, String message, long expiresAt) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String base = mailPath(targetUuid, "letters." + id);
        mailData.set(base + ".from-uuid", fromUuid == null ? "" : fromUuid);
        mailData.set(base + ".from-name", fromName == null || fromName.isBlank() ? "Desconocido" : fromName);
        mailData.set(base + ".to-name", toName == null ? "jugador" : toName);
        mailData.set(base + ".message", message);
        mailData.set(base + ".sent-at", now);
        mailData.set(base + ".expires-at", expiresAt);
        mailData.set(base + ".read", false);
        return id;
    }

    public boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName, String clanTag, String clanName, String message, long expiresAt) {
        return sendClanInviteMail(targetUuid, targetName, inviterUuid, fromName, clanTag, clanName, message, "", expiresAt);
    }

    public boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName, String clanTag, String clanName, String message, String clanBannerData, long expiresAt) {
        if (!mailEnabled() || targetUuid == null || clanTag == null || clanTag.isBlank()) return false;
        String clean = sanitizeMailMessage(message);
        if (clean.isBlank()) clean = "El clan " + clanName + " [" + clanTag + "] te invitó a unirte.";
        int max = getMaxMailLength();
        if (clean.length() > max) clean = clean.substring(0, Math.max(0, max - 3)) + "...";

        boolean ignoreLimit = getConfig().getBoolean("mail.clan-invites.ignore-mailbox-limit", true);
        if (!ignoreLimit) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            int limit = getMailboxLimit(target);
            int count = getMailIds(targetUuid).size();
            if (count >= limit) return false;
        }

        String senderName = fromName == null || fromName.isBlank() ? "MDVClans" : fromName;
        String id = storeMail(targetUuid, targetName == null || targetName.isBlank() ? "jugador" : targetName, inviterUuid == null ? "" : inviterUuid.toString(), senderName, clean, expiresAt);
        String base = mailPath(targetUuid, "letters." + id);
        mailData.set(base + ".type", "MDVCLANS_INVITE");
        mailData.set(base + ".clan-tag", clanTag);
        mailData.set(base + ".clan-name", clanName == null || clanName.isBlank() ? clanTag : clanName);
        mailData.set(base + ".clan-banner", clanBannerData == null ? "" : clanBannerData);
        mailData.set(base + ".inviter-uuid", inviterUuid == null ? "" : inviterUuid.toString());
        saveMailData();
        return true;
    }

    private String sanitizeMailMessage(String message) {
        if (message == null) return "";
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private int getMaxMailLength() {
        return Math.max(20, getConfig().getInt("mail.max-message-length", 180));
    }

    private long getMailExpireMillis() {
        long days = Math.max(1L, getConfig().getLong("mail.expire-after-days", 10L));
        return days * 24L * 60L * 60L * 1000L;
    }

    private int getMailboxLimit(OfflinePlayer player) {
        int limit = Math.max(1, getConfig().getInt("mail.default-mailbox-size", 10));
        if (player != null && player.isOnline()) {
            ConfigurationSection sec = getConfig().getConfigurationSection("mail.mailbox-size-permissions");
            if (sec != null) {
                Player online = player.getPlayer();
                for (String perm : sec.getKeys(false)) {
                    int value = sec.getInt(perm, limit);
                    if (online.hasPermission(perm) && value > limit) limit = value;
                }
            }
        }
        return limit;
    }

    private String mailPath(UUID uuid, String child) {
        return "mailbox." + uuid + "." + child;
    }

    private List<String> getMailIds(UUID uuid) {
        cleanupExpiredMailFor(uuid);
        ConfigurationSection sec = mailData.getConfigurationSection(mailPath(uuid, "letters"));
        if (sec == null) return new ArrayList<>();
        List<String> ids = new ArrayList<>(sec.getKeys(false));
        ids.sort((a, b) -> Long.compare(mailData.getLong(mailPath(uuid, "letters." + b + ".sent-at"), 0L), mailData.getLong(mailPath(uuid, "letters." + a + ".sent-at"), 0L)));
        return ids;
    }

    private void cleanupExpiredMail() {
        if (mailData == null) return;
        ConfigurationSection mailboxes = mailData.getConfigurationSection("mailbox");
        if (mailboxes == null) return;
        boolean changed = false;
        for (String uuidText : mailboxes.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                if (cleanupExpiredMailFor(uuid)) changed = true;
            } catch (Exception ignored) { }
        }
        if (changed) saveMailData();
    }

    private boolean cleanupExpiredMailFor(UUID uuid) {
        if (mailData == null) return false;
        ConfigurationSection sec = mailData.getConfigurationSection(mailPath(uuid, "letters"));
        if (sec == null) return false;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String id : new ArrayList<>(sec.getKeys(false))) {
            long expires = mailData.getLong(mailPath(uuid, "letters." + id + ".expires-at"), 0L);
            if (expires > 0 && expires <= now) {
                mailData.set(mailPath(uuid, "letters." + id), null);
                changed = true;
            }
        }
        if (changed) saveMailData();
        return changed;
    }

    private void openMailbox(Player player, int page) {
        if (!mailEnabled()) { msg(player, "mail-disabled"); return; }
        if (!player.hasPermission("mdvsocial.mail.read")) { msg(player, "no-permission"); return; }
        List<String> ids = getMailIds(player.getUniqueId());
        List<Integer> slots = getMailSlots();
        int size = normalizeMenuSize(getConfig().getInt("mail.menus.mailbox.size", 54));
        int perPage = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil(ids.size() / (double) perPage) - 1);
        page = Math.max(0, Math.min(page, maxPage));
        String title = getConfig().getString("mail.menus.mailbox.title", "&8Buzon {page}/{max_page}")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{max_page}", String.valueOf(maxPage + 1))
                .replace("{count}", String.valueOf(ids.size()))
                .replace("{limit}", String.valueOf(getMailboxLimit(player)));
        Inventory inv = createMenu("MAILBOX", size, title, page);
        fill(inv);
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= ids.size()) break;
            int slot = slots.get(i);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, mailItem(player, ids.get(index)));
        }
        if (ids.isEmpty()) inv.setItem(size / 2, emptyMailboxItem());
        if (page > 0) inv.setItem(size - 9, navItem("previous-page", "PREVIOUS_PAGE"));
        inv.setItem(size - 5, navItem("back", "OPEN_MENU"));
        setTargetMenuOnItem(inv, size - 5, "correo");
        if (page < maxPage) inv.setItem(size - 1, navItem("next-page", "NEXT_PAGE"));
        else inv.setItem(size - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private List<Integer> getMailSlots() {
        List<Integer> slots = getConfig().getIntegerList("mail.menus.mailbox.slots");
        if (slots == null || slots.isEmpty()) return new ArrayList<>(listSlots);
        return slots.stream().filter(i -> i >= 0 && i < 54).collect(Collectors.toList());
    }

    private ItemStack emptyMailboxItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("mail.items.empty.name", "&7Buzon vacio")));
        List<String> lore = getConfig().getStringList("mail.items.empty.lore");
        if (lore.isEmpty()) lore = List.of("&8No tienes cartas guardadas.");
        meta.setLore(lore.stream().map(this::color).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack mailItem(Player viewer, String id) {
        String base = mailPath(viewer.getUniqueId(), "letters." + id);
        String fromName = mailData.getString(base + ".from-name", "Desconocido");
        String fromUuidText = mailData.getString(base + ".from-uuid", "");
        String message = mailData.getString(base + ".message", "");
        boolean read = mailData.getBoolean(base + ".read", false);
        long sentAt = mailData.getLong(base + ".sent-at", 0L);
        long expiresAt = mailData.getLong(base + ".expires-at", 0L);
        boolean clanInviteMail = "MDVCLANS_INVITE".equalsIgnoreCase(mailData.getString(base + ".type", ""));
        String clanBannerData = mailData.getString(base + ".clan-banner", "");

        ItemStack item = clanInviteMail ? bannerFromSerializedData(clanBannerData) : new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (clanInviteMail) {
            hideBannerTooltip(meta);
        } else if (meta instanceof SkullMeta skull) {
            try {
                if (!fromUuidText.isBlank()) skull.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(fromUuidText)));
                else skull.setOwningPlayer(Bukkit.getOfflinePlayer(fromName));
            } catch (Throwable ignored) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(fromName));
            }
            meta = skull;
        }
        meta.setDisplayName(color((read ? "&eCarta de &f" : "&aNueva carta de &f") + fromName));
        List<String> lore = new ArrayList<>();
        lore.add(color(""));
        if (clanInviteMail) lore.add(color("&7Tipo: &dInvitación de clan"));
        lore.add(color("&7Enviada: &e" + formatTime(sentAt)));
        lore.add(color("&7Expira: &c" + daysLeftText(expiresAt)));
        lore.add(color(""));
        lore.add(color("&8\"" + shorten(message, 34) + "\""));
        lore.add(color(""));
        lore.add(color("&eClick para leer."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "READ_MAIL");
        meta.getPersistentDataContainer().set(keyMailId, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private void openMailRead(Player player, String id, int page) {
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + id);
        mailData.set(base + ".read", true);
        saveMailData();
        String fromName = mailData.getString(base + ".from-name", "Desconocido");
        String fromUuid = mailData.getString(base + ".from-uuid", "");
        String message = mailData.getString(base + ".message", "");
        String mailType = mailData.getString(base + ".type", "");
        boolean clanInviteMail = "MDVCLANS_INVITE".equalsIgnoreCase(mailType);
        String clanBannerData = mailData.getString(base + ".clan-banner", "");
        long sentAt = mailData.getLong(base + ".sent-at", 0L);
        long expiresAt = mailData.getLong(base + ".expires-at", 0L);

        int size = normalizeMenuSize(getConfig().getInt("mail.menus.read.size", 27));
        String title = getConfig().getString("mail.menus.read.title", "&8Carta de {sender}").replace("{sender}", fromName);
        MenuHolder holder = new MenuHolder("MAIL_READ", page);
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.inventory = inv;
        fill(inv);

        ItemStack letter = clanInviteMail ? bannerFromSerializedData(clanBannerData) : new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = letter.getItemMeta();
        if (meta != null) {
            if (clanInviteMail) hideBannerTooltip(meta);
            meta.setDisplayName(color((clanInviteMail ? "&d&lInvitación de clan de &f" : "&e&lCarta de &f") + fromName));
            List<String> lore = new ArrayList<>();
            lore.add(color(""));
            if (clanInviteMail) lore.add(color("&7Tipo: &dInvitación de clan"));
            lore.add(color("&7Enviada: &e" + formatTime(sentAt)));
            lore.add(color("&7Expira: &c" + daysLeftText(expiresAt)));
            lore.add(color(""));
            for (String line : wrapText(message, 38)) lore.add(color("&f" + line));
            meta.setLore(lore);
            letter.setItemMeta(meta);
        }
        inv.setItem(getConfig().getInt("mail.menus.read.letter-slot", 13), letter);

        inv.setItem(getConfig().getInt("mail.menus.read.back-slot", 11), mailActionItem("items.back", "MAIL_BACK", id, fromUuid));
        if (clanInviteMail) {
            inv.setItem(getConfig().getInt("mail.menus.read.reply-slot", 14), mailActionItem("mail.items.clan-invite-accept", "ACCEPT_CLAN_INVITE", id, fromUuid, Material.LIME_DYE, "&a&lAceptar invitación", List.of("", "&7Acepta la invitación", "&7y entra al clan si hay cupo.", "", "&eClick para aceptar.")));
            inv.setItem(getConfig().getInt("mail.menus.read.delete-slot", 15), mailActionItem("mail.items.clan-invite-reject", "REJECT_CLAN_INVITE", id, fromUuid, Material.RED_DYE, "&c&lRechazar invitación", List.of("", "&7Rechaza la invitación", "&7y elimina esta carta.", "", "&eClick para rechazar.")));
        } else {
            inv.setItem(getConfig().getInt("mail.menus.read.reply-slot", 14), mailActionItem("mail.items.reply", "REPLY_MAIL", id, fromUuid));
            inv.setItem(getConfig().getInt("mail.menus.read.delete-slot", 15), mailActionItem("mail.items.delete", "DELETE_MAIL", id, fromUuid));
            inv.setItem(getConfig().getInt("mail.menus.read.block-slot", 16), mailActionItem("mail.items.block-sender", "BLOCK_MAIL_SENDER", id, fromUuid));
        }
        player.openInventory(inv);
    }

    private ItemStack mailActionItem(String path, String action, String mailId, String senderUuid) {
        return mailActionItem(path, action, mailId, senderUuid, Material.PAPER, "", List.of());
    }

    private ItemStack mailActionItem(String path, String action, String mailId, String senderUuid, Material defMaterial, String defName, List<String> defLore) {
        ConfigurationSection sec = getConfig().getConfigurationSection(path);
        ItemStack item;
        if (sec == null) {
            item = new ItemStack(defMaterial == null ? Material.PAPER : defMaterial);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (defName != null && !defName.isBlank()) meta.setDisplayName(color(defName));
                if (defLore != null && !defLore.isEmpty()) meta.setLore(defLore.stream().map(this::color).collect(Collectors.toList()));
                if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
                item.setItemMeta(meta);
            }
        } else {
            item = itemFromSection(sec, action, null);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (mailId != null && !mailId.isBlank()) meta.getPersistentDataContainer().set(keyMailId, PersistentDataType.STRING, mailId);
            if (senderUuid != null && !senderUuid.isBlank()) meta.getPersistentDataContainer().set(keyMailSender, PersistentDataType.STRING, senderUuid);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setTargetMenuOnItem(Inventory inv, int slot, String target) {
        ItemStack item = inv.getItem(slot);
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyTargetMenu, PersistentDataType.STRING, target);
        item.setItemMeta(meta);
    }

    private void deleteMail(Player player, String id) {
        if (!player.hasPermission("mdvsocial.mail.delete")) { msg(player, "no-permission"); return; }
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            return;
        }
        mailData.set(mailPath(player.getUniqueId(), "letters." + id), null);
        saveMailData();
        msg(player, "mail-deleted");
    }

    private void deleteMailInternal(UUID owner, String id) {
        if (owner == null || id == null || id.isBlank()) return;
        mailData.set(mailPath(owner, "letters." + id), null);
        saveMailData();
    }

    private void handleClanInviteMailAction(Player player, String id, boolean accept, int page) {
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + id);
        String type = mailData.getString(base + ".type", "");
        String clanTag = mailData.getString(base + ".clan-tag", "");
        if (!"MDVCLANS_INVITE".equalsIgnoreCase(type) || clanTag == null || clanTag.isBlank()) {
            msg(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.dispatchCommand(player, accept ? "clan aceptar " + clanTag : "clan rechazar " + clanTag);
            deleteMailInternal(player.getUniqueId(), id);
        });
    }

    private boolean blockMailSender(Player player, String senderUuidText) {
        if (senderUuidText == null || senderUuidText.isBlank()) {
            msg(player, "mail-cannot-block-server");
            return false;
        }
        try {
            UUID uuid = UUID.fromString(senderUuidText);
            addBlockedMail(player.getUniqueId(), uuid);
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            msg(player, "mail-blocked", Map.of("target", off.getName() == null ? "jugador" : off.getName()));
            return true;
        } catch (Exception ignored) {
            msg(player, "mail-cannot-block-server");
            return false;
        }
    }

    private void blockMailByName(Player player, String targetName) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) { sendPlayerNotFound(player, targetName); return; }
        if (target.getUniqueId().equals(player.getUniqueId())) { msg(player, "mail-self-block"); return; }
        addBlockedMail(player.getUniqueId(), target.getUniqueId());
        msg(player, "mail-blocked", Map.of("target", target.getName() == null ? targetName : target.getName()));
    }

    private void unblockMailByName(Player player, String targetName) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) { sendPlayerNotFound(player, targetName); return; }
        List<String> list = new ArrayList<>(mailData.getStringList(mailPath(player.getUniqueId(), "blocked")));
        boolean removed = list.remove(target.getUniqueId().toString());
        mailData.set(mailPath(player.getUniqueId(), "blocked"), list);
        saveMailData();
        msg(player, removed ? "mail-unblocked" : "mail-not-blocked", Map.of("target", target.getName() == null ? targetName : target.getName()));
    }

    private boolean isMailBlocked(UUID recipient, UUID sender) {
        return mailData.getStringList(mailPath(recipient, "blocked")).contains(sender.toString());
    }

    private void addBlockedMail(UUID recipient, UUID sender) {
        List<String> list = new ArrayList<>(mailData.getStringList(mailPath(recipient, "blocked")));
        if (!list.contains(sender.toString())) list.add(sender.toString());
        mailData.set(mailPath(recipient, "blocked"), list);
        saveMailData();
    }

    private void sendBlockedList(Player player) {
        List<String> list = mailData.getStringList(mailPath(player.getUniqueId(), "blocked"));
        if (list.isEmpty()) {
            player.sendMessage(color(getPrefix() + "&7No tienes jugadores bloqueados para cartas."));
            return;
        }
        player.sendMessage(color(getPrefix() + "&eJugadores bloqueados:"));
        for (String uuidText : list) {
            try {
                OfflinePlayer off = Bukkit.getOfflinePlayer(UUID.fromString(uuidText));
                player.sendMessage(color("&8- &f" + (off.getName() == null ? uuidText : off.getName())));
            } catch (Exception ignored) {
                player.sendMessage(color("&8- &f" + uuidText));
            }
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "desconocido";
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(getConfig().getString("mail.date-format", "dd/MM HH:mm")).withZone(ZoneId.systemDefault());
            return fmt.format(Instant.ofEpochMilli(millis));
        } catch (Exception e) {
            return String.valueOf(millis);
        }
    }

    private String daysLeftText(long expiresAt) {
        long diff = expiresAt - System.currentTimeMillis();
        if (expiresAt <= 0) return getConfig().getString("mail.never-expires-text", "no expira");
        if (diff <= 0) return "expirada";
        long days = diff / (24L * 60L * 60L * 1000L);
        long hours = (diff / (60L * 60L * 1000L)) % 24L;
        if (days > 0) return days + "d " + hours + "h";
        return Math.max(1, diff / (60L * 60L * 1000L)) + "h";
    }

    private String shorten(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private List<String> wrapText(String text, int maxLen) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > maxLen) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private void returnToMailSessionMenu(Player player, MailComposeSession session) {
        if (session == null) return;
        String menu = normalize(session.returnMenu);
        if (menu.equals("mailbox") || menu.equals("buzon")) {
            int mailboxPage = Math.max(0, session.returnPage);
            Bukkit.getScheduler().runTask(this, () -> openMailbox(player, mailboxPage));
            return;
        }
        int page = Math.max(1, session.returnPage);
        if (!menu.isBlank() && customMenus.containsKey(menu)) {
            Bukkit.getScheduler().runTask(this, () -> openCustomMenu(player, menu, page, "", 1));
        } else if (customMenus.containsKey("correo")) {
            Bukkit.getScheduler().runTask(this, () -> openCustomMenu(player, "correo", 1, "menuamigos", 1));
        }
    }

    private void sendPlayerNotFound(Player player, String input) {
        List<String> suggestions = similarPlayerSuggestions(input, getConfig().getInt("mail.name-suggestions-limit", 5));
        if (suggestions.isEmpty()) {
            msg(player, "mail-player-not-found");
        } else {
            msg(player, "mail-player-not-found-suggestions", Map.of("suggestions", String.join(", ", suggestions)));
        }
    }

    private List<String> similarPlayerSuggestions(String input, int limit) {
        if (input == null || input.isBlank()) return Collections.emptyList();
        String raw = input.trim();
        String low = raw.toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new HashMap<>();
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            String name = off.getName();
            if (name == null || name.isBlank()) continue;
            String n = name.toLowerCase(Locale.ROOT);
            int score;
            if (n.equals(low)) score = 0;
            else if (n.startsWith(low)) score = 1;
            else if (n.contains(low)) score = 2;
            else {
                int dist = levenshtein(low, n);
                int max = Math.max(2, Math.min(4, Math.max(low.length(), n.length()) / 3));
                if (dist > max) continue;
                score = 10 + dist;
            }
            scores.put(name, score);
        }
        return scores.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).thenComparing(Map.Entry::getKey))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
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

        if (sec != null && mat == Material.PLAYER_HEAD && meta instanceof SkullMeta skull) {
            String texture = readTexture(sec);
            if (texture != null && !texture.isBlank()) {
                applySkullTexture(skull, texture);
            } else {
                String ownerName = sec.getString("head-owner", "");
                if (ownerName != null && !ownerName.isBlank() && !ownerName.contains("{player}")) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
                }
            }
            meta = skull;
        }

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

        if (mat == Material.PLAYER_HEAD) {
            SkullMeta skull = (SkullMeta) item.getItemMeta();
            if (title.texture != null && !title.texture.isBlank()) {
                applySkullTexture(skull, applyPlayerPlaceholders(title.texture, player));
            } else if (title.headOwner != null && !title.headOwner.isBlank()) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(applyPlayerPlaceholders(title.headOwner, player));
                skull.setOwningPlayer(owner);
            }
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
            if (isHiddenTitle(title.id)) continue;
            boolean owned = hasTitle(player, title.id);
            if (type.equals("MY_TITLES") && owned) out.add(title);
            else if (type.equals("SHOP") && title.purchasable && !owned) out.add(title);
            else if (type.equals("LOCKED") && !owned && !title.purchasable) out.add(title);
        }
        return out;
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true)) return;
        Player player = event.getPlayer();
        if (getConfig().getBoolean("scoreboard-party-permission.reset-on-join", true)) {
            setScoreboardPartyPermission(player, false);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> syncScoreboardPartyPermission(player), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true)) return;
        Player player = event.getPlayer();
        setScoreboardPartyPermission(player, false);
        PermissionAttachment attachment = scoreboardPartyAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Throwable ignored) { }
        }
    }

    private void syncAllScoreboardPartyPermissions() {
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncScoreboardPartyPermission(player);
        }
    }

    private void resetAllScoreboardPartyPermissions() {
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            setScoreboardPartyPermission(player, false);
        }
    }

    private void syncScoreboardPartyPermission(Player player) {
        if (player == null || !player.isOnline()) return;
        boolean inParty = getMMOCoreParty(player) != null;
        setScoreboardPartyPermission(player, inParty);
    }

    private void setScoreboardPartyPermission(Player player, boolean value) {
        if (player == null) return;
        String permission = getConfig().getString("scoreboard-party-permission.permission", "animatedscoreboard.party");
        if (permission == null || permission.isBlank()) return;

        PermissionAttachment attachment = scoreboardPartyAttachments.computeIfAbsent(player.getUniqueId(), id -> player.addAttachment(this));
        attachment.setPermission(permission, value);
        player.recalculatePermissions();

        if (getConfig().getBoolean("scoreboard-party-permission.debug", false)) {
            getLogger().info("Scoreboard party permission: " + player.getName() + " -> " + permission + " = " + value);
        }
    }

    private boolean handleExternalFriendOptionsClick(InventoryClickEvent event, Player player) {
        if (!getConfig().getBoolean("social-friend-options.enabled", true)) return false;
        if (event.getClickedInventory() == null) return false;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return false;

        String clickMode = getConfig().getString("social-friend-options.click", "LEFT");
        if (!matchesConfiguredClick(event.getClick(), clickMode)) return false;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return false;
        List<Integer> slots = getConfig().getIntegerList("social-friend-options.slots");
        if (!slots.isEmpty() && !slots.contains(slot)) return false;

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) title = event.getView().getTitle();
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        String equals = ChatColor.stripColor(color(getConfig().getString("social-friend-options.title", ""))).trim().toLowerCase(Locale.ROOT);
        String contains = ChatColor.stripColor(color(getConfig().getString("social-friend-options.title-contains", ""))).trim().toLowerCase(Locale.ROOT);
        boolean titleMatches = (!equals.isBlank() && normalizedTitle.equals(equals)) || (!contains.isBlank() && normalizedTitle.contains(contains));
        if (!titleMatches && equals.isBlank() && contains.isBlank()) titleMatches = true;
        if (!titleMatches) return false;

        ItemStack clicked = event.getCurrentItem();
        UUID targetUuid = extractMMOCoreFriendUuid(clicked);
        if (targetUuid == null) return false;

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null || target.getName().isBlank() ? "jugador" : target.getName();
        boolean targetOnline = Bukkit.getPlayer(targetUuid) != null;

        event.setCancelled(getConfig().getBoolean("social-friend-options.cancel-event", true));
        String targetMenu = normalize(getConfig().getString("social-friend-options.target-menu", "amigo_opciones"));
        Bukkit.getScheduler().runTask(this, () -> openCustomMenu(player, targetMenu, 1, "", 1, targetUuid, targetName, targetOnline));
        return true;
    }

    private boolean matchesConfiguredClick(org.bukkit.event.inventory.ClickType click, String configured) {
        String value = configured == null ? "LEFT" : configured.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "ANY", "ALL" -> true;
            case "LEFT", "LEFT_CLICK" -> click == org.bukkit.event.inventory.ClickType.LEFT;
            case "SHIFT_LEFT", "SHIFT_LEFT_CLICK" -> click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
            case "RIGHT", "RIGHT_CLICK" -> click == org.bukkit.event.inventory.ClickType.RIGHT;
            case "SHIFT_RIGHT", "SHIFT_RIGHT_CLICK" -> click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
            default -> click == org.bukkit.event.inventory.ClickType.LEFT;
        };
    }

    private UUID extractMMOCoreFriendUuid(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
            if (!key.getNamespace().equalsIgnoreCase("mmocore")) continue;
            if (!key.getKey().equalsIgnoreCase("Uuid")) continue;
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value == null || value.isBlank()) return null;
            try {
                return UUID.fromString(value);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean handleExternalGuiClick(InventoryClickEvent event, Player player) {
        if (externalGuiActions.isEmpty()) return false;
        if (event.getClickedInventory() == null) return false;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return false;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return false;

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) title = event.getView().getTitle();

        for (ExternalGuiAction def : externalGuiActions) {
            if (!def.matches(title, slot)) continue;
            if (def.cancelEvent) event.setCancelled(true);
            runExternalGuiCommands(player, def);
            return true;
        }
        return false;
    }

    private void runExternalGuiCommands(Player player, ExternalGuiAction def) {
        Runnable task = () -> {
            if (def.closeOnClick) player.closeInventory();
            for (String raw : def.playerCommands) {
                String cmd = applyPlayerPlaceholders(raw, player).trim();
                if (cmd.isBlank()) continue;
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                Bukkit.dispatchCommand(player, cmd);
            }
            for (String raw : def.consoleCommands) {
                String cmd = applyPlayerPlaceholders(raw, player).trim();
                if (cmd.isBlank()) continue;
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        };
        Bukkit.getScheduler().runTask(this, task);
    }

    private boolean evaluateMenuCondition(Player player, String placeholder, String expected) {
        if (placeholder == null || placeholder.isBlank()) return false;
        String value = applyPlayerPlaceholders(placeholder, player);
        if (expected == null || expected.isBlank()) expected = "true";
        return value.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean shouldCloseOnClick(PersistentDataContainer pdc) {
        String value = pdc.get(keyCloseOnClick, PersistentDataType.STRING);
        return value == null || !value.equalsIgnoreCase("false");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            if (handleExternalFriendOptionsClick(event, player)) return;
            handleExternalGuiClick(event, player);
            return;
        }
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
            case "OPEN_CONDITIONAL_MENU" -> {
                String placeholder = pdc.get(keyConditionPlaceholder, PersistentDataType.STRING);
                String expected = pdc.get(keyConditionEquals, PersistentDataType.STRING);
                String trueMenu = pdc.get(keyTrueMenu, PersistentDataType.STRING);
                String falseMenu = pdc.get(keyFalseMenu, PersistentDataType.STRING);
                boolean result = evaluateMenuCondition(player, placeholder, expected);
                openCustomMenu(player, result ? trueMenu : falseMenu, 1, holder.menuId, holder.page);
            }
            case "MDVCLANS_OPEN" -> {
                if (shouldCloseOnClick(pdc)) player.closeInventory();
                String clansMenu = pdc.get(keyClansMenu, PersistentDataType.STRING);
                if (clansMenu == null || clansMenu.isBlank()) clansMenu = pdc.get(keyTargetMenu, PersistentDataType.STRING);
                if (clansMenu == null || clansMenu.isBlank()) clansMenu = "auto";
                Bukkit.dispatchCommand(player, "clan abrir " + clansMenu);
            }
            case "BACK" -> {
                if (holder.previousMenu != null && !holder.previousMenu.isBlank()) openCustomMenu(player, holder.previousMenu, holder.previousPage, "", 1);
                else openSocialStart(player);
            }
            case "COMMAND_PLAYER" -> runPlayerCommandsFromPdc(player, pdc, holder);
            case "OPEN_MAILBOX" -> openMailbox(player, 0);
            case "START_MAIL_SEND" -> {
                if (shouldCloseOnClick(pdc)) player.closeInventory();
                startMailRecipientPrompt(player, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "START_MAIL_SEND_TARGET" -> {
                if (shouldCloseOnClick(pdc)) player.closeInventory();
                startMailMessagePromptToTarget(player, holder.targetUuid, holder.targetName, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "INVITE_PARTY_TARGET" -> {
                if (shouldCloseOnClick(pdc)) player.closeInventory();
                inviteFriendToParty(player, holder.targetUuid, holder.targetName);
            }
            case "START_MAIL_BLOCK" -> {
                if (shouldCloseOnClick(pdc)) player.closeInventory();
                startMailBlockPrompt(player, true, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "START_MAIL_UNBLOCK" -> {
                if (shouldCloseOnClick(pdc)) player.closeInventory();
                startMailBlockPrompt(player, false, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
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
            case "READ_MAIL" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                openMailRead(player, mailId, holder.page);
            }
            case "DELETE_MAIL" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                deleteMail(player, mailId);
                openMailbox(player, holder.page);
            }
            case "MAIL_BACK" -> openMailbox(player, holder.page);
            case "REPLY_MAIL" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                startMailReplyFromMail(player, mailId, holder.page);
            }
            case "BLOCK_MAIL_SENDER" -> {
                String senderUuid = pdc.get(keyMailSender, PersistentDataType.STRING);
                if (blockMailSender(player, senderUuid)) player.closeInventory();
            }
            case "ACCEPT_CLAN_INVITE" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                handleClanInviteMailAction(player, mailId, true, holder.page);
            }
            case "REJECT_CLAN_INVITE" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                handleClanInviteMailAction(player, mailId, false, holder.page);
            }
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
        else if (type.equals("MAILBOX")) openMailbox(player, page);
        else if (type.equals("MY_TITLES") || type.equals("SHOP") || type.equals("LOCKED")) openTitleList(player, type, page);
    }


    private void runPlayerCommandsFromPdc(Player player, PersistentDataContainer pdc) {
        runPlayerCommandsFromPdc(player, pdc, null);
    }

    private void runPlayerCommandsFromPdc(Player player, PersistentDataContainer pdc, MenuHolder holder) {
        String raw = pdc.get(keyCommands, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return;
        String close = pdc.get(keyCloseOnClick, PersistentDataType.STRING);
        if (close == null || Boolean.parseBoolean(close)) player.closeInventory();
        UUID targetUuid = holder == null ? readUuidFromPdc(pdc) : holder.targetUuid;
        String targetName = holder == null ? pdc.get(keyFriendTargetName, PersistentDataType.STRING) : holder.targetName;
        boolean targetOnline = holder != null && holder.targetOnline;
        String onlineRaw = pdc.get(keyFriendTargetOnline, PersistentDataType.STRING);
        if (holder == null && onlineRaw != null) targetOnline = Boolean.parseBoolean(onlineRaw);
        for (String line : raw.split("\\n")) {
            String cmd = applyTargetPlaceholders(line, player, targetUuid, targetName, targetOnline).trim();
            if (cmd.isBlank()) continue;
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }

    private UUID readUuidFromPdc(PersistentDataContainer pdc) {
        String raw = pdc.get(keyFriendTargetUuid, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (Exception ignored) { return null; }
    }

    private void inviteFriendToParty(Player player, UUID targetUuid, String fallbackName) {
        if (targetUuid == null) { msg(player, "social-target-not-found"); return; }
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        String targetName = targetPlayer != null ? targetPlayer.getName() : (fallbackName == null || fallbackName.isBlank() ? "jugador" : fallbackName);
        if (targetPlayer == null) {
            msg(player, "party-target-offline", Map.of("target", targetName));
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            msg(player, "party-self");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            msg(player, "party-mmocore-missing");
            return;
        }

        try {
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Method getData = playerDataClass.getMethod("get", OfflinePlayer.class);
            Object playerData = getData.invoke(null, player);
            Object targetData = getData.invoke(null, targetPlayer);
            Method getParty = playerDataClass.getMethod("getParty");
            Object party = getParty.invoke(playerData);

            if (party == null) {
                String behavior = getConfig().getString("social-friend-options.party.when-no-party", "message");
                if (behavior != null && (behavior.equalsIgnoreCase("create") || behavior.equalsIgnoreCase("auto-create") || behavior.equalsIgnoreCase("create-and-invite") || behavior.equalsIgnoreCase("create_and_invite"))) {
                    party = createMMOCoreParty(playerData);
                    if (party != null) {
                        msg(player, "party-auto-created");
                        syncScoreboardPartyPermission(player);
                    }
                } else {
                    msg(player, "party-must-create", Map.of("target", targetName));
                    return;
                }
            }

            if (party == null) {
                msg(player, "party-error");
                return;
            }

            try {
                Method hasMember = party.getClass().getMethod("hasMember", UUID.class);
                Object already = hasMember.invoke(party, targetUuid);
                if (already instanceof Boolean b && b) {
                    msg(player, "party-target-already-member", Map.of("target", targetName));
                    return;
                }
            } catch (NoSuchMethodException ignored) { }

            int max = Math.max(2, getConfig().getInt("social-friend-options.party.max-members", 5));
            try {
                Method countMembers = party.getClass().getMethod("countMembers");
                Object count = countMembers.invoke(party);
                if (count instanceof Number n && n.intValue() >= max) {
                    msg(player, "party-full", Map.of("max", String.valueOf(max)));
                    return;
                }
            } catch (NoSuchMethodException ignored) { }

            Method invite;
            try {
                invite = party.getClass().getMethod("sendPartyInvite", playerDataClass, playerDataClass);
            } catch (NoSuchMethodException ignored) {
                invite = party.getClass().getMethod("sendInvite", playerDataClass, playerDataClass);
            }
            invite.invoke(party, playerData, targetData);
            syncScoreboardPartyPermission(player);
            msg(player, "party-invite-sent", Map.of("target", targetName));
        } catch (Throwable ex) {
            getLogger().warning("No se pudo invitar amigo a party: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            msg(player, "party-error");
        }
    }

    private Object createMMOCoreParty(Object playerData) throws Exception {
        Class<?> mmocoreClass = Class.forName("net.Indyuce.mmocore.MMOCore");
        Object mmocore = mmocoreClass.getField("plugin").get(null);
        if (mmocore == null) return null;
        Object partyModule = mmocoreClass.getField("partyModule").get(mmocore);
        if (partyModule == null) return null;
        Method create = partyModule.getClass().getMethod("newRegisteredParty", playerData.getClass());
        return create.invoke(partyModule, playerData);
    }

    private Object getMMOCorePlayerData(Player player) throws Exception {
        Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
        Method getData = playerDataClass.getMethod("get", OfflinePlayer.class);
        return getData.invoke(null, player);
    }

    private Object getMMOCoreParty(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) return null;
        try {
            Object playerData = getMMOCorePlayerData(player);
            if (playerData == null) return null;
            Method getParty = playerData.getClass().getMethod("getParty");
            return getParty.invoke(playerData);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getConfiguredPartyMaxMembers() {
        return Math.max(2, getConfig().getInt("social-friend-options.party.max-members", 5));
    }

    private int countMMOCorePartyMembers(Object party) {
        if (party == null) return 0;
        try {
            Method countMembers = party.getClass().getMethod("countMembers");
            Object count = countMembers.invoke(party);
            if (count instanceof Number n) return Math.max(0, n.intValue());
        } catch (Throwable ignored) { }
        return getMMOCorePartyMemberNames(party).size();
    }

    private List<String> getMMOCorePartyMemberNames(Object party) {
        if (party == null) return Collections.emptyList();
        List<?> rawMembers = Collections.emptyList();

        String[] memberMethods = {"getOnlineMembers", "getMembers"};
        for (String methodName : memberMethods) {
            try {
                Method method = party.getClass().getMethod(methodName);
                Object result = method.invoke(party);
                if (result instanceof Iterable<?> iterable) {
                    List<Object> collected = new ArrayList<>();
                    for (Object value : iterable) collected.add(value);
                    if (!collected.isEmpty()) {
                        rawMembers = collected;
                        break;
                    }
                }
            } catch (Throwable ignored) { }
        }

        if (rawMembers.isEmpty()) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (Object member : rawMembers) {
            String name = getMMOCorePlayerDataName(member);
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names;
    }

    private String getMMOCorePlayerDataName(Object playerData) {
        if (playerData == null) return null;

        String[] objectMethods = {"getPlayer", "getOfflinePlayer", "getBukkitPlayer", "getProfile"};
        for (String methodName : objectMethods) {
            try {
                Method method = playerData.getClass().getMethod(methodName);
                Object value = method.invoke(playerData);
                String name = extractPlayerLikeName(value);
                if (name != null && !name.isBlank()) return name;
            } catch (Throwable ignored) { }
        }

        String[] stringMethods = {"getName", "getPlayerName", "getUsername"};
        for (String methodName : stringMethods) {
            try {
                Method method = playerData.getClass().getMethod(methodName);
                Object value = method.invoke(playerData);
                if (value instanceof String str && !str.isBlank()) return str;
            } catch (Throwable ignored) { }
        }

        String[] uuidMethods = {"getUniqueId", "getUniqueID", "getUUID", "getUuid"};
        for (String methodName : uuidMethods) {
            try {
                Method method = playerData.getClass().getMethod(methodName);
                Object value = method.invoke(playerData);
                if (value instanceof UUID uuid) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                    return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
                }
            } catch (Throwable ignored) { }
        }

        return null;
    }

    private String extractPlayerLikeName(Object value) {
        if (value == null) return null;
        if (value instanceof Player player) return player.getName();
        if (value instanceof OfflinePlayer offlinePlayer) return offlinePlayer.getName();

        try {
            Method getName = value.getClass().getMethod("getName");
            Object name = getName.invoke(value);
            if (name instanceof String str && !str.isBlank()) return str;
        } catch (Throwable ignored) { }

        try {
            Method getUniqueId = value.getClass().getMethod("getUniqueId");
            Object uuid = getUniqueId.invoke(value);
            if (uuid instanceof UUID id) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                return offline.getName() == null ? id.toString().substring(0, 8) : offline.getName();
            }
        } catch (Throwable ignored) { }

        return null;
    }

    private String partyScoreboardPlaceholder(Player player, String key) {
        Object party = getMMOCoreParty(player);
        int max = getConfiguredPartyMaxMembers();

        if (party == null) {
            return switch (key) {
                case "party_in_group", "party_in_party" -> "false";
                case "party_max" -> String.valueOf(max);
                case "party_count" -> "0";
                default -> "";
            };
        }

        int count = countMMOCorePartyMembers(party);
        List<String> members = getMMOCorePartyMemberNames(party);

        if (key.equals("party_header")) return color("&dGrupo: &f" + count + "&7/&f" + max);
        if (key.equals("party_count")) return String.valueOf(count);
        if (key.equals("party_max")) return String.valueOf(max);
        if (key.equals("party_in_group") || key.equals("party_in_party")) return "true";
        if (key.equals("party_spacer")) return " ";
        if (key.equals("party_members")) return members.isEmpty() ? "" : String.join(", ", members);

        if (key.startsWith("party_member_")) {
            try {
                int index = Integer.parseInt(key.substring("party_member_".length())) - 1;
                if (index < 0 || index >= members.size()) return "";
                return color("&7• &f" + members.get(index));
            } catch (NumberFormatException ignored) {
                return "";
            }
        }

        return "";
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
        if (!title.playerEquippable) {
            msg(player, "title-not-player-equippable");
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
        if (!allowClearTitle()) {
            msg(player, "title-clear-disabled");
            return;
        }
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
        if (titleId.equals(getDefaultTitleId())) return true;
        if (getDefaultUnlockedTitles().contains(titleId)) return true;
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
        if (getActiveTitleId(uuid).equals(titleId)) data.set(path(uuid, "active"), getClearTargetTitleId());
        saveData();
    }

    private Set<String> getUnlockedTitles(UUID uuid) {
        List<String> list = data.getStringList(path(uuid, "unlocked"));
        return list.stream().map(this::normalize).collect(Collectors.toCollection(HashSet::new));
    }

    public String getActiveTitleId(UUID uuid) {
        String active = normalize(data.getString(path(uuid, "active"), ""));
        if (active.isBlank() && isMandatoryTitle()) {
            String def = getDefaultTitleId();
            if (!def.isBlank() && titles.containsKey(def)) return def;
        }
        return active;
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

    /**
     * API publica para otros plugins: devuelve el titulo equipado de cualquier jugador por UUID.
     * Para jugadores offline no se revalidan permisos dinamicos, solo se lee el titulo guardado/default.
     */
    public TitleDef getEquippedTitle(UUID uuid) {
        if (uuid == null) return null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return getActiveTitle(online);
        String id = getActiveTitleId(uuid);
        if (id.isBlank()) return null;
        return titles.get(id);
    }

    public String getEquippedTitleId(UUID uuid) {
        TitleDef title = getEquippedTitle(uuid);
        return title == null ? "" : title.id;
    }

    public String getEquippedTitleDisplay(UUID uuid, boolean colored) {
        TitleDef title = getEquippedTitle(uuid);
        if (title == null) return "";
        return colored ? color(title.display) : stripColor(title.display);
    }

    public String getEquippedTitlePrefix(UUID uuid, boolean colored) {
        TitleDef title = getEquippedTitle(uuid);
        if (title == null) return "";
        return colored ? color(title.prefix) : stripColor(title.prefix);
    }

    public int countUnlocked(UUID uuid) {
        Set<String> all = new HashSet<>(getDefaultUnlockedTitles());
        all.addAll(getUnlockedTitles(uuid));
        all.removeIf(this::isHiddenTitle);
        return all.size();
    }

    private boolean isMandatoryTitle() {
        return getConfig().getBoolean("settings.mandatory-title", false);
    }

    private boolean allowClearTitle() {
        return getConfig().getBoolean("settings.allow-clear-title", !isMandatoryTitle());
    }

    private String getDefaultTitleId() {
        return normalize(getConfig().getString("settings.default-title", ""));
    }

    private String getClearTargetTitleId() {
        if (!isMandatoryTitle()) return "";
        String def = getDefaultTitleId();
        return titles.containsKey(def) ? def : "";
    }

    private Set<String> getDefaultUnlockedTitles() {
        return getConfig().getStringList("settings.default-unlocked-titles")
                .stream()
                .map(this::normalize)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean isHiddenTitle(String titleId) {
        titleId = normalize(titleId);
        TitleDef def = titles.get(titleId);
        if (def != null && def.hidden) return true;
        if (getConfig().getBoolean("settings.hide-default-title-in-menus", true) && titleId.equals(getDefaultTitleId())) return true;
        for (String hidden : getConfig().getStringList("settings.hidden-titles")) {
            if (titleId.equals(normalize(hidden))) return true;
        }
        return false;
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
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("social")) {
            if (args.length == 1) {
                List<String> menus = new ArrayList<>(customMenus.keySet());
                menus.addAll(Arrays.asList("main", "titulos", "mis_titulos", "tienda", "rangos"));
                return partial(args[0], menus);
            }
            return Collections.emptyList();
        }

        if (commandName.equals("correo") || commandName.equals("carta")) {
            if (args.length == 1) return partial(args[0], Arrays.asList("buzon", "enviar", "bloquear", "desbloquear", "bloqueados", "cancelar"));
            if (args.length == 2 && Arrays.asList("enviar", "bloquear", "desbloquear").contains(args[0].toLowerCase(Locale.ROOT))) {
                return null;
            }
            return Collections.emptyList();
        }

        if (!commandName.equals("mdvsocial")) return Collections.emptyList();
        if (args.length == 1) return partial(args[0], Arrays.asList("reload", "open", "title", "mail"));
        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            List<String> menus = new ArrayList<>(customMenus.keySet());
            menus.addAll(Arrays.asList("main", "titulos", "mis_titulos", "tienda", "rangos"));
            return partial(args[2], menus);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mail")) {
            return partial(args[1], Arrays.asList("sendall", "sendall-days", "sendall-never"));
        }
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
        final UUID targetUuid;
        final String targetName;
        final boolean targetOnline;
        Inventory inventory;

        MenuHolder(String type, int page) {
            this(type, page, "", "", 1, null, "", false);
        }

        MenuHolder(String type, int page, String menuId, String previousMenu, int previousPage) {
            this(type, page, menuId, previousMenu, previousPage, null, "", false);
        }

        MenuHolder(String type, int page, String menuId, String previousMenu, int previousPage, UUID targetUuid, String targetName, boolean targetOnline) {
            this.type = type;
            this.page = page;
            this.menuId = menuId == null ? "" : menuId;
            this.previousMenu = previousMenu == null ? "" : previousMenu;
            this.previousPage = previousPage <= 0 ? 1 : previousPage;
            this.targetUuid = targetUuid;
            this.targetName = targetName == null ? "" : targetName;
            this.targetOnline = targetOnline;
        }
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class ExternalGuiAction {
        final String id;
        final String titleEquals;
        final String titleContains;
        final List<Integer> slots;
        final List<String> playerCommands;
        final List<String> consoleCommands;
        final boolean closeOnClick;
        final boolean cancelEvent;

        ExternalGuiAction(String id, String titleEquals, String titleContains, List<Integer> slots, List<String> playerCommands, List<String> consoleCommands, boolean closeOnClick, boolean cancelEvent) {
            this.id = id == null ? "" : id;
            this.titleEquals = titleEquals == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', titleEquals)).trim().toLowerCase(Locale.ROOT);
            this.titleContains = titleContains == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', titleContains)).trim().toLowerCase(Locale.ROOT);
            this.slots = slots == null ? Collections.emptyList() : slots;
            this.playerCommands = playerCommands == null ? Collections.emptyList() : playerCommands;
            this.consoleCommands = consoleCommands == null ? Collections.emptyList() : consoleCommands;
            this.closeOnClick = closeOnClick;
            this.cancelEvent = cancelEvent;
        }

        boolean matches(String rawTitle, int slot) {
            if (!slots.contains(slot)) return false;
            String title = rawTitle == null ? "" : rawTitle.trim().toLowerCase(Locale.ROOT);
            if (!titleEquals.isBlank() && title.equals(titleEquals)) return true;
            if (!titleContains.isBlank() && title.contains(titleContains)) return true;
            return titleEquals.isBlank() && titleContains.isBlank();
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
        final String texture;
        final String action;
        final String targetMenu;
        final List<String> commands;
        final boolean closeOnClick;
        final String visibleWhen;
        final String conditionPlaceholder;
        final String conditionEquals;
        final String trueMenu;
        final String falseMenu;
        final String clansMenu;
        final boolean useClanBanner;

        CustomMenuItem(String id, int slot, String material, int amount, String name, List<String> lore, String headOwner, String texture, String action, String targetMenu, List<String> commands, boolean closeOnClick, String visibleWhen, String conditionPlaceholder, String conditionEquals, String trueMenu, String falseMenu, String clansMenu, boolean useClanBanner) {
            this.id = id;
            this.slot = slot;
            this.material = material == null ? "PAPER" : material;
            this.amount = amount;
            this.name = name == null ? "" : name;
            this.lore = lore == null ? Collections.emptyList() : lore;
            this.headOwner = headOwner == null ? "" : headOwner;
            this.texture = texture == null ? "" : texture;
            this.action = action == null ? "" : action;
            this.targetMenu = targetMenu == null ? "" : targetMenu;
            this.commands = commands == null ? Collections.emptyList() : commands;
            this.closeOnClick = closeOnClick;
            this.visibleWhen = visibleWhen == null ? "always" : visibleWhen.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            this.conditionPlaceholder = conditionPlaceholder == null ? "" : conditionPlaceholder;
            this.conditionEquals = conditionEquals == null ? "true" : conditionEquals;
            this.trueMenu = trueMenu == null ? "" : trueMenu;
            this.falseMenu = falseMenu == null ? "" : falseMenu;
            this.clansMenu = clansMenu == null ? "" : clansMenu;
            this.useClanBanner = useClanBanner;
        }

        boolean isVisible(UUID targetUuid, boolean targetOnline) {
            return switch (visibleWhen) {
                case "online", "target_online", "friend_online" -> targetUuid != null && targetOnline;
                case "offline", "target_offline", "friend_offline" -> targetUuid != null && !targetOnline;
                case "target", "has_target", "friend", "has_friend" -> targetUuid != null;
                default -> true;
            };
        }
    }


    enum MailStage { RECIPIENT, MESSAGE, BLOCK, UNBLOCK }

    static final class MailComposeSession {
        final MailStage stage;
        final String targetName;
        final UUID targetUuid;
        final String returnMenu;
        final int returnPage;
        MailComposeSession(MailStage stage, String targetName) {
            this(stage, targetName, null, "correo", 1);
        }
        MailComposeSession(MailStage stage, String targetName, String returnMenu, int returnPage) {
            this(stage, targetName, null, returnMenu, returnPage);
        }
        MailComposeSession(MailStage stage, String targetName, UUID targetUuid, String returnMenu, int returnPage) {
            this.stage = stage;
            this.targetName = targetName;
            this.targetUuid = targetUuid;
            String normalizedReturnMenu = returnMenu == null ? "correo" : returnMenu;
            this.returnMenu = normalizedReturnMenu;
            this.returnPage = normalizedReturnMenu.equalsIgnoreCase("MAILBOX") || normalizedReturnMenu.equalsIgnoreCase("buzon")
                    ? Math.max(0, returnPage)
                    : (returnPage <= 0 ? 1 : returnPage);
        }
    }

    public static final class TitleDef {
        public final String id;
        public final String display;
        public final String prefix;
        public final String material;
        public final String headOwner;
        public final String texture;
        public final boolean purchasable;
        public final double price;
        public final String unlockPermission;
        public final boolean hidden;
        public final boolean playerEquippable;
        public final List<String> lore;

        TitleDef(String id, String display, String prefix, String material, String headOwner, String texture, boolean purchasable, double price, String unlockPermission, boolean hidden, boolean playerEquippable, List<String> lore) {
            this.id = id;
            this.display = display;
            this.prefix = prefix;
            this.material = material;
            this.headOwner = headOwner;
            this.texture = texture == null ? "" : texture;
            this.purchasable = purchasable;
            this.price = price;
            this.unlockPermission = unlockPermission;
            this.hidden = hidden;
            this.playerEquippable = playerEquippable;
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
            return handlePlaceholder(player, params);
        }

        @Override
        public String onRequest(OfflinePlayer offlinePlayer, String params) {
            return handlePlaceholder(offlinePlayer, params);
        }

        private String handlePlaceholder(OfflinePlayer viewer, String params) {
            if (viewer == null || params == null) return "";
            String p = params.toLowerCase(Locale.ROOT);

            UUID viewerUuid = viewer.getUniqueId();
            TitleDef active = viewer instanceof Player online ? plugin.getActiveTitle(online) : plugin.getEquippedTitle(viewerUuid);

            String targetValue;
            if ((targetValue = afterPrefix(p, "title_of_")) != null) return plugin.getEquippedTitleDisplay(resolveTarget(targetValue), false);
            if ((targetValue = afterPrefix(p, "title_colored_of_")) != null) return plugin.getEquippedTitleDisplay(resolveTarget(targetValue), true);
            if ((targetValue = afterPrefix(p, "title_prefix_of_")) != null) return plugin.getEquippedTitlePrefix(resolveTarget(targetValue), true);
            if ((targetValue = afterPrefix(p, "title_prefix_plain_of_")) != null) return plugin.getEquippedTitlePrefix(resolveTarget(targetValue), false);
            if ((targetValue = afterPrefix(p, "title_id_of_")) != null) return plugin.getEquippedTitleId(resolveTarget(targetValue));
            if ((targetValue = afterPrefix(p, "active_title_of_")) != null) return plugin.getEquippedTitleId(resolveTarget(targetValue));

            return switch (p) {
                case "title" -> active == null ? "" : ChatColor.stripColor(plugin.color(active.display));
                case "title_colored" -> active == null ? "" : plugin.color(active.display);
                case "title_prefix" -> active == null ? "" : plugin.color(active.prefix);
                case "title_prefix_plain" -> active == null ? "" : ChatColor.stripColor(plugin.color(active.prefix));
                case "active_title", "title_id" -> active == null ? "" : active.id;
                case "unlocked_titles" -> String.valueOf(plugin.countUnlocked(viewerUuid));
                case "party_header", "party_count", "party_max", "party_in_group", "party_in_party", "party_spacer", "party_members" ->
                        viewer instanceof Player online ? plugin.partyScoreboardPlaceholder(online, p) : "";
                default -> p.startsWith("party_member_") && viewer instanceof Player online ? plugin.partyScoreboardPlaceholder(online, p) : "";
            };
        }

        private String afterPrefix(String value, String prefix) {
            return value.startsWith(prefix) ? value.substring(prefix.length()) : null;
        }

        private UUID resolveTarget(String token) {
            if (token == null || token.isBlank()) return null;
            String raw = token.trim();
            if (raw.startsWith("uuid_")) raw = raw.substring("uuid_".length());
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(raw);
                return offline == null ? null : offline.getUniqueId();
            }
        }
    }
}
