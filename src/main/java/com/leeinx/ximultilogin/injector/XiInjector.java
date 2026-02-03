package com.leeinx.ximultilogin.injector;

import com.leeinx.ximultilogin.auth.XiSessionService;
import com.leeinx.ximultilogin.reflection.XiReflection;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * NMS 注入器
 * 负责将 XiSessionService 注入到 Minecraft 服务器的验证流程中
 * 支持 1.16.5 - 1.21+ 全版本
 */
public class XiInjector {

    private static final Logger LOGGER = Bukkit.getLogger();
    private Object originalSessionService;
    private Field sessionServiceField;
    private Object serverHandle;

    /**
     * 注入 XiSessionService
     *
     * @param xiSessionService 要注入的 XiSessionService 实例
     * @return 是否注入成功
     */
    public boolean inject(XiSessionService xiSessionService) {
        try {
            // 获取服务器实例
            Server server = Bukkit.getServer();
            if (server == null) {
                LOGGER.severe("XiInjector: Failed to get server instance");
                return false;
            }

            // 获取 NMS 服务器句柄
            serverHandle = XiReflection.getNMSHandle(server);
            if (serverHandle == null) {
                LOGGER.severe("XiInjector: Failed to get NMS server handle");
                return false;
            }

            // 查找 SessionService 字段
            sessionServiceField = findSessionServiceField(serverHandle.getClass());
            if (sessionServiceField == null) {
                LOGGER.severe("XiInjector: Failed to find SessionService field");
                return false;
            }

            // 保存原始 SessionService
            originalSessionService = XiReflection.getFieldValue(serverHandle, sessionServiceField);
            if (originalSessionService == null) {
                LOGGER.warning("XiInjector: Original SessionService is null");
            } else {
                LOGGER.info("XiInjector: Found original SessionService: " + originalSessionService.getClass().getName());
            }

            // 注入 XiSessionService
            boolean success = XiReflection.setFieldValue(serverHandle, sessionServiceField, xiSessionService);
            if (success) {
                LOGGER.info("XiInjector: Successfully injected XiSessionService");
                return true;
            } else {
                LOGGER.severe("XiInjector: Failed to set XiSessionService");
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("XiInjector: Exception during injection: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 卸载注入，恢复原始 SessionService
     *
     * @return 是否卸载成功
     */
    public boolean uninject() {
        try {
            if (serverHandle == null || sessionServiceField == null || originalSessionService == null) {
                LOGGER.warning("XiInjector: Nothing to uninject");
                return false;
            }

            // 恢复原始 SessionService
            boolean success = XiReflection.setFieldValue(serverHandle, sessionServiceField, originalSessionService);
            if (success) {
                LOGGER.info("XiInjector: Successfully uninjected, restored original SessionService");
                // 清理引用
                originalSessionService = null;
                sessionServiceField = null;
                serverHandle = null;
                return true;
            } else {
                LOGGER.severe("XiInjector: Failed to restore original SessionService");
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("XiInjector: Exception during uninjection: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 查找 SessionService 字段
     * 支持不同版本的字段名称
     *
     * @param serverClass 服务器类
     * @return 找到的字段
     */
    private Field findSessionServiceField(Class<?> serverClass) {
        // 尝试通过类型查找
        Field field = XiReflection.getFieldByType(serverClass, MinecraftSessionService.class);
        if (field != null) {
            LOGGER.info("XiInjector: Found SessionService field by type: " + field.getName());
            return field;
        }

        // 尝试常见的字段名称
        String[] possibleFieldNames = {
                "sessionService",
                "authenticationService",
                "authService",
                "sessionManager"
        };

        for (String fieldName : possibleFieldNames) {
            try {
                Field f = serverClass.getDeclaredField(fieldName);
                // 检查字段类型是否与 SessionService 相关
                if (MinecraftSessionService.class.isAssignableFrom(f.getType()) || 
                    f.getType().getName().contains("SessionService") ||
                    f.getType().getName().contains("AuthService")) {
                    LOGGER.info("XiInjector: Found SessionService field by name: " + fieldName);
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException e) {
                // 字段不存在，继续尝试下一个
            }
        }

        // 尝试查找 AuthenticationService 字段，再从其中获取 SessionService
        Field authServiceField = XiReflection.getFieldByType(serverClass, findAuthenticationServiceClass());
        if (authServiceField != null) {
            try {
                authServiceField.setAccessible(true);
                Object authService = authServiceField.get(serverHandle);
                if (authService != null) {
                    // 尝试从 AuthenticationService 中获取 SessionService
                    Field sessionField = XiReflection.getFieldByType(authService.getClass(), MinecraftSessionService.class);
                    if (sessionField != null) {
                        LOGGER.info("XiInjector: Found SessionService field in AuthenticationService: " + sessionField.getName());
                        return sessionField;
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("XiInjector: Exception when looking for SessionService in AuthenticationService: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * 查找 AuthenticationService 类
     * 支持不同版本的包路径
     *
     * @return AuthenticationService 类
     */
    private Class<?> findAuthenticationServiceClass() {
        String[] possibleClassNames = {
                "com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService",
                "net.minecraft.server.auth.GameAuthenticationService",
                "net.minecraft.server.AuthenticationService"
        };

        for (String className : possibleClassNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                // 类不存在，继续尝试下一个
            }
        }

        return null;
    }

    /**
     * 检查是否已注入
     *
     * @return 是否已注入
     */
    public boolean isInjected() {
        return sessionServiceField != null && serverHandle != null;
    }

    /**
     * 获取原始 SessionService
     *
     * @return 原始 SessionService
     */
    public Object getOriginalSessionService() {
        return originalSessionService;
    }
}
