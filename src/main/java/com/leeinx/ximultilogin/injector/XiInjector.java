package com.Leeinx.ximultilogin.injector;

import com.Leeinx.ximultilogin.auth.XiSessionService;
import com.Leeinx.ximultilogin.reflection.XiReflection;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * XiInjector
 * 负责将 XiSessionService 注入到 Minecraft 服务器的验证流程中
 */
public class XiInjector {

    private static final Logger LOGGER = Bukkit.getLogger();

    /**
     * 获取原始的 MinecraftSessionService
     * 
     * @return 原始的 MinecraftSessionService
     */
    public Object getOriginalSessionService() {
        try {
            // 获取 MinecraftServer 实例
            Object minecraftServer = XiReflection.getMinecraftServer();
            if (minecraftServer == null) {
                LOGGER.severe("XiInjector: Failed to get MinecraftServer instance");
                return null;
            }
            
            // 获取 SessionService
            return XiReflection.getSessionService(minecraftServer);
        } catch (Exception e) {
            LOGGER.severe("XiInjector: Failed to get original session service: " + e.getMessage());
            return null;
        }
    }

    /**
     * 注入 XiSessionService
     * 
     * @param xiSessionService XiSessionService 实例
     * @return 是否注入成功
     */
    public boolean inject(XiSessionService xiSessionService) {
        try {
            // 获取 MinecraftServer 实例
            Object minecraftServer = XiReflection.getMinecraftServer();
            if (minecraftServer == null) {
                LOGGER.severe("XiInjector: Failed to get MinecraftServer instance");
                return false;
            }
            
            // 注入 SessionService
            return XiReflection.setSessionService(minecraftServer, xiSessionService);
        } catch (Exception e) {
            LOGGER.severe("XiInjector: Failed to inject session service: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复原始的 MinecraftSessionService
     * 
     * @param originalSessionService 原始的 MinecraftSessionService
     * @return 是否恢复成功
     */
    public boolean restore(Object originalSessionService) {
        try {
            // 获取 MinecraftServer 实例
            Object minecraftServer = XiReflection.getMinecraftServer();
            if (minecraftServer == null) {
                LOGGER.severe("XiInjector: Failed to get MinecraftServer instance");
                return false;
            }
            
            // 恢复 SessionService
            return XiReflection.setSessionService(minecraftServer, originalSessionService);
        } catch (Exception e) {
            LOGGER.severe("XiInjector: Failed to restore original session service: " + e.getMessage());
            return false;
        }
    }
}
