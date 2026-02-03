package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProvider;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Mojang 官方认证提供者
 * 实现 Mojang 官方的验证流程
 */
public class MojangAuthProvider implements AuthProvider {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final Object sessionService;
    private final boolean enabled;

    /**
     * 构造 MojangAuthProvider
     * 
     * @param sessionService Minecraft 会话服务
     * @param enabled        是否启用
     */
    public MojangAuthProvider(Object sessionService, boolean enabled) {
        this.sessionService = sessionService;
        this.enabled = enabled;
    }

    /**
     * 执行 Mojang 官方认证
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 认证成功返回 GameProfile，认证失败返回 null
     */
    @Override
    public Object authenticate(String username, String serverId) {
        if (!enabled) {
            LOGGER.info("MojangAuthProvider: Provider is disabled");
            return null;
        }

        try {
            LOGGER.info("MojangAuthProvider: Authenticating " + username + " with Mojang");
            // 使用反射调用 hasJoinedServer 方法
            Object profile = callHasJoinedServer(sessionService, username, serverId, null);
            if (profile != null) {
                LOGGER.info("MojangAuthProvider: Authentication successful for " + username);
            } else {
                LOGGER.info("MojangAuthProvider: Authentication failed for " + username);
            }
            return profile;
        } catch (Exception e) {
            LOGGER.warning("MojangAuthProvider: Exception during authentication: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射调用 hasJoinedServer 方法
     * 
     * @param sessionService Minecraft 会话服务
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @param ipAddress IP地址
     * @return 认证成功返回 GameProfile，认证失败返回 null
     */
    private Object callHasJoinedServer(Object sessionService, String username, String serverId, String ipAddress) {
        try {
            // 调用 hasJoinedServer 方法
            java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class, String.class);
            return method.invoke(sessionService, username, serverId, ipAddress);
        } catch (Exception e) {
            LOGGER.warning("MojangAuthProvider: Exception calling hasJoinedServer: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取提供者名称
     * 
     * @return 提供者名称
     */
    @Override
    public String getName() {
        return "Mojang";
    }

    /**
     * 检查提供者是否启用
     * 
     * @return 是否启用
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
