package com.Leeinx.ximultilogin.papi;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * XiMultiLogin 占位符扩展
 * 为 PAPI 提供变量支持
 */
public class XiPlaceholderExpansion extends PlaceholderExpansion {

    private final XiMultiLogin plugin;
    private final IdentityGuard identityGuard;

    /**
     * 构造 XiPlaceholderExpansion
     *
     * @param plugin 插件实例
     */
    public XiPlaceholderExpansion(XiMultiLogin plugin) {
        this.plugin = plugin;
        this.identityGuard = plugin.getIdentityGuard();
    }

    @Override
    public String getIdentifier() {
        return "ximultilogin";
    }

    @Override
    public String getAuthor() {
        return "XiLogin Team";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // 持久化，避免重载时丢失
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }

        String playerName = player.getName();
        if (playerName == null) {
            return "";
        }

        switch (params.toLowerCase()) {
            case "auth":
                return getAuthType(playerName);
            case "auth_display":
                return getAuthTypeDisplay(playerName);
            case "uuid":
                return getUUID(playerName);
            case "cracked":
                return isCracked(playerName);
            case "status":
                return getPlayerStatus(playerName);
            default:
                return "";
        }
    }

    /**
     * 获取玩家的认证类型
     *
     * @param playerName 玩家名称
     * @return 认证类型，若不存在返回 "Unknown"
     */
    private String getAuthType(String playerName) {
        String authType = identityGuard.getAuthProvider(playerName);
        return authType != null ? authType : "Unknown";
    }

    /**
     * 获取格式化的认证类型显示名称
     *
     * @param playerName 玩家名称
     * @return 格式化的认证类型名称
     */
    private String getAuthTypeDisplay(String playerName) {
        String authType = identityGuard.getAuthProvider(playerName);
        if (authType == null) {
            return "未知";
        }

        switch (authType.toUpperCase()) {
            case "MOJANG":
                return "正版";
            case "YGGDRASIL":
                return "外置登录";
            default:
                return authType;
        }
    }

    /**
     * 获取玩家的UUID
     *
     * @param playerName 玩家名称
     * @return UUID字符串，若不存在返回 "Unknown"
     */
    private String getUUID(String playerName) {
        UUID uuid = identityGuard.getUUID(playerName);
        return uuid != null ? uuid.toString() : "Unknown";
    }
    
    /**
     * 检查玩家是否为盗版玩家
     *
     * @param playerName 玩家名称
     * @return "true" 或 "false"
     */
    private String isCracked(String playerName) {
        String authType = identityGuard.getAuthProvider(playerName);
        return authType != null && authType.equals("CRACKED") ? "true" : "false";
    }
    
    /**
     * 获取玩家的登录状态
     *
     * @param playerName 玩家名称
     * @return 登录状态描述
     */
    private String getPlayerStatus(String playerName) {
        String authType = identityGuard.getAuthProvider(playerName);
        if (authType == null) {
            return "未知";
        }

        switch (authType.toUpperCase()) {
            case "MOJANG":
                return "正版";
            case "YGGDRASIL":
                return "外置登录";
            case "CRACKED":
                return "盗版";
            default:
                return authType;
        }
    }
}
