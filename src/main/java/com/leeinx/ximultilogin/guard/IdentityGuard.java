package com.Leeinx.ximultilogin.guard;

import com.Leeinx.ximultilogin.database.DatabaseFactory;
import com.Leeinx.ximultilogin.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 身份守护者
 * 管理名称-UUID-认证方式的持久化映射
 */
public class IdentityGuard {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final DatabaseManager databaseManager;

    /**
     * 构造 IdentityGuard
     * 
     * @param configManager 配置管理器
     */
    public IdentityGuard(com.Leeinx.ximultilogin.config.ConfigManager configManager) {
        this.databaseManager = DatabaseFactory.createDatabaseManager(configManager);
        this.databaseManager.initialize();
        LOGGER.info("IdentityGuard: Initialized successfully");
    }

    /**
     * 验证玩家身份
     * 
     * @param name 玩家名称
     * @param incomingUuid 传入的 UUID
     * @param authProvider 认证提供者名称
     * @return 身份验证是否通过
     */
    public boolean verifyIdentity(String name, UUID incomingUuid, String authProvider) {
        if (name == null || incomingUuid == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name, UUID, or authProvider is null");
            return false;
        }
        
        UUID storedUuid = databaseManager.getUUID(name);
        
        if (storedUuid == null) {
            // 第一次登录，记录身份和认证方式
            boolean stored = databaseManager.storeIdentity(name, incomingUuid, authProvider);
            if (stored) {
                LOGGER.info("IdentityGuard: New identity registered: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                return true;
            } else {
                LOGGER.warning("IdentityGuard: Failed to store new identity: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                return false;
            }
        } else {
            // 老玩家，检查 UUID 是否匹配
            if (storedUuid.equals(incomingUuid)) {
                // UUID 匹配，检查认证方式是否一致
                String storedAuthProvider = databaseManager.getAuthProvider(name);
                if (storedAuthProvider != null && !storedAuthProvider.equals(authProvider)) {
                    // 认证方式变更，更新记录
                    boolean updated = databaseManager.storeIdentity(name, incomingUuid, authProvider);
                    if (updated) {
                        LOGGER.info("IdentityGuard: Auth provider updated for " + name + ": " + storedAuthProvider + " -> " + authProvider);
                    }
                }
                LOGGER.info("IdentityGuard: Identity verified: " + name + " -> " + incomingUuid);
                return true;
            } else {
                // UUID 不匹配，拒绝登录
                LOGGER.warning("IdentityGuard: Identity verification failed: " + name + " -> " + incomingUuid + " (expected: " + storedUuid + ")");
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
     * 关闭数据库连接
     */
    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
            LOGGER.info("IdentityGuard: Database connection closed");
        }
    }
}
