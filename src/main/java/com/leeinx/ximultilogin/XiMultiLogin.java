package com.Leeinx.ximultilogin;

import com.Leeinx.ximultilogin.auth.XiSessionService;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.config.MessageManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import com.Leeinx.ximultilogin.command.XiCommandExecutor;
import com.Leeinx.ximultilogin.command.XiTabCompleter;
import com.Leeinx.ximultilogin.injector.XiInjector;
import com.Leeinx.ximultilogin.listener.PlayerLoginListener;
import com.Leeinx.ximultilogin.reflection.XiReflection;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * XiMultiLogin 主类
 * 插件的入口点，负责整合所有模块
 */
public class XiMultiLogin extends JavaPlugin {

    private static final Logger LOGGER = Bukkit.getLogger();
    private ConfigManager configManager;
    private MessageManager messageManager;
    private IdentityGuard identityGuard;
    private XiInjector xiInjector;
    private Object originalSessionService;
    private XiSessionService xiSessionService;
    private PlayerLoginListener loginListener;

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
        
        // 初始化消息管理器
        messageManager = new MessageManager(this);
        
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
        
        // 检测服务端Online Mode状态
        boolean onlineMode = getServer().getOnlineMode();
        if (!onlineMode) {
            LOGGER.severe("XiMultiLogin: Server is not in online mode! Plugin requires online mode to function properly.");
            LOGGER.severe("XiMultiLogin: Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        LOGGER.info("XiMultiLogin: Server is in online mode, proceeding with startup");
        
        // 检测调试模式
        boolean debugMode = configManager.isDebug();
        if (debugMode) {
            LOGGER.info("XiMultiLogin: Debug mode enabled - verbose logging will be used");
        }
        
        // 注册登录监听器
        loginListener = new PlayerLoginListener(this);
        getServer().getPluginManager().registerEvents(loginListener, this);
        LOGGER.info("XiMultiLogin: Player login listener registered");
        
        // 注入会话服务
        try {
            originalSessionService = xiInjector.getOriginalSessionService();
            if (originalSessionService == null) {
                LOGGER.severe("XiMultiLogin: Failed to get original session service");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // 创建自定义会话服务
            xiSessionService = new XiSessionService(originalSessionService, configManager, identityGuard, loginListener);
            
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
        
        // 注册命令执行器
        XiCommandExecutor commandExecutor = new XiCommandExecutor(this);
        getCommand("ximultilogin").setExecutor(commandExecutor);
        
        // 注册标签补全器
        XiTabCompleter tabCompleter = new XiTabCompleter(this);
        getCommand("ximultilogin").setTabCompleter(tabCompleter);
        LOGGER.info("XiMultiLogin: Command executor and tab completer registered");
        
        // 注册 PAPI 扩展
        registerPlaceholderExpansion();
        
        LOGGER.info("XiMultiLogin: Plugin enabled successfully");
    }
    
    /**
     * 注册 PAPI 占位符扩展
     */
    private void registerPlaceholderExpansion() {
        try {
            // 检查 PAPI 是否存在
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                // 直接创建并注册扩展
                com.Leeinx.ximultilogin.papi.XiPlaceholderExpansion expansion = new com.Leeinx.ximultilogin.papi.XiPlaceholderExpansion(this);
                expansion.register();
                LOGGER.info("XiMultiLogin: PlaceholderAPI expansion registered");
            } else {
                LOGGER.info("XiMultiLogin: PlaceholderAPI not found, skipping expansion registration");
            }
        } catch (Exception e) {
            LOGGER.warning("XiMultiLogin: Failed to register PlaceholderAPI expansion: " + e.getMessage());
        }
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
    
    /**
     * 获取消息管理器
     * 
     * @return 消息管理器实例
     */
    public MessageManager getMessageManager() {
        return messageManager;
    }
}
