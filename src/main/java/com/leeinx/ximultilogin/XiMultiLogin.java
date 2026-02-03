package com.Leeinx.ximultilogin;

import com.Leeinx.ximultilogin.auth.XiSessionService;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import com.Leeinx.ximultilogin.injector.XiInjector;
import com.Leeinx.ximultilogin.reflection.XiReflection;
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
    private IdentityGuard identityGuard;
    private XiInjector xiInjector;
    private Object originalSessionService;
    private XiSessionService xiSessionService;

    /**
     * 插件加载时调用
     */
    @Override
    public void onLoad() {
        LOGGER.info("XiMultiLogin: Loading plugin...");
        
        // 初始化配置管理器
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            if (dataFolder.mkdirs()) {
                LOGGER.info("XiMultiLogin: Created data folder");
            } else {
                LOGGER.severe("XiMultiLogin: Failed to create data folder");
            }
        }
        configManager = new ConfigManager(this);
        
        // 初始化身份守护者
        identityGuard = new IdentityGuard(configManager);
        
        // 初始化反射工具
        XiReflection.init();
        
        // 初始化注入器
        xiInjector = new XiInjector();
        
        LOGGER.info("XiMultiLogin: Plugin loaded successfully");
    }

    /**
     * 插件启用时调用
     */
    @Override
    public void onEnable() {
        LOGGER.info("XiMultiLogin: Enabling plugin...");
        
        // 注入会话服务
        try {
            originalSessionService = xiInjector.getOriginalSessionService();
            if (originalSessionService == null) {
                LOGGER.severe("XiMultiLogin: Failed to get original session service");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // 创建自定义会话服务
            xiSessionService = new XiSessionService(originalSessionService, configManager, identityGuard);
            
            // 注入自定义会话服务
            boolean injected = xiInjector.inject(xiSessionService);
            if (!injected) {
                LOGGER.severe("XiMultiLogin: Failed to inject session service");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            LOGGER.info("XiMultiLogin: Session service injected successfully");
        } catch (Exception e) {
            LOGGER.severe("XiMultiLogin: Exception during injection: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        LOGGER.info("XiMultiLogin: Plugin enabled successfully");
    }

    /**
     * 插件禁用时调用
     */
    @Override
    public void onDisable() {
        LOGGER.info("XiMultiLogin: Disabling plugin...");
        
        // 恢复原始会话服务
        if (originalSessionService != null && xiInjector != null) {
            try {
                boolean restored = xiInjector.restore(originalSessionService);
                if (restored) {
                    LOGGER.info("XiMultiLogin: Original session service restored");
                } else {
                    LOGGER.warning("XiMultiLogin: Failed to restore original session service");
                }
            } catch (Exception e) {
                LOGGER.severe("XiMultiLogin: Exception during restoration: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 关闭数据库连接
        if (identityGuard != null) {
            identityGuard.close();
        }
        
        LOGGER.info("XiMultiLogin: Plugin disabled successfully");
    }

    /**
     * 获取配置管理器
     * 
     * @return 配置管理器实例
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 获取身份守护者
     * 
     * @return 身份守护者实例
     */
    public IdentityGuard getIdentityGuard() {
        return identityGuard;
    }

    /**
     * 获取注入器
     * 
     * @return 注入器实例
     */
    public XiInjector getXiInjector() {
        return xiInjector;
    }

    /**
     * 获取原始会话服务
     * 
     * @return 原始会话服务实例
     */
    public Object getOriginalSessionService() {
        return originalSessionService;
    }

    /**
     * 获取自定义会话服务
     * 
     * @return 自定义会话服务实例
     */
    public XiSessionService getXiSessionService() {
        return xiSessionService;
    }
}
