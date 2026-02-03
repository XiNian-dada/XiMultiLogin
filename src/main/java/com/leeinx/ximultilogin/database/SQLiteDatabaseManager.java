package com.Leeinx.ximultilogin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite 数据库管理器
 * 实现 SQLite 数据库操作
 */
public class SQLiteDatabaseManager implements DatabaseManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private HikariDataSource dataSource;
    private final String dbPath;

    /**
     * 构造 SQLiteDatabaseManager
     * 
     * @param dbPath 数据库文件路径
     */
    public SQLiteDatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * 初始化 SQLite 数据库
     * 
     * @return 是否初始化成功
     */
    @Override
    public boolean initialize() {
        try {
            // 配置 HikariCP
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            // 创建表
            createTables();
            LOGGER.info("SQLiteDatabaseManager: Database initialized successfully");
            return true;
        } catch (Exception e) {
            LOGGER.severe("SQLiteDatabaseManager: Failed to initialize database: " + e.getMessage());
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
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE NOT NULL, " +
                    "uuid TEXT NOT NULL, " +
                    "auth_provider TEXT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
            stmt.executeUpdate(sql);
            
            LOGGER.info("SQLiteDatabaseManager: Tables created successfully");
        } catch (SQLException e) {
            LOGGER.severe("SQLiteDatabaseManager: Failed to create tables: " + e.getMessage());
        }
    }

    /**
     * 关闭数据库连接
     */
    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            LOGGER.info("SQLiteDatabaseManager: Database connection closed");
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
                     "INSERT OR REPLACE INTO identities (name, uuid, auth_provider) VALUES (?, ?, ?)")) {
            
            pstmt.setString(1, name);
            pstmt.setString(2, uuid.toString());
            pstmt.setString(3, authProvider);
            
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.severe("SQLiteDatabaseManager: Failed to store identity: " + e.getMessage());
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
            LOGGER.severe("SQLiteDatabaseManager: Failed to get UUID: " + e.getMessage());
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
            LOGGER.severe("SQLiteDatabaseManager: Failed to get auth provider: " + e.getMessage());
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
            LOGGER.severe("SQLiteDatabaseManager: Failed to check existence: " + e.getMessage());
        }
        return false;
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
                     "UPDATE identities SET auth_provider = ? WHERE name = ? AND uuid = ?")) {
            
            pstmt.setString(1, authProvider);
            pstmt.setString(2, name);
            pstmt.setString(3, uuid.toString());
            
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.severe("SQLiteDatabaseManager: Failed to update auth provider: " + e.getMessage());
            return false;
        }
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
            LOGGER.severe("SQLiteDatabaseManager: Failed to delete identity: " + e.getMessage());
            return false;
        }
    }
}
