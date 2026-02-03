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
     * 
     * @return 验证提供者配置列表
     */
    public List<ProviderConfig> getPipelineConfig() {
        List<ProviderConfig> providers = new ArrayList<>();
        ConfigurationSection pipelineSection = config.getConfigurationSection("pipeline");
        if (pipelineSection != null) {
            for (String key : pipelineSection.getKeys(false)) {
                ConfigurationSection providerSection = pipelineSection.getConfigurationSection(key);
                if (providerSection != null) {
                    ProviderConfig providerConfig = new ProviderConfig();
                    providerConfig.setType(providerSection.getString("type", "MOJANG"));
                    providerConfig.setEnabled(providerSection.getBoolean("enabled", true));
                    if ("YGGDRASIL".equalsIgnoreCase(providerConfig.getType())) {
                        providerConfig.setName(providerSection.getString("name", "Yggdrasil"));
                        providerConfig.setApiUrl(providerSection.getString("api", "https://authserver.mojang.com"));
                    } else {
                        providerConfig.setName(providerSection.getString("name", "Mojang"));
                    }
                    providers.add(providerConfig);
                }
            }
        } else {
            // 添加默认配置
            ProviderConfig mojangConfig = new ProviderConfig();
            mojangConfig.setType("MOJANG");
            mojangConfig.setEnabled(true);
            mojangConfig.setName("Mojang");
            providers.add(mojangConfig);
            LOGGER.info("XiMultiLogin: Using default pipeline config");
        }
        return providers;
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
