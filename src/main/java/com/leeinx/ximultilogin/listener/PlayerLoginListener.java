package com.Leeinx.ximultilogin.listener;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerLoginListener implements Listener {

    private static final Logger LOGGER = Bukkit.getLogger();

    private final XiMultiLogin plugin;
    private final MessageManager messageManager;

    private final Map<String, String> failedAuthReasons = new ConcurrentHashMap<>();
    private final Map<String, String> failedAuthProviders = new ConcurrentHashMap<>();
    private final Map<String, String> failedAuthConflictProviders = new ConcurrentHashMap<>();
    private final Map<String, String> successfulAuthProviders = new ConcurrentHashMap<>();

    public PlayerLoginListener(XiMultiLogin plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        String reason = failedAuthReasons.get(username);

        LOGGER.info("PlayerLoginListener: AsyncPlayerPreLoginEvent for " + username);

        if (reason == null) {
            return;
        }

        String provider = failedAuthProviders.get(username);
        String conflictProviders = failedAuthConflictProviders.get(username);
        String kickMessage = buildKickMessage(reason, provider, conflictProviders);

        LOGGER.info("PlayerLoginListener: Found failed auth reason for " + username + ": " + reason);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
        LOGGER.info("PlayerLoginListener: Kicked player " + username + " with custom message");

        clearAuthFailure(username);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        String username = event.getPlayer().getName();
        String reason = failedAuthReasons.get(username);

        LOGGER.info("PlayerLoginListener: PlayerLoginEvent for " + username);

        if (reason == null) {
            return;
        }

        String provider = failedAuthProviders.get(username);
        String conflictProviders = failedAuthConflictProviders.get(username);
        String kickMessage = buildKickMessage(reason, provider, conflictProviders);

        LOGGER.severe("PlayerLoginListener: CRITICAL SECURITY BREACH - Player " + username + " bypassed AsyncPlayerPreLoginEvent check!");
        LOGGER.severe("PlayerLoginListener: Authentication failure reason: " + reason);

        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
        LOGGER.info("PlayerLoginListener: Kicked player " + username + " with custom message (safety net)");

        clearAuthFailure(username);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        String username = player.getName();
        String provider = successfulAuthProviders.get(username);

        if (provider == null) {
            return;
        }

        String joinMessage = messageManager.getMessage("login.success", "provider", provider);
        player.sendMessage(joinMessage);
        LOGGER.info("PlayerLoginListener: Sent auth success message to " + username + " (provider: " + provider + ")");

        successfulAuthProviders.remove(username);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String username = event.getPlayer().getName();

        if (failedAuthReasons.remove(username) != null
                || failedAuthProviders.remove(username) != null
                || failedAuthConflictProviders.remove(username) != null) {
            LOGGER.info("PlayerLoginListener: Cleaned up auth failure records for " + username + " on quit");
        }

        if (successfulAuthProviders.remove(username) != null) {
            LOGGER.info("PlayerLoginListener: Cleaned up auth success records for " + username + " on quit");
        }
    }

    public void recordAuthFailure(String username, String reason, String provider) {
        LOGGER.info("PlayerLoginListener: Recording auth failure for " + username + ": " + reason + " (provider: " + provider + ")");
        failedAuthReasons.put(username, reason);
        if (provider != null && !provider.trim().isEmpty()) {
            failedAuthProviders.put(username, provider);
        } else {
            failedAuthProviders.remove(username);
        }
        failedAuthConflictProviders.remove(username);
        successfulAuthProviders.remove(username);
    }

    public void recordProviderConflict(String username, String lockedProvider, String conflictProviders) {
        LOGGER.warning("PlayerLoginListener: Recording provider conflict for " + username
                + " (locked: " + lockedProvider + ", conflicts: " + conflictProviders + ")");
        failedAuthReasons.put(username, "provider_conflict");
        if (lockedProvider != null && !lockedProvider.trim().isEmpty()) {
            failedAuthProviders.put(username, lockedProvider);
        } else {
            failedAuthProviders.remove(username);
        }
        if (conflictProviders != null && !conflictProviders.trim().isEmpty()) {
            failedAuthConflictProviders.put(username, conflictProviders);
        } else {
            failedAuthConflictProviders.put(username, "unknown");
        }
        successfulAuthProviders.remove(username);
    }

    public void recordAuthSuccess(String username, String provider) {
        LOGGER.info("PlayerLoginListener: Recording auth success for " + username + " (provider: " + provider + ")");
        successfulAuthProviders.put(username, provider);
        failedAuthReasons.remove(username);
        failedAuthProviders.remove(username);
        failedAuthConflictProviders.remove(username);
    }

    public void cleanupOldRecords() {
        failedAuthReasons.clear();
        failedAuthProviders.clear();
        failedAuthConflictProviders.clear();
        successfulAuthProviders.clear();
        LOGGER.info("PlayerLoginListener: Cleaned up old auth records");
    }

    public void clearAuthFailure(String username) {
        if (failedAuthReasons.remove(username) != null
                || failedAuthProviders.remove(username) != null
                || failedAuthConflictProviders.remove(username) != null) {
            LOGGER.info("PlayerLoginListener: Cleared auth failure record for " + username);
        }
    }

    public void clearAuthSuccess(String username) {
        if (successfulAuthProviders.remove(username) != null) {
            LOGGER.info("PlayerLoginListener: Cleared auth success record for " + username);
        }
    }

    private String buildKickMessage(String reason, String provider, String conflictProviders) {
        if ("strict_auth_failed".equals(reason)) {
            return messageManager.getMessage("login.strict_auth_failed", "provider", provider);
        }
        if ("mojang_failed".equals(reason)) {
            return messageManager.getMessage("login.mojang_failed");
        }
        if ("yggdrasil_failed".equals(reason)) {
            return messageManager.getMessage("login.yggdrasil_failed", "provider", provider);
        }
        if ("all_providers_failed".equals(reason)) {
            return messageManager.getMessage("login.all_providers_failed");
        }
        if ("provider_conflict".equals(reason)) {
            String lockedProvider = provider != null && !provider.trim().isEmpty() ? provider : "未锁定";
            String providers = conflictProviders != null && !conflictProviders.trim().isEmpty()
                    ? conflictProviders
                    : "未知";
            if (messageManager.getMessages().contains("login.provider_conflict")) {
                return messageManager.getMessage(
                        "login.provider_conflict",
                        "locked_provider", lockedProvider,
                        "providers", providers
                );
            }
            return messageManager.getMessage(
                    "login.failed",
                    "reason", "检测到同 ID 多鉴权冲突。当前锁定鉴权：" + lockedProvider + "，同时命中的鉴权方式：" + providers
            );
        }
        return messageManager.getMessage("login.failed", "reason", reason);
    }
}
