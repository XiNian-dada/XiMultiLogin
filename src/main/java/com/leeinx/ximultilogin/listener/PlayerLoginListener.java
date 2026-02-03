package com.Leeinx.ximultilogin.listener;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 玩家登录监听器
 * 监听玩家登录事件，处理认证失败时的自定义消息
 */
public class PlayerLoginListener implements Listener {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final XiMultiLogin plugin;
    private final MessageManager messageManager;

    private final Map<String, String> failedAuthReasons = new ConcurrentHashMap<>();
    private final Map<String, String> failedAuthProviders = new ConcurrentHashMap<>();

    /**
     * 构造 PlayerLoginListener
     *
     * @param plugin 插件实例
     */
    public PlayerLoginListener(XiMultiLogin plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    /**
     * 监听异步玩家预登录事件
     * 在这个阶段检查认证状态，如果认证失败则使用自定义消息拒绝登录
     *
     * @param event 异步玩家预登录事件
     */
    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        UUID uuid = event.getUniqueId();

        LOGGER.info("PlayerLoginListener: AsyncPlayerPreLoginEvent for " + username);

        String reason = failedAuthReasons.remove(username);
        String provider = failedAuthProviders.remove(username);

        if (reason != null) {
            LOGGER.info("PlayerLoginListener: Found failed auth reason for " + username + ": " + reason);

            String kickMessage;
            if ("strict_auth_failed".equals(reason)) {
                kickMessage = messageManager.getMessage("login.strict_auth_failed", "provider", provider);
            } else if ("mojang_failed".equals(reason)) {
                kickMessage = messageManager.getMessage("login.mojang_failed");
            } else if ("yggdrasil_failed".equals(reason)) {
                kickMessage = messageManager.getMessage("login.yggdrasil_failed", "provider", provider);
            } else if ("all_providers_failed".equals(reason)) {
                kickMessage = messageManager.getMessage("login.all_providers_failed");
            } else {
                kickMessage = messageManager.getMessage("login.failed", "reason", reason);
            }

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            LOGGER.info("PlayerLoginListener: Kicked player " + username + " with custom message");
        }
    }

    /**
     * 监听玩家登录事件
     * 作为备用方案，处理在 AsyncPlayerPreLoginEvent 中未处理的认证失败
     *
     * @param event 玩家登录事件
     */
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String username = event.getPlayer().getName();

        LOGGER.info("PlayerLoginListener: PlayerLoginEvent for " + username);

        if (event.getResult() == PlayerLoginEvent.Result.KICK_OTHER) {
            String reason = failedAuthReasons.remove(username);
            String provider = failedAuthProviders.remove(username);

            if (reason != null) {
                LOGGER.info("PlayerLoginListener: Found failed auth reason for " + username + ": " + reason);

                String kickMessage;
                if ("strict_auth_failed".equals(reason)) {
                    kickMessage = messageManager.getMessage("login.strict_auth_failed", "provider", provider);
                } else if ("mojang_failed".equals(reason)) {
                    kickMessage = messageManager.getMessage("login.mojang_failed");
                } else if ("yggdrasil_failed".equals(reason)) {
                    kickMessage = messageManager.getMessage("login.yggdrasil_failed", "provider", provider);
                } else if ("all_providers_failed".equals(reason)) {
                    kickMessage = messageManager.getMessage("login.all_providers_failed");
                } else {
                    kickMessage = messageManager.getMessage("login.failed", "reason", reason);
                }

                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
                LOGGER.info("PlayerLoginListener: Kicked player " + username + " with custom message");
            }
        }
    }

    /**
     * 记录认证失败原因
     * 这个方法会在 XiSessionService 中调用
     *
     * @param username 玩家名称
     * @param reason 失败原因
     * @param provider 认证提供者（可选）
     */
    public void recordAuthFailure(String username, String reason, String provider) {
        LOGGER.info("PlayerLoginListener: Recording auth failure for " + username + ": " + reason + " (provider: " + provider + ")");
        failedAuthReasons.put(username, reason);
        if (provider != null) {
            failedAuthProviders.put(username, provider);
        }
    }

    /**
     * 清理过期的认证失败记录
     * 定期调用以防止内存泄漏
     */
    public void cleanupOldRecords() {
        long currentTime = System.currentTimeMillis();
        failedAuthReasons.clear();
        failedAuthProviders.clear();
        LOGGER.info("PlayerLoginListener: Cleaned up old auth failure records");
    }
}
