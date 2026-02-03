package com.leeinx.ximultilogin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite 数据库管理器
 * 使用 SQLite 作为默认的数据库存储
 */
public class SQLiteDatabaseManager implements DatabaseManager {

    private static final String DATABASE_FILE = "identity.db";
    private static final String TABLE_NAME = "identities";
    
    private final Logger LOGGER;

    private final File databaseFile;
    private HikariDataSource dataSource;

    /**
     * 构造 SQLiteDatabaseManager
     *
     * @param dataFolder 插件数据文件夹
     */
    public SQLiteDatabaseManager(File dataFolder) {
        this.LOGGER = initializeLogger();
        this.databaseFile = new File(dataFolder, DATABASE_FILE);
        this.dataSource = null;
    }
    
    /**
     * 初始化日志记录器
     * 处理 Bukkit 未初始化的情况
     *
     * @return 日志记录器
     */
    private Logger initializeLogger() {
        try {
            return Bukkit.getLogger();
        } catch (NullPointerException e) {
            // Bukkit 未初始化，使用 Java 默认日志记录器
            return Logger.getLogger(SQLiteDatabaseManager.class.getName());
        }
    }

    @Override
    public boolean initialize() {
        try {
            // 配置 HikariCP
            HikariConfig config = new HikariConfig();
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            config.setJdbcUrl(url);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setConnectionTimeout(10000); // 10秒
            config.setMaximumPoolSize(5); // 最大连接数
            config.setMinimumIdle(1); // 最小空闲连接数
            config.setIdleTimeout(60000); // 空闲超时时间
            config.setMaxLifetime(1800000); // 连接最大生命周期

            // 初始化数据源
            dataSource = new HikariDataSource(config);
            LOGGER.info("SQLiteDatabaseManager: Connected to database: " + databaseFile.getAbsolutePath());

            // 创建表结构
            createTable();
            return true;
        } catch (Exception e) {
            LOGGER.severe("SQLiteDatabaseManager: Failed to initialize database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (dataSource != null) {
                dataSource.close();
                LOGGER.info("SQLiteDatabaseManager: Closed database connection pool");
            }
        } catch (Exception e) {
            LOGGER.warning("SQLiteDatabaseManager: Error closing database connection pool: " + e.getMessage());
        }
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public boolean exists(String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("SQLiteDatabaseManager: Error checking if name exists: " + e.getMessage());
        }
        return false;
    }

    @Override
    public UUID getUUID(String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid FROM " + TABLE_NAME + " WHERE name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String uuidString = rs.getString("uuid");
                    return UUID.fromString(uuidString);
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("SQLiteDatabaseManager: Error getting UUID: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("SQLiteDatabaseManager: Invalid UUID format: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean storeIdentity(String name, UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + TABLE_NAME + " (name, uuid, created_at) VALUES (?, ?, ?)")) {
            stmt.setString(1, name);
            stmt.setString(2, uuid.toString());
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            LOGGER.warning("SQLiteDatabaseManager: Error storing identity: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean updateIdentity(String name, UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "UPDATE " + TABLE_NAME + " SET uuid = ?, updated_at = ? WHERE name = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setString(3, name);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            LOGGER.warning("SQLiteDatabaseManager: Error updating identity: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteIdentity(String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM " + TABLE_NAME + " WHERE name = ?")) {
            stmt.setString(1, name);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            LOGGER.warning("SQLiteDatabaseManager: Error deleting identity: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getIdentityCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.warning("SQLiteDatabaseManager: Error getting identity count: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public String getDatabaseType() {
        return "SQLite";
    }

    /**
     * 创建表结构
     */
    private void createTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE NOT NULL, " +
                    "uuid TEXT NOT NULL, " +
                    "created_at TIMESTAMP NOT NULL, " +
                    "updated_at TIMESTAMP" +
                    ");";
            stmt.executeUpdate(sql);
            LOGGER.info("SQLiteDatabaseManager: Created table " + TABLE_NAME);
        } catch (SQLException e) {
            LOGGER.severe("SQLiteDatabaseManager: Failed to create table: " + e.getMessage());
        }
    }
}
