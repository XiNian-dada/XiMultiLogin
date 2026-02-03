package com.leeinx.ximultilogin.auth.providers;

import com.leeinx.ximultilogin.auth.AuthProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Mojang 验证提供者
 * 包装原生的 SessionService 调用，使用 Mojang 官方验证
 */
public class MojangAuthProvider implements AuthProvider {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final MinecraftSessionService sessionService;
    private final boolean enabled;

    /**
     * 构造 Mojang 验证提供者
     *
     * @param sessionService 原生的 SessionService 实例
     * @param enabled        是否启用
     */
    public MojangAuthProvider(MinecraftSessionService sessionService, boolean enabled) {
        this.sessionService = sessionService;
        this.enabled = enabled;
    }

    @Override
    public GameProfile authenticate(String name, String serverId) {
        if (!enabled) {
            return null;
        }

        try {
            LOGGER.info("MojangAuthProvider: Authenticating player " + name + " with serverId " + serverId);
            
            // 调用原生的 SessionService 验证方法
            GameProfile profile = sessionService.hasJoinedServer(name, serverId, null);
            
            if (profile != null) {
                LOGGER.info("MojangAuthProvider: Authentication successful for " + name + ", UUID: " + profile.getId());
            } else {
                LOGGER.info("MojangAuthProvider: Authentication failed for " + name);
            }
            
            return profile;
        } catch (Exception e) {
            // 捕获所有异常，确保不会影响后续验证提供者
            LOGGER.warning("MojangAuthProvider: Exception during authentication for " + name + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() {
        return "Mojang";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
