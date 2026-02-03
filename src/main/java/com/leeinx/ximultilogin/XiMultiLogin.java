package com.leeinx.ximultilogin;

import com.leeinx.ximultilogin.auth.XiSessionService;
import com.leeinx.ximultilogin.config.ConfigManager;
import com.leeinx.ximultilogin.guard.IdentityGuard;
import com.leeinx.ximultilogin.injector.XiInjector;
import com.leeinx.ximultilogin.reflection.XiReflection;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * XiMultiLogin 主类
 * 插件的入口点，负责整合所有模块
 */
public class XiMultiLogin extends JavaPlugin {

    private static final Logger LOGGER = Bukkit.getLogger();
    private ConfigManager configManager;
    private XiInjector injector;
    private XiSessionService xiSessionService;
    private IdentityGuard identityGuard;

    @Override
    public void onEnable() {
        LOGGER.info("XiMultiLogin: Enabling plugin...");

        try {
            // 确保数据文件夹存在
            if (!getDataFolder().exists()) {
                if (getDataFolder().mkdirs()) {
                    LOGGER.info("XiMultiLogin: Created data folder");
                } else {
                    LOGGER.severe("XiMultiLogin: Failed to create data folder");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
            }

            // 初始化配置管理器
            configManager = new ConfigManager(this);
            LOGGER.info("XiMultiLogin: ConfigManager initialized");

            // 初始化身份守护者
            // 加载数据库配置
            ConfigManager.DatabaseConfig dbConfig = configManager.getDatabaseConfig();
            // 创建数据库管理器
            com.leeinx.ximultilogin.database.DatabaseManager databaseManager = com.leeinx.ximultilogin.database.DatabaseFactory.createDatabaseManager(
                    dbConfig.getType(),
                    getDataFolder(),
                    dbConfig.getHost(),
                    dbConfig.getPort(),
                    dbConfig.getDatabase(),
                    dbConfig.getUsername(),
                    dbConfig.getPassword()
            );
            // 初始化数据库管理器
            databaseManager.initialize();
            // 创建 IdentityGuard
            identityGuard = new com.leeinx.ximultilogin.guard.IdentityGuard(databaseManager);
            LOGGER.info("XiMultiLogin: IdentityGuard initialized with " + identityGuard.getIdentityCount() + " identities");

            // 获取原始 SessionService
            MinecraftSessionService originalSessionService = getOriginalSessionService();
            if (originalSessionService == null) {
                LOGGER.severe("XiMultiLogin: Failed to get original SessionService");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // 创建 XiSessionService
            xiSessionService = new XiSessionService(originalSessionService, configManager, identityGuard);
            LOGGER.info("XiMultiLogin: XiSessionService created");

            // 初始化注入器并注入
            injector = new XiInjector();
            boolean injected = injector.inject(xiSessionService);
            if (injected) {
                LOGGER.info("XiMultiLogin: Plugin enabled successfully");
                LOGGER.info("XiMultiLogin: Authentication pipeline initialized with " + xiSessionService.getProviders().size() + " providers");
            } else {
                LOGGER.severe("XiMultiLogin: Failed to inject XiSessionService");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } catch (Exception e) {
            LOGGER.severe("XiMultiLogin: Exception during enable: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        LOGGER.info("XiMultiLogin: Disabling plugin...");

        try {
            // 尝试卸载注入
            if (injector != null && injector.isInjected()) {
                boolean uninjected = injector.uninject();
                if (uninjected) {
                    LOGGER.info("XiMultiLogin: Successfully uninjected");
                } else {
                    LOGGER.warning("XiMultiLogin: Failed to uninject, but plugin is being disabled anyway");
                }
            }

            // 保存身份数据并关闭数据库连接
            if (identityGuard != null) {
                identityGuard.save();
                identityGuard.close();
                LOGGER.info("XiMultiLogin: Identity data saved and database connection closed");
            }

            // 清理资源
            injector = null;
            xiSessionService = null;
            identityGuard = null;
            configManager = null;

            LOGGER.info("XiMultiLogin: Plugin disabled successfully");
        } catch (Exception e) {
            LOGGER.severe("XiMultiLogin: Exception during disable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取原始的 SessionService
     *
     * @return 原始 SessionService
     */
    private MinecraftSessionService getOriginalSessionService() {
        try {
            // 获取服务器实例
            Object serverHandle = XiReflection.getNMSHandle(getServer());
            if (serverHandle == null) {
                LOGGER.severe("XiMultiLogin: Failed to get server handle");
                return null;
            }

            // 查找 SessionService 字段
            java.lang.reflect.Field sessionServiceField = findSessionServiceField(serverHandle.getClass());
            if (sessionServiceField == null) {
                LOGGER.severe("XiMultiLogin: Failed to find SessionService field");
                return null;
            }

            // 获取原始 SessionService
            Object sessionService = XiReflection.getFieldValue(serverHandle, sessionServiceField);
            if (sessionService == null) {
                LOGGER.severe("XiMultiLogin: SessionService is null");
                return null;
            }

            if (sessionService instanceof MinecraftSessionService) {
                LOGGER.info("XiMultiLogin: Found original SessionService: " + sessionService.getClass().getName());
                return (MinecraftSessionService) sessionService;
            } else {
                LOGGER.severe("XiMultiLogin: Found object is not a MinecraftSessionService: " + sessionService.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            LOGGER.severe("XiMultiLogin: Exception getting original SessionService: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 查找 SessionService 字段
     *
     * @param serverClass 服务器类
     * @return SessionService 字段
     */
    private java.lang.reflect.Field findSessionServiceField(Class<?> serverClass) {
        // 尝试通过类型查找
        java.lang.reflect.Field field = XiReflection.getFieldByType(serverClass, MinecraftSessionService.class);
        if (field != null) {
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
                java.lang.reflect.Field f = serverClass.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // 字段不存在，继续尝试下一个
            }
        }

        return null;
    }

    /**
     * 获取配置管理器
     *
     * @return 配置管理器
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 获取 XiSessionService
     *
     * @return XiSessionService
     */
    public XiSessionService getXiSessionService() {
        return xiSessionService;
    }

    /**
     * 获取身份守护者
     *
     * @return 身份守护者
     */
    public IdentityGuard getIdentityGuard() {
        return identityGuard;
    }
}
