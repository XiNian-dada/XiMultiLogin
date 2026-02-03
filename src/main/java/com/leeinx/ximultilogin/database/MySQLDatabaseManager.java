package com.leeinx.ximultilogin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * MySQL 数据库管理器
 * 使用 MySQL 作为数据库存储
 */
public class MySQLDatabaseManager implements DatabaseManager {

    private static final String TABLE_NAME = "ximultilogin_identities";
    
    private final Logger LOGGER;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private HikariDataSource dataSource;

    /**
     * 构造 MySQLDatabaseManager
     *
     * @param host     数据库主机
     * @param port     数据库端口
     * @param database 数据库名称
     * @param username 数据库用户名
     * @param password 数据库密码
     */
    public MySQLDatabaseManager(String host, int port, String database, String username, String password) {
        this.LOGGER = initializeLogger();
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
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
            return Logger.getLogger(MySQLDatabaseManager.class.getName());
        }
    }

    @Override
    public boolean initialize() {
        try {
            // 配置 HikariCP
            HikariConfig config = new HikariConfig();
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=utf8mb4";
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.jdbc.Driver");
            config.setConnectionTimeout(10000); // 10秒
            config.setMaximumPoolSize(10); // 最大连接数
            config.setMinimumIdle(2); // 最小空闲连接数
            config.setIdleTimeout(60000); // 空闲超时时间
            config.setMaxLifetime(1800000); // 连接最大生命周期
            config.setConnectionTestQuery("SELECT 1"); // 连接测试查询

            // 初始化数据源
            dataSource = new HikariDataSource(config);
            LOGGER.info("MySQLDatabaseManager: Connected to MySQL database: " + host + ":" + port + "/" + database);

            // 创建表结构
            createTable();
            return true;
        } catch (Exception e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to initialize database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (dataSource != null) {
                dataSource.close();
                LOGGER.info("MySQLDatabaseManager: Closed database connection pool");
            }
        } catch (Exception e) {
            LOGGER.warning("MySQLDatabaseManager: Error closing database connection pool: " + e.getMessage());
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
            LOGGER.warning("MySQLDatabaseManager: Error checking if name exists: " + e.getMessage());
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
            LOGGER.warning("MySQLDatabaseManager: Error getting UUID: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("MySQLDatabaseManager: Invalid UUID format: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean storeIdentity(String name, UUID uuid, String authProvider) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + TABLE_NAME + " (name, uuid, auth_provider, created_at) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, name);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, authProvider);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            LOGGER.warning("MySQLDatabaseManager: Error storing identity: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getAuthProvider(String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT auth_provider FROM " + TABLE_NAME + " WHERE name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("auth_provider");
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("MySQLDatabaseManager: Error getting auth provider: " + e.getMessage());
        }
        return null;
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
            LOGGER.warning("MySQLDatabaseManager: Error updating identity: " + e.getMessage());
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
            LOGGER.warning("MySQLDatabaseManager: Error deleting identity: " + e.getMessage());
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
            LOGGER.warning("MySQLDatabaseManager: Error getting identity count: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }

    /**
     * 创建表结构
     */
    private void createTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "name VARCHAR(16) UNIQUE NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "auth_provider VARCHAR(50) NOT NULL, " +
                    "created_at TIMESTAMP NOT NULL, " +
                    "updated_at TIMESTAMP, " +
                    "INDEX idx_name (name) " +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            stmt.executeUpdate(sql);
            LOGGER.info("MySQLDatabaseManager: Created table " + TABLE_NAME);
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to create table: " + e.getMessage());
        }
    }
}
