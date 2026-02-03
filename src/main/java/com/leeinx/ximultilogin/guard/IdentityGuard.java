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
            // 老玩家，更新认证方式，保持UUID不变
            boolean updated = databaseManager.updateAuthProvider(name, storedUuid, authProvider);
            if (updated) {
                LOGGER.info("IdentityGuard: Auth provider updated for " + name + ": " + databaseManager.getAuthProvider(name) + " -> " + authProvider);
            }
            LOGGER.info("IdentityGuard: Identity verified: " + name + " -> " + storedUuid + " (using stored UUID)");
            return true;
        }
    }
    
    /**
     * 获取或创建玩家身份（UUID接管核心方法）
     * 
     * @param name 玩家名称
     * @param incomingUuid 传入的 UUID
     * @param authProvider 认证提供者名称
     * @return 固定的 UUID
     */
    public UUID getOrCreateIdentity(String name, UUID incomingUuid, String authProvider) {
        if (name == null || incomingUuid == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name, UUID, or authProvider is null");
            return null;
        }
        
        UUID storedUuid = databaseManager.getUUID(name);
        
        if (storedUuid == null) {
            // 第一次登录，使用传入的UUID
            boolean stored = databaseManager.storeIdentity(name, incomingUuid, authProvider);
            if (stored) {
                LOGGER.info("IdentityGuard: Created new identity: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                return incomingUuid;
            } else {
                LOGGER.warning("IdentityGuard: Failed to create identity: " + name);
                return null;
            }
        } else {
            // 老玩家，更新认证方式，返回存储的UUID
            boolean updated = databaseManager.updateAuthProvider(name, storedUuid, authProvider);
            if (updated) {
                LOGGER.info("IdentityGuard: Updated auth provider for " + name + " to " + authProvider + " (keeping UUID: " + storedUuid + ")");
            }
            return storedUuid;
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
     * 获取玩家的UUID
     * 
     * @param name 玩家名称
     * @return 玩家UUID，若不存在返回 null
     */
    public UUID getUUID(String name) {
        return databaseManager.getUUID(name);
    }

    /**
     * 更新玩家的认证提供者
     * 
     * @param name 玩家名称
     * @param uuid 玩家UUID
     * @param authProvider 新的认证提供者名称
     * @return 是否更新成功
     */
    public boolean updateAuthProvider(String name, UUID uuid, String authProvider) {
        if (name == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name or authProvider is null");
            return false;
        }
        
        // 检查玩家是否存在
        UUID storedUuid = databaseManager.getUUID(name);
        if (storedUuid != null) {
            // 玩家已存在，使用存储的UUID，只更新认证方式
            boolean updated = databaseManager.updateAuthProvider(name, storedUuid, authProvider);
            if (updated) {
                LOGGER.info("IdentityGuard: Auth provider updated for " + name + " to " + authProvider);
            } else {
                LOGGER.warning("IdentityGuard: Failed to update auth provider for " + name);
            }
            return updated;
        } else if (uuid != null) {
            // 玩家不存在但提供了UUID，存储新身份
            boolean stored = databaseManager.storeIdentity(name, uuid, authProvider);
            if (stored) {
                LOGGER.info("IdentityGuard: New identity registered: " + name + " -> " + uuid + " (" + authProvider + ")");
            } else {
                LOGGER.warning("IdentityGuard: Failed to store new identity: " + name + " -> " + uuid + " (" + authProvider + ")");
            }
            return stored;
        } else {
            // 玩家不存在且没有提供UUID
            LOGGER.warning("IdentityGuard: Player not found and no UUID provided for " + name);
            return false;
        }
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
