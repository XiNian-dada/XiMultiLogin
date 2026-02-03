package com.Leeinx.ximultilogin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * MySQL 数据库管理器
 * 实现 MySQL 数据库操作
 */
public class MySQLDatabaseManager implements DatabaseManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    /**
     * 构造 MySQLDatabaseManager
     * 
     * @param host     MySQL 服务器地址
     * @param port     MySQL 端口
     * @param database 数据库名称
     * @param username 数据库用户名
     * @param password 数据库密码
     */
    public MySQLDatabaseManager(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    /**
     * 初始化 MySQL 数据库
     * 
     * @return 是否初始化成功
     */
    @Override
    public boolean initialize() {
        try {
            // 配置 HikariCP
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            // 创建表
            createTables();
            LOGGER.info("MySQLDatabaseManager: Database initialized successfully");
            return true;
        } catch (Exception e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to initialize database: " + e.getMessage());
            return false;
        }
    }

    /**
     * 创建数据库表
     */
    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // 创建身份表
            String sql = "CREATE TABLE IF NOT EXISTS identities " +
                    "(id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(255) UNIQUE NOT NULL, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "auth_provider VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
            stmt.executeUpdate(sql);
            
            LOGGER.info("MySQLDatabaseManager: Tables created successfully");
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to create tables: " + e.getMessage());
        }
    }

    /**
     * 关闭数据库连接
     */
    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            LOGGER.info("MySQLDatabaseManager: Database connection closed");
        }
    }

    /**
     * 存储身份映射
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 是否存储成功
     */
    @Override
    public boolean storeIdentity(String name, UUID uuid, String authProvider) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO identities (name, uuid, auth_provider) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE uuid = VALUES(uuid), auth_provider = VALUES(auth_provider)")) {
            
            pstmt.setString(1, name);
            pstmt.setString(2, uuid.toString());
            pstmt.setString(3, authProvider);
            
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to store identity: " + e.getMessage());
            return false;
        }
    }

    /**
     * 更新玩家的认证提供者
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 是否更新成功
     */
    @Override
    public boolean updateAuthProvider(String name, UUID uuid, String authProvider) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE identities SET uuid = ?, auth_provider = ? WHERE name = ?")) {
            
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, authProvider);
            pstmt.setString(3, name);
            
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to update auth provider: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取玩家的 UUID
     * 
     * @param name 玩家名称
     * @return 玩家 UUID，若不存在返回 null
     */
    @Override
    public UUID getUUID(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT uuid FROM identities WHERE name = ?")) {
            
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to get UUID: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取玩家的认证提供者
     * 
     * @param name 玩家名称
     * @return 认证提供者名称，若不存在返回 null
     */
    @Override
    public String getAuthProvider(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT auth_provider FROM identities WHERE name = ?")) {
            
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("auth_provider");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to get auth provider: " + e.getMessage());
        }
        return null;
    }

    /**
     * 检查玩家是否存在
     * 
     * @param name 玩家名称
     * @return 是否存在
     */
    @Override
    public boolean exists(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM identities WHERE name = ?")) {
            
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to check existence: " + e.getMessage());
        }
        return false;
    }

    /**
     * 删除玩家身份
     * 
     * @param name 玩家名称
     * @return 是否删除成功
     */
    @Override
    public boolean deleteIdentity(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM identities WHERE name = ?")) {
            
            pstmt.setString(1, name);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.severe("MySQLDatabaseManager: Failed to delete identity: " + e.getMessage());
            return false;
        }
    }
}
