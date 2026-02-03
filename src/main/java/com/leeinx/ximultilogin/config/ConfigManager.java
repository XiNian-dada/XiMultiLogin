package com.Leeinx.ximultilogin.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 配置管理器
 * 负责加载和管理插件配置
 */
public class ConfigManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    /**
     * 构造 ConfigManager
     * 
     * @param plugin 插件实例
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载配置
     */
    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
            LOGGER.info("XiMultiLogin: Created default config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        LOGGER.info("XiMultiLogin: Config loaded successfully");
    }

    /**
     * 保存配置
     */
    public void saveConfig() {
        try {
            config.save(configFile);
            LOGGER.info("XiMultiLogin: Config saved successfully");
        } catch (IOException e) {
            LOGGER.severe("XiMultiLogin: Failed to save config: " + e.getMessage());
        }
    }

    /**
     * 获取配置
     * 
     * @return 配置对象
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * 获取验证链配置
     * 支持列表格式和映射格式的配置
     * 
     * @return 验证提供者配置列表
     */
    public List<ProviderConfig> getPipelineConfig() {
        List<ProviderConfig> providers = new ArrayList<>();
        
        LOGGER.info("XiMultiLogin: Loading pipeline configuration...");
        
        // 1. 尝试将 pipeline 作为列表读取（优先）
        List<?> pipelineList = config.getList("pipeline");
        LOGGER.info("XiMultiLogin: Pipeline list: " + (pipelineList != null ? pipelineList.size() + " items" : "null"));
        if (pipelineList != null && !pipelineList.isEmpty()) {
            LOGGER.info("XiMultiLogin: Loading pipeline as list format");
            for (Object item : pipelineList) {
                LOGGER.info("XiMultiLogin: Processing item: " + item.getClass().getName() + " = " + item);
                
                // 处理不同类型的列表项
                ConfigurationSection providerSection = null;
                if (item instanceof ConfigurationSection) {
                    providerSection = (ConfigurationSection) item;
                } else if (item instanceof java.util.Map) {
                    // 将 Map 转换为 ConfigurationSection
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                    org.bukkit.configuration.MemoryConfiguration memoryConfig = new org.bukkit.configuration.MemoryConfiguration();
                    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                        memoryConfig.set(entry.getKey().toString(), entry.getValue());
                    }
                    providerSection = memoryConfig;
                    LOGGER.info("XiMultiLogin: Converted Map to ConfigurationSection");
                }
                
                if (providerSection != null) {
                    ProviderConfig providerConfig = parseProviderConfig(providerSection);
                    if (providerConfig != null) {
                        providers.add(providerConfig);
                        LOGGER.info("XiMultiLogin: Successfully added provider: " + providerConfig.getName());
                    } else {
                        LOGGER.warning("XiMultiLogin: Failed to parse provider config");
                    }
                } else {
                    LOGGER.warning("XiMultiLogin: Item is not a ConfigurationSection or Map: " + item.getClass().getName());
                }
            }
        } else {
            // 2. 尝试将 pipeline 作为映射读取（向后兼容）
            ConfigurationSection pipelineSection = config.getConfigurationSection("pipeline");
            LOGGER.info("XiMultiLogin: Pipeline section: " + (pipelineSection != null ? "found" : "null"));
            if (pipelineSection != null) {
                LOGGER.info("XiMultiLogin: Loading pipeline as map format");
                for (String key : pipelineSection.getKeys(false)) {
                    LOGGER.info("XiMultiLogin: Processing key: " + key);
                    ConfigurationSection providerSection = pipelineSection.getConfigurationSection(key);
                    if (providerSection != null) {
                        ProviderConfig providerConfig = parseProviderConfig(providerSection);
                        if (providerConfig != null) {
                            providers.add(providerConfig);
                            LOGGER.info("XiMultiLogin: Successfully added provider: " + providerConfig.getName());
                        } else {
                            LOGGER.warning("XiMultiLogin: Failed to parse provider config for key: " + key);
                        }
                    } else {
                        LOGGER.warning("XiMultiLogin: Provider section is null for key: " + key);
                    }
                }
            } else {
                // 3. 使用默认配置
                LOGGER.warning("XiMultiLogin: No pipeline configuration found, using default");
                ProviderConfig mojangConfig = new ProviderConfig();
                mojangConfig.setType("MOJANG");
                mojangConfig.setEnabled(true);
                mojangConfig.setName("Mojang");
                providers.add(mojangConfig);
            }
        }
        
        // 打印加载的提供者
        LOGGER.info("XiMultiLogin: Loaded " + providers.size() + " providers:");
        for (ProviderConfig config : providers) {
            LOGGER.info("  - " + config.getName() + " (" + config.getType() + "): " + (config.isEnabled() ? "enabled" : "disabled"));
            if ("YGGDRASIL".equalsIgnoreCase(config.getType())) {
                LOGGER.info("    API URL: " + config.getApiUrl());
            }
        }
        
        return providers;
    }
    
    /**
     * 解析单个提供者配置
     * 
     * @param section 配置节
     * @return 提供者配置，解析失败返回 null
     */
    private ProviderConfig parseProviderConfig(ConfigurationSection section) {
        try {
            LOGGER.info("XiMultiLogin: Parsing provider config section: " + section.getName());
            LOGGER.info("XiMultiLogin: Section keys: " + section.getKeys(false));
            
            ProviderConfig providerConfig = new ProviderConfig();
            providerConfig.setType(section.getString("type", "MOJANG"));
            providerConfig.setEnabled(section.getBoolean("enabled", true));
            
            LOGGER.info("XiMultiLogin: Provider type: " + providerConfig.getType() + ", enabled: " + providerConfig.isEnabled());
            
            if ("YGGDRASIL".equalsIgnoreCase(providerConfig.getType())) {
                // 处理 YGGDRASIL 类型的配置
                providerConfig.setName(section.getString("name", "Yggdrasil"));
                // 支持 api 和 apiUrl 两种配置键名
                String apiUrl = section.getString("api");
                LOGGER.info("XiMultiLogin: Raw API URL from 'api' key: " + apiUrl);
                if (apiUrl == null) {
                    apiUrl = section.getString("apiUrl", "https://authserver.mojang.com");
                    LOGGER.info("XiMultiLogin: API URL from 'apiUrl' key: " + apiUrl);
                } else {
                    // 处理 apiUrl，去除首尾的空格和引号
                    apiUrl = apiUrl.trim();
                    // 去除所有可能的引号和反引号
                    apiUrl = apiUrl.replaceAll("[`\"']", "");
                    LOGGER.info("XiMultiLogin: Processed API URL: " + apiUrl);
                }
                providerConfig.setApiUrl(apiUrl);
            } else {
                // 处理其他类型的配置
                providerConfig.setName(section.getString("name", "Mojang"));
            }
            
            LOGGER.info("XiMultiLogin: Successfully parsed provider: " + providerConfig.getName());
            return providerConfig;
        } catch (Exception e) {
            LOGGER.warning("XiMultiLogin: Failed to parse provider config: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取数据库配置
     * 
     * @return 数据库配置
     */
    public DatabaseConfig getDatabaseConfig() {
        DatabaseConfig databaseConfig = new DatabaseConfig();
        ConfigurationSection databaseSection = config.getConfigurationSection("database");
        if (databaseSection != null) {
            databaseConfig.setType(databaseSection.getString("type", "SQLite"));
            ConfigurationSection mysqlSection = databaseSection.getConfigurationSection("mysql");
            if (mysqlSection != null) {
                databaseConfig.setHost(mysqlSection.getString("host", "localhost"));
                databaseConfig.setPort(mysqlSection.getInt("port", 3306));
                databaseConfig.setDatabase(mysqlSection.getString("database", "ximultilogin"));
                databaseConfig.setUsername(mysqlSection.getString("username", "root"));
                databaseConfig.setPassword(mysqlSection.getString("password", ""));
            }
        } else {
            // 默认使用 SQLite
            databaseConfig.setType("SQLite");
            LOGGER.info("XiMultiLogin: Using default database config");
        }
        return databaseConfig;
    }
    
    /**
     * 获取是否允许盗版玩家加入
     * 
     * @return 是否允许盗版玩家加入
     */
    public boolean isAllowCracked() {
        return config.getBoolean("allow_cracked", false);
    }
    
    /**
     * 设置是否允许盗版玩家加入
     * 
     * @param allowCracked 是否允许盗版玩家加入
     */
    public void setAllowCracked(boolean allowCracked) {
        config.set("allow_cracked", allowCracked);
        saveConfig();
        LOGGER.info("XiMultiLogin: Allow cracked set to " + allowCracked);
    }

    /**
     * 提供者配置类
     */
    public static class ProviderConfig {
        private String type;
        private boolean enabled;
        private String name;
        private String apiUrl;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }
    }

    /**
     * 数据库配置类
     */
    public static class DatabaseConfig {
        private String type;
        private String host;
        private int port;
        private String database;
        private String username;
        private String password;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
