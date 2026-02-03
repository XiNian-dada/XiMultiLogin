package com.leeinx.ximultilogin.database;

import java.io.File;

/**
 * 数据库工厂类
 * 根据配置创建合适的数据库管理器实例
 */
public class DatabaseFactory {

    /**
     * 创建数据库管理器实例
     *
     * @param type       数据库类型 (SQLite 或 MySQL)
     * @param dataFolder 插件数据文件夹
     * @param host       MySQL 主机
     * @param port       MySQL 端口
     * @param database   MySQL 数据库名称
     * @param username   MySQL 用户名
     * @param password   MySQL 密码
     * @return 数据库管理器实例
     */
    public static DatabaseManager createDatabaseManager(
            String type, 
            File dataFolder, 
            String host, 
            int port, 
            String database, 
            String username, 
            String password) {

        type = type.toUpperCase();

        switch (type) {
            case "MYSQL":
                return new MySQLDatabaseManager(host, port, database, username, password);
            case "SQLITE":
            default:
                return new SQLiteDatabaseManager(dataFolder);
        }
    }
}
