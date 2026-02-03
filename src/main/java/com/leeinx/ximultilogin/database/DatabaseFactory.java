package com.Leeinx.ximultilogin.database;

import com.Leeinx.ximultilogin.config.ConfigManager;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.logging.Logger;

/**
 * 数据库工厂
 * 负责创建数据库管理器实例
 */
public class DatabaseFactory {

    private static final Logger LOGGER = Bukkit.getLogger();

    /**
     * 创建数据库管理器
     * 
     * @param configManager 配置管理器
     * @return 数据库管理器实例
     */
    public static DatabaseManager createDatabaseManager(ConfigManager configManager) {
        ConfigManager.DatabaseConfig databaseConfig = configManager.getDatabaseConfig();
        String type = databaseConfig.getType();

        switch (type.toUpperCase()) {
            case "MYSQL":
                LOGGER.info("DatabaseFactory: Creating MySQL database manager");
                return createMySQLDatabaseManager(databaseConfig);
            case "SQLITE":
            default:
                LOGGER.info("DatabaseFactory: Creating SQLite database manager");
                return createSQLiteDatabaseManager(configManager);
        }
    }

    /**
     * 创建 SQLite 数据库管理器
     * 
     * @param configManager 配置管理器
     * @return SQLite 数据库管理器实例
     */
    private static DatabaseManager createSQLiteDatabaseManager(ConfigManager configManager) {
        // 使用默认路径
        File dataFolder = new File("plugins/XiMultiLogin");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "ximultilogin.db");
        return new SQLiteDatabaseManager(dbFile.getAbsolutePath());
    }

    /**
     * 创建 MySQL 数据库管理器
     * 
     * @param databaseConfig 数据库配置
     * @return MySQL 数据库管理器实例
     */
    private static DatabaseManager createMySQLDatabaseManager(ConfigManager.DatabaseConfig databaseConfig) {
        return new MySQLDatabaseManager(
                databaseConfig.getHost(),
                databaseConfig.getPort(),
                databaseConfig.getDatabase(),
                databaseConfig.getUsername(),
                databaseConfig.getPassword()
        );
    }
}
