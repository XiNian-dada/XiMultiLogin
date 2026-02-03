package com.leeinx.ximultilogin.guard;

import com.leeinx.ximultilogin.database.DatabaseManager;
import com.leeinx.ximultilogin.database.SQLiteDatabaseManager;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 身份守护者
 * 管理 "Name <-> UUID" 的持久化映射，防止同名不同UUID的情况
 */
public class IdentityGuard {

    private final Logger LOGGER;

    private final DatabaseManager databaseManager;

    /**
     * 构造 IdentityGuard
     *
     * @param dataFolder 插件数据文件夹
     */
    public IdentityGuard(File dataFolder) {
        // 初始化 logger，处理 Bukkit 未初始化的情况
        this.LOGGER = initializeLogger();
        
        // 暂时使用 SQLite 作为默认数据库
        // 后续会通过配置文件指定数据库类型
        SQLiteDatabaseManager sqliteManager = new SQLiteDatabaseManager(dataFolder);
        sqliteManager.initialize();
        this.databaseManager = sqliteManager;
        LOGGER.info("IdentityGuard: Initialized with " + databaseManager.getDatabaseType() + " database");
    }

    /**
     * 构造 IdentityGuard
     *
     * @param databaseManager 数据库管理器
     */
    public IdentityGuard(DatabaseManager databaseManager) {
        // 初始化 logger，处理 Bukkit 未初始化的情况
        this.LOGGER = initializeLogger();
        
        this.databaseManager = databaseManager;
        LOGGER.info("IdentityGuard: Initialized with " + databaseManager.getDatabaseType() + " database");
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
            return Logger.getLogger(IdentityGuard.class.getName());
        }
    }

    /**
     * 检查名称是否已被锁定
     *
     * @param name 玩家名称
     * @return 是否已被锁定
     */
    public boolean isLocked(String name) {
        return databaseManager.exists(name);
    }

    /**
     * 验证身份
     * 如果名称不存在，记录并返回 true
     * 如果存在且 UUID 匹配，返回 true
     * 否则返回 false
     *
     * @param name         玩家名称
     * @param incomingUuid 传入的 UUID
     * @param authProvider 认证提供者名称
     * @return 验证是否通过
     */
    public boolean verifyIdentity(String name, UUID incomingUuid, String authProvider) {
        if (name == null || incomingUuid == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name, UUID, or authProvider is null");
            return false;
        }

        UUID storedUuid = databaseManager.getUUID(name);

        if (storedUuid == null) {
            // 第一次登录，记录身份
            boolean stored = databaseManager.storeIdentity(name, incomingUuid, authProvider);
            if (stored) {
                LOGGER.info("IdentityGuard: New identity registered: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                return true;
            } else {
                LOGGER.warning("IdentityGuard: Failed to store new identity: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                return false;
            }
        } else {
            // 已存在身份，验证是否匹配
            boolean matches = storedUuid.equals(incomingUuid);
            if (matches) {
                LOGGER.info("IdentityGuard: Identity verified: " + name + " -> " + incomingUuid + " (" + databaseManager.getAuthProvider(name) + ")");
                return true;
            } else {
                LOGGER.warning("IdentityGuard: Identity mismatch for " + name + ": stored=" + storedUuid + ", incoming=" + incomingUuid + " (" + authProvider + ")");
                return false;
            }
        }
    }

    /**
     * 获取玩家的认证提供者
     *
     * @param name 玩家名称
     * @return 认证提供者名称，若不存在返回 null
     */
    public String getAuthProvider(String name) {
        return databaseManager.getAuthProvider(name);
    }

    /**
     * 保存身份映射数据
     * 对于数据库存储，此方法主要用于刷新连接
     */
    public void save() {
        // 数据库存储不需要显式保存
        LOGGER.info("IdentityGuard: Save operation completed (database storage)");
    }

    /**
     * 异步保存身份映射数据
     */
    public void saveAsync() {
        try {
            // 尝试使用 Bukkit 调度器
            if (Bukkit.getPluginManager() != null && Bukkit.getScheduler() != null) {
                Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("XiMultiLogin"), this::save);
            } else {
                // Bukkit 未初始化，同步执行
                save();
            }
        } catch (NullPointerException e) {
            // Bukkit 未初始化，同步执行
            save();
        }
    }

    /**
     * 获取身份映射大小
     *
     * @return 映射大小
     */
    public int getIdentityCount() {
        return databaseManager.getIdentityCount();
    }

    /**
     * 清除所有身份映射
     */
    public void clear() {
        // 注意：此方法在实际实现中需要谨慎使用
        // 这里只是一个示例，实际实现需要根据数据库类型编写相应的清除逻辑
        LOGGER.warning("IdentityGuard: Clear operation not implemented for database storage");
    }

    /**
     * 获取数据库管理器
     *
     * @return 数据库管理器
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
            LOGGER.info("IdentityGuard: Closed database connection");
        }
    }
}