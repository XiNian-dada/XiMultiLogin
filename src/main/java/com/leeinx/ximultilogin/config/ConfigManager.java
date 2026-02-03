package com.leeinx.ximultilogin.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 配置管理类
 * 加载和解析 YAML 配置文件，提供验证链配置
 */
public class ConfigManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /**
     * 构造配置管理器
     *
     * @param plugin 插件实例
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    /**
     * 重载配置文件
     */
    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }

        // 如果配置文件不存在，从资源文件复制
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
            LOGGER.info("ConfigManager: Created default config.yml");
        }

        // 加载配置文件
        config = YamlConfiguration.loadConfiguration(configFile);

        // 加载默认配置作为 fallback
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream));
                config.setDefaults(defaultConfig);
            }
        } catch (IOException e) {
            LOGGER.warning("ConfigManager: Exception loading default config: " + e.getMessage());
        }

        LOGGER.info("ConfigManager: Config reloaded successfully");
    }

    /**
     * 获取配置文件
     *
     * @return 配置文件
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    /**
     * 保存配置文件
     */
    public void saveConfig() {
        if (config == null || configFile == null) {
            return;
        }

        try {
            getConfig().save(configFile);
            LOGGER.info("ConfigManager: Config saved successfully");
        } catch (IOException e) {
            LOGGER.severe("ConfigManager: Exception saving config: " + e.getMessage());
        }
    }

    /**
     * 获取验证链配置
     *
     * @return 验证链配置列表
     */
    public List<ProviderConfig> getPipelineConfig() {
        List<ProviderConfig> pipeline = new ArrayList<>();
        ConfigurationSection pipelineSection = config.getConfigurationSection("pipeline");

        if (pipelineSection == null) {
            LOGGER.warning("ConfigManager: Pipeline section not found in config");
            return pipeline;
        }

        // 遍历配置中的验证提供者
        for (String key : pipelineSection.getKeys(false)) {
            ConfigurationSection providerSection = pipelineSection.getConfigurationSection(key);
            if (providerSection == null) {
                continue;
            }

            try {
                String type = providerSection.getString("type");
                boolean enabled = providerSection.getBoolean("enabled", true);

                if (type == null) {
                    LOGGER.warning("ConfigManager: Provider type not specified for " + key);
                    continue;
                }

                ProviderConfig providerConfig = new ProviderConfig();
                providerConfig.setType(type);
                providerConfig.setEnabled(enabled);

                // 根据类型设置额外参数
                if (type.equalsIgnoreCase("YGGDRASIL")) {
                    String name = providerSection.getString("name", "Yggdrasil");
                    String api = providerSection.getString("api");
                    if (api == null) {
                        LOGGER.warning("ConfigManager: API URL not specified for Yggdrasil provider " + name);
                        continue;
                    }
                    providerConfig.setName(name);
                    providerConfig.setApiUrl(api);
                }

                pipeline.add(providerConfig);
                LOGGER.info("ConfigManager: Loaded provider config: " + providerConfig.getType() + " (" + providerConfig.getName() + ") enabled: " + providerConfig.isEnabled());
            } catch (Exception e) {
                LOGGER.warning("ConfigManager: Exception loading provider config " + key + ": " + e.getMessage());
            }
        }

        return pipeline;
    }

    /**
     * 获取数据库配置
     *
     * @return 数据库配置
     */
    public DatabaseConfig getDatabaseConfig() {
        DatabaseConfig dbConfig = new DatabaseConfig();
        ConfigurationSection databaseSection = config.getConfigurationSection("database");

        if (databaseSection == null) {
            LOGGER.warning("ConfigManager: Database section not found in config, using default SQLite");
            dbConfig.setType("SQLite");
            return dbConfig;
        }

        try {
            String type = databaseSection.getString("type", "SQLite");
            dbConfig.setType(type);

            // 加载 MySQL 配置
            if (type.equalsIgnoreCase("MySQL")) {
                ConfigurationSection mysqlSection = databaseSection.getConfigurationSection("mysql");
                if (mysqlSection != null) {
                    String host = mysqlSection.getString("host", "localhost");
                    int port = mysqlSection.getInt("port", 3306);
                    String database = mysqlSection.getString("database", "ximultilogin");
                    String username = mysqlSection.getString("username", "root");
                    String password = mysqlSection.getString("password", "");

                    dbConfig.setHost(host);
                    dbConfig.setPort(port);
                    dbConfig.setDatabase(database);
                    dbConfig.setUsername(username);
                    dbConfig.setPassword(password);
                }
            }

            LOGGER.info("ConfigManager: Loaded database config: " + dbConfig.getType());
        } catch (Exception e) {
            LOGGER.warning("ConfigManager: Exception loading database config: " + e.getMessage());
        }

        return dbConfig;
    }

    /**
     * 数据库配置类
     * 存储数据库相关的配置信息
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

    /**
     * 验证提供者配置类
     * 存储单个验证提供者的配置信息
     */
    public static class ProviderConfig {
        private String type;
        private String name;
        private String apiUrl;
        private boolean enabled;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
