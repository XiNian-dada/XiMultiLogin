package com.Leeinx.ximultilogin.listener;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 玩家登录监听器
 * 监听玩家登录事件，处理认证失败时的自定义消息、同 ID 多鉴权冲突拦截及服务不可用通知
 */
public class PlayerLoginListener implements Listener {

    private static final Logger LOGGER = Bukkit.getLogger();

    private final XiMultiLogin plugin;
    private final MessageManager messageManager;

    private final Map<String, String> failedAuthReasons = new ConcurrentHashMap<>();
    private final Map<String, String> failedAuthProviders = new ConcurrentHashMap<>();
    private final Map<String, String> failedAuthConflictProviders = new ConcurrentHashMap<>();
    private final Map<String, String> successfulAuthProviders = new ConcurrentHashMap<>();

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

    /**
     * 监听玩家登录事件
     * 作为安全网，处理在 AsyncPlayerPreLoginEvent 中未处理的认证失败
     *
     * @param event 玩家登录事件
     */
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

    /**
     * 监听玩家加入事件
     * 发送认证成功的消息给玩家
     *
     * @param event 玩家加入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
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

    /**
     * 监听玩家退出事件
     * 确保认证记录被彻底清理
     *
     * @param event 玩家退出事件
     */
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

    /**
     * 记录认证失败原因
     * 这个方法会在 XiSessionService 中调用
     *
     * @param username 玩家名称
     * @param reason   失败原因
     * @param provider 认证提供者（可选）
     */
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

    /**
     * 记录认证服务不可用状态
     *
     * @param username 玩家名称
     * @param provider 认证提供者名称
     */
    public void recordAuthUnavailable(String username, String provider) {
        LOGGER.warning("PlayerLoginListener: Recording auth service unavailable for " + username + " (provider: " + provider + ")");
        failedAuthReasons.put(username, "provider_unavailable");
        if (provider != null && !provider.trim().isEmpty()) {
            failedAuthProviders.put(username, provider);
        } else {
            failedAuthProviders.remove(username);
        }
        failedAuthConflictProviders.remove(username);
        successfulAuthProviders.remove(username);
    }

    /**
     * 记录同 ID 多鉴权冲突
     *
     * @param username          玩家名称
     * @param lockedProvider    已锁定的认证提供者
     * @param conflictProviders 同时匹配的其他认证提供者
     */
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

    /**
     * 记录认证成功
     * 这个方法会在 XiSessionService 中调用
     *
     * @param username 玩家名称
     * @param provider 认证提供者名称
     */
    public void recordAuthSuccess(String username, String provider) {
        LOGGER.info("PlayerLoginListener: Recording auth success for " + username + " (provider: " + provider + ")");
        successfulAuthProviders.put(username, provider);
        failedAuthReasons.remove(username);
        failedAuthProviders.remove(username);
        failedAuthConflictProviders.remove(username);
    }

    /**
     * 清理所有旧的认证记录
     * 可以在插件禁用或重新加载时调用
     */
    public void cleanupOldRecords() {
        failedAuthReasons.clear();
        failedAuthProviders.clear();
        failedAuthConflictProviders.clear();
        successfulAuthProviders.clear();
        LOGGER.info("PlayerLoginListener: Cleaned up old auth records");
    }

    /**
     * 清除指定玩家的认证失败记录
     * 在验证成功时调用，防止玩家被错误踢出
     *
     * @param username 玩家名称
     */
    public void clearAuthFailure(String username) {
        if (failedAuthReasons.remove(username) != null
                || failedAuthProviders.remove(username) != null
                || failedAuthConflictProviders.remove(username) != null) {
            LOGGER.info("PlayerLoginListener: Cleared auth failure record for " + username);
        }
    }

    /**
     * 清除指定玩家的认证成功记录
     *
     * @param username 玩家名称
     */
    public void clearAuthSuccess(String username) {
        if (successfulAuthProviders.remove(username) != null) {
            LOGGER.info("PlayerLoginListener: Cleared auth success record for " + username);
        }
    }

    /**
     * 构建踢出提示消息
     *
     * @param reason            失败原因标识
     * @param provider          提供者名称
     * @param conflictProviders 冲突提供者列表字符串
     * @return 格式化后的踢出消息
     */
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
        if ("provider_unavailable".equals(reason)) {
            String unavailableProvider = provider != null && !provider.trim().isEmpty() ? provider : "认证服务";
            if (messageManager.getMessages().contains("login.provider_unavailable")) {
                return messageManager.getMessage("login.provider_unavailable", "provider", unavailableProvider);
            }
            return messageManager.getMessage(
                    "login.failed",
                    "reason", unavailableProvider + " 暂时无法验证您的身份，请稍后重试"
            );
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
