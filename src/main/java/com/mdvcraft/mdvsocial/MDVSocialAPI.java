package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * API simple para consultar titulos equipados desde otros plugins de MDVCRAFT.
 */
public final class MDVSocialAPI {
    private MDVSocialAPI() { }

    private static MDVSocialPlugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MDVSocial");
        if (!(plugin instanceof MDVSocialPlugin social) || !plugin.isEnabled()) return null;
        return social;
    }

    public static MDVSocialPlugin.TitleDef getEquippedTitle(UUID uuid) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? null : plugin.getEquippedTitle(uuid);
    }

    public static String getEquippedTitleId(UUID uuid) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getEquippedTitleId(uuid);
    }

    public static String getEquippedTitle(UUID uuid, boolean colored) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getEquippedTitleDisplay(uuid, colored);
    }

    public static String getEquippedTitleColored(UUID uuid) {
        return getEquippedTitle(uuid, true);
    }

    public static String getEquippedTitlePlain(UUID uuid) {
        return getEquippedTitle(uuid, false);
    }

    public static String getEquippedTitlePrefix(UUID uuid, boolean colored) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getEquippedTitlePrefix(uuid, colored);
    }
    public static boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName, String clanTag, String clanName, String message, long expiresAt) {
        MDVSocialPlugin plugin = plugin();
        return plugin != null && plugin.sendClanInviteMail(targetUuid, targetName, inviterUuid, fromName, clanTag, clanName, message, "", expiresAt);
    }

    public static boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName, String clanTag, String clanName, String message, String clanBannerData, long expiresAt) {
        MDVSocialPlugin plugin = plugin();
        return plugin != null && plugin.sendClanInviteMail(targetUuid, targetName, inviterUuid, fromName, clanTag, clanName, message, clanBannerData, expiresAt);
    }

}
