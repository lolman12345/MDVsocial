package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Menu personal de hogares usando Essentials como backend.
 *
 * MDVSocial solo muestra/ordena la interfaz y ejecuta /home, /sethome y /delhome
 * como jugador. Essentials conserva cooldowns, teleports, permisos y datos reales.
 */
public final class PlayerHomesMenuManager implements Listener, CommandExecutor, TabCompleter {

    private static final String CONFIG_PATH = "homes-menu";

    private final MDVSocialPlugin plugin;
    private org.bukkit.NamespacedKey keyAction;
    private org.bukkit.NamespacedKey keyHomeName;
    private org.bukkit.NamespacedKey keyHomeNumber;

    private boolean enabled;
    private String usePermission;
    private int size;
    private String title;
    private int maxVisibleHomes;
    private String defaultHomeNameFormat;
    private String userdataPath;
    private boolean sortHomesByName;
    private boolean closeOnAction;
    private int defaultMaxHomes;

    private String teleportCommand;
    private String setCommand;
    private String deleteCommand;

    public PlayerHomesMenuManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
        this.keyAction = new org.bukkit.NamespacedKey(plugin, "homes_action");
        this.keyHomeName = new org.bukkit.NamespacedKey(plugin, "homes_name");
        this.keyHomeNumber = new org.bukkit.NamespacedKey(plugin, "homes_number");
    }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("casa");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("No se pudo registrar /casa porque no existe en plugin.yml.");
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(CONFIG_PATH + ".enabled", true);
        usePermission = config.getString(CONFIG_PATH + ".use-permission", "mdvsocial.homes.use");
        size = normalizeMenuSize(config.getInt(CONFIG_PATH + ".size", 54));
        title = config.getString(CONFIG_PATH + ".title", "&8Casas &8- &6&l{player}");
        maxVisibleHomes = Math.max(1, Math.min(9, config.getInt(CONFIG_PATH + ".max-visible-homes", 4)));
        defaultHomeNameFormat = config.getString(CONFIG_PATH + ".default-home-name-format", "casa{number}");
        userdataPath = config.getString(CONFIG_PATH + ".essentials-userdata-path", "plugins/Essentials/userdata");
        sortHomesByName = config.getBoolean(CONFIG_PATH + ".sort-homes-by-name", false);
        closeOnAction = config.getBoolean(CONFIG_PATH + ".close-on-action", true);
        defaultMaxHomes = Math.max(0, config.getInt(CONFIG_PATH + ".max-homes.default", 1));

        teleportCommand = normalizeCommand(config.getString(CONFIG_PATH + ".commands.teleport", "home {home_name}"));
        setCommand = normalizeCommand(config.getString(CONFIG_PATH + ".commands.set", "sethome {home_name}"));
        deleteCommand = normalizeCommand(config.getString(CONFIG_PATH + ".commands.delete", "delhome {home_name}"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(getMessage("only-players", "&cSolo jugadores.")));
            return true;
        }
        if (!enabled) {
            sendMessage(player, "disabled", "&cEl menu de casas esta desactivado.", Map.of());
            return true;
        }
        if (usePermission != null && !usePermission.isBlank() && !player.hasPermission(usePermission)) {
            sendMessage(player, "no-permission", "&cNo tienes permiso para usar este menu.", Map.of());
            return true;
        }
        openHomesMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    private void openHomesMenu(Player player) {
        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        Inventory inv = Bukkit.createInventory(new HomesHolder(), size, color(applyGlobalPlaceholders(title, player, homes, maxHomes)));
        fill(inv, player, homes, maxHomes);

        inv.setItem(slot("items.info.slot", 13), itemFromPath("items.info", "INFO", null, 0, player, homes, maxHomes));

        for (int i = 1; i <= maxVisibleHomes; i++) {
            HomeData home = i <= homes.size() ? homes.get(i - 1) : null;
            boolean unlocked = i <= maxHomes;
            int teleportSlot = slot("items.teleport.slot-" + i, defaultTeleportSlot(i));
            int setSlot = slot("items.set.slot-" + i, defaultSetSlot(i));

            if (!unlocked) {
                inv.setItem(teleportSlot, itemFromPath("items.teleport.locked", "LOCKED", null, i, player, homes, maxHomes));
                inv.setItem(setSlot, itemFromPath("items.set.locked", "LOCKED", null, i, player, homes, maxHomes));
                continue;
            }

            if (home == null) {
                String generatedName = generateHomeName(i);
                inv.setItem(teleportSlot, itemFromPath("items.teleport.missing", "MISSING", HomeData.missing(generatedName), i, player, homes, maxHomes));
                inv.setItem(setSlot, itemFromPath("items.set.available", "SET_HOME", HomeData.missing(generatedName), i, player, homes, maxHomes));
            } else {
                inv.setItem(teleportSlot, itemFromPath("items.teleport.available", "TELEPORT_HOME", home, i, player, homes, maxHomes));
                inv.setItem(setSlot, itemFromPath("items.set.available", "SET_HOME", home, i, player, homes, maxHomes));
            }
        }

        ConfigurationSection back = section("items.back");
        if (back == null || back.getBoolean("enabled", true)) {
            int backSlot = slot("items.back.slot", Math.min(size - 5, 49));
            if (backSlot >= 0 && backSlot < size) {
                inv.setItem(backSlot, itemFromPath("items.back", "BACK", null, 0, player, homes, maxHomes));
            }
        }

        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof HomesHolder)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(keyAction, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;

        String homeName = pdc.get(keyHomeName, PersistentDataType.STRING);
        Integer homeNumber = pdc.get(keyHomeNumber, PersistentDataType.INTEGER);
        if (homeNumber == null) homeNumber = 0;

        switch (action) {
            case "TELEPORT_HOME" -> runHomeCommand(player, teleportCommand, homeName, homeNumber, true);
            case "SET_HOME" -> {
                if (isDeleteClick(event.getClick())) {
                    if (homeExists(player, homeName)) {
                        runHomeCommand(player, deleteCommand, homeName, homeNumber, true);
                    } else {
                        sendMessage(player, "home-not-set", "&cEse hogar aun no esta establecido.", Map.of("home", safe(homeName), "number", String.valueOf(homeNumber)));
                    }
                } else {
                    runHomeCommand(player, setCommand, homeName, homeNumber, true);
                }
            }
            case "BACK" -> {
                if (closeOnAction) player.closeInventory();
                String command = normalizeCommand(sectionString("items.back.command", "social"));
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, command));
            }
            default -> { }
        }
    }

    private boolean isDeleteClick(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }

    private void runHomeCommand(Player player, String commandTemplate, String homeName, int homeNumber, boolean requireName) {
        if (requireName && (homeName == null || homeName.isBlank())) {
            sendMessage(player, "home-not-set", "&cEse hogar aun no esta establecido.", Map.of("home", "", "number", String.valueOf(homeNumber)));
            return;
        }
        if (closeOnAction) player.closeInventory();
        String command = commandTemplate
                .replace("{player}", player.getName())
                .replace("{home}", safe(homeName))
                .replace("{home_name}", safe(homeName))
                .replace("{home_number}", String.valueOf(homeNumber));
        if (command.startsWith("/")) command = command.substring(1);
        final String finalCommand = command;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, finalCommand));
    }

    private boolean homeExists(Player player, String homeName) {
        if (homeName == null || homeName.isBlank()) return false;
        for (HomeData home : readHomes(player)) {
            if (home.name.equalsIgnoreCase(homeName)) return true;
        }
        return false;
    }

    private List<HomeData> readHomes(Player player) {
        File file = new File(resolveUserdataFolder(), player.getUniqueId().toString() + ".yml");
        if (!file.exists()) return new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection homesSec = yaml.getConfigurationSection("homes");
        if (homesSec == null) return new ArrayList<>();

        List<HomeData> out = new ArrayList<>();
        for (String name : homesSec.getKeys(false)) {
            ConfigurationSection homeSec = homesSec.getConfigurationSection(name);
            if (homeSec != null) {
                out.add(new HomeData(
                        name,
                        homeSec.getString("world", homeSec.getString("world-name", "world")),
                        homeSec.getDouble("x", homeSec.getDouble("loc.x", 0)),
                        homeSec.getDouble("y", homeSec.getDouble("loc.y", 0)),
                        homeSec.getDouble("z", homeSec.getDouble("loc.z", 0)),
                        true
                ));
                continue;
            }

            String raw = homesSec.getString(name, "");
            HomeData parsed = parseLegacyHome(name, raw);
            if (parsed != null) out.add(parsed);
        }
        if (sortHomesByName) out.sort(Comparator.comparing(h -> h.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    private HomeData parseLegacyHome(String name, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length < 4) return new HomeData(name, "world", 0, 0, 0, true);
        try {
            return new HomeData(name, parts[0].trim(), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()), true);
        } catch (Exception ignored) {
            return new HomeData(name, "world", 0, 0, 0, true);
        }
    }

    private File resolveUserdataFolder() {
        if (userdataPath == null || userdataPath.isBlank()) return new File("plugins/Essentials/userdata");
        File file = new File(userdataPath);
        if (file.isAbsolute()) return file;
        return new File(Bukkit.getWorldContainer(), userdataPath);
    }

    private int getMaxHomes(Player player) {
        int max = defaultMaxHomes;

        List<Map<?, ?>> list = plugin.getConfig().getMapList(CONFIG_PATH + ".max-homes.permission-limits");
        for (Map<?, ?> entry : list) {
            Object permObj = entry.get("permission");
            Object homesObj = entry.get("homes");
            if (homesObj == null) homesObj = entry.get("max");
            String permission = permObj == null ? "" : String.valueOf(permObj);
            int homes = parseInt(homesObj, -1);
            if (!permission.isBlank() && homes > max && player.hasPermission(permission)) max = homes;
        }

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(CONFIG_PATH + ".max-homes.permissions");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int homes = sec.getInt(key, -1);
                if (homes > max && player.hasPermission(key)) max = homes;
            }
        }

        return Math.max(0, Math.min(maxVisibleHomes, max));
    }

    private int parseInt(Object value, int def) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return def; }
    }

    private String generateHomeName(int number) {
        return safe(defaultHomeNameFormat).replace("{number}", String.valueOf(number)).replace("{home_number}", String.valueOf(number));
    }

    private ItemStack itemFromPath(String relativePath, String action, HomeData home, int number, Player player, List<HomeData> homes, int maxHomes) {
        ConfigurationSection sec = section(relativePath);
        String matName = sec == null ? "PAPER" : sec.getString("material", "PAPER");
        Material material = Material.matchMaterial(matName == null ? "PAPER" : matName.toUpperCase(Locale.ROOT));
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, sec == null ? 1 : sec.getInt("amount", 1))));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (sec != null && material == Material.PLAYER_HEAD && meta instanceof SkullMeta skull) {
            String texture = readTexture(sec);
            if (!texture.isBlank()) {
                applySkullTexture(skull, texture);
            } else {
                String owner = sec.getString("head-owner", "");
                if (owner != null && !owner.isBlank()) {
                    owner = applyPlaceholders(owner, player, home, number, homes, maxHomes);
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
                    skull.setOwningPlayer(offline);
                }
            }
            meta = skull;
        }

        String name = sec == null ? "" : sec.getString("name", "");
        if (name != null && !name.isBlank()) meta.setDisplayName(color(applyPlaceholders(name, player, home, number, homes, maxHomes)));

        List<String> lore = sec == null ? Collections.emptyList() : sec.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(line -> color(applyPlaceholders(line, player, home, number, homes, maxHomes))).collect(Collectors.toList()));
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (home != null && home.name != null && !home.name.isBlank()) meta.getPersistentDataContainer().set(keyHomeName, PersistentDataType.STRING, home.name);
        if (number > 0) meta.getPersistentDataContainer().set(keyHomeNumber, PersistentDataType.INTEGER, number);
        item.setItemMeta(meta);
        return item;
    }

    private String applyGlobalPlaceholders(String input, Player player, List<HomeData> homes, int maxHomes) {
        return safe(input)
                .replace("{player}", player.getName())
                .replace("{homes}", String.valueOf(homes.size()))
                .replace("{home_count}", String.valueOf(homes.size()))
                .replace("{max_homes}", String.valueOf(maxHomes))
                .replace("{max_visible_homes}", String.valueOf(maxVisibleHomes));
    }

    private String applyPlaceholders(String input, Player player, HomeData home, int number, List<HomeData> homes, int maxHomes) {
        if (home == null) home = HomeData.missing(generateHomeName(number));
        String currentWorld = player.getWorld().getName();
        int currentX = player.getLocation().getBlockX();
        int currentY = player.getLocation().getBlockY();
        int currentZ = player.getLocation().getBlockZ();

        return applyGlobalPlaceholders(input, player, homes, maxHomes)
                .replace("{home_number}", String.valueOf(number))
                .replace("{home_name}", home.name)
                .replace("{home}", home.name)
                .replace("{home_status}", home.exists ? color(sectionString("status-text.established", "&aEstablecida")) : color(sectionString("status-text.missing", "&cNo establecida")))
                .replace("{home_world}", home.world)
                .replace("{home_x}", home.exists ? String.valueOf((int) Math.floor(home.x)) : "-")
                .replace("{home_y}", home.exists ? String.valueOf((int) Math.floor(home.y)) : "-")
                .replace("{home_z}", home.exists ? String.valueOf((int) Math.floor(home.z)) : "-")
                .replace("{current_world}", currentWorld)
                .replace("{current_x}", String.valueOf(currentX))
                .replace("{current_y}", String.valueOf(currentY))
                .replace("{current_z}", String.valueOf(currentZ));
    }

    private void fill(Inventory inv, Player player, List<HomeData> homes, int maxHomes) {
        ConfigurationSection sec = section("items.filler");
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        ItemStack filler = itemFromPath("items.filler", "", null, 0, player, homes, maxHomes);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private int slot(String path, int def) {
        return Math.max(0, Math.min(size - 1, plugin.getConfig().getInt(CONFIG_PATH + "." + path, def)));
    }

    private int defaultTeleportSlot(int number) {
        int[] slots = {10, 19, 28, 37, 46, 11, 20, 29, 38};
        return slots[Math.max(0, Math.min(slots.length - 1, number - 1))];
    }

    private int defaultSetSlot(int number) {
        int[] slots = {16, 25, 34, 43, 52, 15, 24, 33, 42};
        return slots[Math.max(0, Math.min(slots.length - 1, number - 1))];
    }

    private int normalizeMenuSize(int value) {
        int size = Math.max(9, Math.min(54, value));
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return size;
    }

    private ConfigurationSection section(String relativePath) {
        return plugin.getConfig().getConfigurationSection(CONFIG_PATH + "." + relativePath);
    }

    private String sectionString(String relativePath, String def) {
        return plugin.getConfig().getString(CONFIG_PATH + "." + relativePath, def);
    }

    private String normalizeCommand(String raw) {
        if (raw == null || raw.isBlank()) return "social";
        String out = raw.trim();
        if (out.startsWith("/")) out = out.substring(1);
        return out;
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

    private void applySkullTexture(SkullMeta skull, String textureValue) {
        if (skull == null || textureValue == null || textureValue.isBlank()) return;
        String textureUrl = extractTextureUrl(textureValue.trim());
        if (textureUrl == null || textureUrl.isBlank()) return;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID(), "MDVHome");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            skull.setOwnerProfile(profile);
        } catch (Throwable ignored) { }
    }

    private String getMessage(String key, String def) {
        return plugin.getConfig().getString(CONFIG_PATH + ".messages." + key, def);
    }

    private void sendMessage(Player player, String key, String def, Map<String, String> placeholders) {
        String raw = getMessage(key, def);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(color(plugin.getConfig().getString("messages.prefix", "&6[MDVSocial] &r") + raw));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class HomeData {
        final String name;
        final String world;
        final double x;
        final double y;
        final double z;
        final boolean exists;

        HomeData(String name, String world, double x, double y, double z, boolean exists) {
            this.name = name == null ? "" : name;
            this.world = world == null || world.isBlank() ? "world" : world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.exists = exists;
        }

        static HomeData missing(String name) {
            return new HomeData(name, "-", 0, 0, 0, false);
        }
    }

    private static final class HomesHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
