package com.Leeinx.ximultilogin.reflection;

import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * XiReflection
 * 提供反射工具方法，用于操作 Minecraft 服务器的内部类
 */
public class XiReflection {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static Class<?> minecraftServerClass;
    private static Class<?> dedicatedServerClass;
    private static Class<?> minecraftServerClassServer;

    /**
     * 初始化反射工具
     */
    public static void init() {
        try {
            // 获取 MinecraftServer 类
            minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            LOGGER.info("XiReflection: Found MinecraftServer class");
            
            // 获取 DedicatedServer 类
            dedicatedServerClass = Class.forName("net.minecraft.server.dedicated.DedicatedServer");
            LOGGER.info("XiReflection: Found DedicatedServer class");
            
            LOGGER.info("XiReflection: Initialized successfully");
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Failed to initialize: " + e.getMessage());
        }
    }

    /**
     * 获取 MinecraftServer 实例
     * 
     * @return MinecraftServer 实例
     */
    public static Object getMinecraftServer() {
        try {
            // 通过 Bukkit 获取服务器实例
            Object server = Bukkit.getServer();
            Method getServerMethod = server.getClass().getMethod("getServer");
            return getServerMethod.invoke(server);
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Failed to get MinecraftServer instance: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取 SessionService
     * 
     * @param minecraftServer MinecraftServer 实例
     * @return SessionService 实例
     */
    public static Object getSessionService(Object minecraftServer) {
        try {
            // 获取 Server 实例
            Method getServerMethod = minecraftServer.getClass().getMethod("getServer");
            Object server = getServerMethod.invoke(minecraftServer);
            
            // 获取 SessionService
            Field sessionServiceField = server.getClass().getDeclaredField("sessionService");
            sessionServiceField.setAccessible(true);
            return sessionServiceField.get(server);
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Failed to get SessionService: " + e.getMessage());
            return null;
        }
    }

    /**
     * 设置 SessionService
     * 
     * @param minecraftServer MinecraftServer 实例
     * @param sessionService 要设置的 SessionService 实例
     * @return 是否设置成功
     */
    public static boolean setSessionService(Object minecraftServer, Object sessionService) {
        try {
            // 获取 Server 实例
            Method getServerMethod = minecraftServer.getClass().getMethod("getServer");
            Object server = getServerMethod.invoke(minecraftServer);
            
            // 设置 SessionService
            Field sessionServiceField = server.getClass().getDeclaredField("sessionService");
            sessionServiceField.setAccessible(true);
            sessionServiceField.set(server, sessionService);
            
            LOGGER.info("XiReflection: SessionService set successfully");
            return true;
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Failed to set SessionService: " + e.getMessage());
            return false;
        }
    }
}
