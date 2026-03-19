package com.Leeinx.ximultilogin.guard;

import com.Leeinx.ximultilogin.database.AsyncDatabaseManager;
import com.Leeinx.ximultilogin.database.AsyncDatabaseManagerImpl;
import com.Leeinx.ximultilogin.database.DatabaseFactory;
import com.Leeinx.ximultilogin.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * 身份守护者
 * 管理名称-UUID-认证方式的持久化映射
 */
public class IdentityGuard {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final AsyncDatabaseManager databaseManager;
    private final ConcurrentMap<String, UUID> uuidCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> authProviderCache = new ConcurrentHashMap<>();
    private final Set<String> missingCache = ConcurrentHashMap.newKeySet();

    /**
     * 构造 IdentityGuard
     * 
     * @param configManager 配置管理器
     */
    public IdentityGuard(com.Leeinx.ximultilogin.config.ConfigManager configManager) {
        DatabaseManager delegate = DatabaseFactory.createDatabaseManager(configManager);
        // 使用性能配置的数据库线程池大小
        int dbThreadPoolSize = configManager.getPerformanceConfig().getDbThreadPoolSize();
        this.databaseManager = new AsyncDatabaseManagerImpl(delegate, dbThreadPoolSize);
        this.databaseManager.initialize();
        LOGGER.info("IdentityGuard: Initialized successfully with async database operations");
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
        return verifyIdentityAsync(name, incomingUuid, authProvider).join();
    }
    
    /**
     * 异步验证玩家身份
     * 
     * @param name 玩家名称
     * @param incomingUuid 传入的 UUID
     * @param authProvider 认证提供者名称
     * @return 身份验证是否通过的 CompletableFuture
     */
    public CompletableFuture<Boolean> verifyIdentityAsync(String name, UUID incomingUuid, String authProvider) {
        if (name == null || incomingUuid == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name, UUID, or authProvider is null");
            return CompletableFuture.completedFuture(false);
        }
        
        return databaseManager.getUUIDAsync(name)
                .thenCompose(storedUuid -> {
                    if (storedUuid == null) {
                        // 第一次登录，记录身份和认证方式
                        return databaseManager.storeIdentityAsync(name, incomingUuid, authProvider)
                                .thenApply(stored -> {
                                    if (stored) {
                                        cacheIdentity(name, incomingUuid, authProvider);
                                        LOGGER.info("IdentityGuard: New identity registered: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                                        return true;
                                    } else {
                                        LOGGER.warning("IdentityGuard: Failed to store new identity: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                                        return false;
                                    }
                                });
                    } else {
                        // 老玩家，更新认证方式，保持UUID不变
                        return databaseManager.updateAuthProviderAsync(name, storedUuid, authProvider)
                                .thenApply(updated -> {
                                    if (updated) {
                                        cacheIdentity(name, storedUuid, authProvider);
                                        LOGGER.info("IdentityGuard: Auth provider updated for " + name + " to " + authProvider);
                                    }
                                    LOGGER.info("IdentityGuard: Identity verified: " + name + " -> " + storedUuid + " (using stored UUID)");
                                    return true;
                                });
                    }
                });
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
        return getOrCreateIdentityAsync(name, incomingUuid, authProvider).join();
    }
    
    /**
     * 异步获取或创建玩家身份（UUID接管核心方法）
     * 
     * @param name 玩家名称
     * @param incomingUuid 传入的 UUID
     * @param authProvider 认证提供者名称
     * @return 固定的 UUID 的 CompletableFuture
     */
    public CompletableFuture<UUID> getOrCreateIdentityAsync(String name, UUID incomingUuid, String authProvider) {
        if (name == null || incomingUuid == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name, UUID, or authProvider is null");
            return CompletableFuture.completedFuture(null);
        }
        
        return databaseManager.getUUIDAsync(name)
                .thenCompose(storedUuid -> {
                    if (storedUuid == null) {
                        // 第一次登录，使用传入的UUID
                        return databaseManager.storeIdentityAsync(name, incomingUuid, authProvider)
                                .thenApply(stored -> {
                                    if (stored) {
                                        cacheIdentity(name, incomingUuid, authProvider);
                                        LOGGER.info("IdentityGuard: Created new identity: " + name + " -> " + incomingUuid + " (" + authProvider + ")");
                                        return incomingUuid;
                                    } else {
                                        LOGGER.warning("IdentityGuard: Failed to create identity: " + name);
                                        return null;
                                    }
                                });
                    } else {
                        // 老玩家，更新认证方式，返回存储的UUID
                        return databaseManager.updateAuthProviderAsync(name, storedUuid, authProvider)
                                .thenApply(updated -> {
                                    if (updated) {
                                        cacheIdentity(name, storedUuid, authProvider);
                                        LOGGER.info("IdentityGuard: Updated auth provider for " + name + " to " + authProvider + " (keeping UUID: " + storedUuid + ")");
                                    }
                                    return storedUuid;
                                });
                    }
                });
    }

    /**
     * 获取玩家的认证提供者
     * 
     * @param name 玩家名称
     * @return 认证提供者名称，若不存在返回 null
     */
    public String getAuthProvider(String name) {
        String cached = authProviderCache.get(name);
        if (cached != null) {
            return cached;
        }
        if (missingCache.contains(name)) {
            return null;
        }

        String authProvider = databaseManager.getAuthProvider(name);
        if (authProvider != null) {
            cacheAuthProvider(name, authProvider);
        } else if (!uuidCache.containsKey(name)) {
            missingCache.add(name);
        }
        return authProvider;
    }
    
    /**
     * 异步获取玩家的认证提供者
     * 
     * @param name 玩家名称
     * @return 认证提供者名称的 CompletableFuture，若不存在返回 null
     */
    public CompletableFuture<String> getAuthProviderAsync(String name) {
        String cached = authProviderCache.get(name);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (missingCache.contains(name)) {
            return CompletableFuture.completedFuture(null);
        }

        return databaseManager.getAuthProviderAsync(name)
                .thenApply(authProvider -> {
                    if (authProvider != null) {
                        cacheAuthProvider(name, authProvider);
                    } else if (!uuidCache.containsKey(name)) {
                        missingCache.add(name);
                    }
                    return authProvider;
                });
    }

    /**
     * 获取玩家的UUID
     * 
     * @param name 玩家名称
     * @return 玩家UUID，若不存在返回 null
     */
    public UUID getUUID(String name) {
        UUID cached = uuidCache.get(name);
        if (cached != null) {
            return cached;
        }
        if (missingCache.contains(name)) {
            return null;
        }

        UUID uuid = databaseManager.getUUID(name);
        if (uuid != null) {
            cacheUuid(name, uuid);
        } else if (!authProviderCache.containsKey(name)) {
            missingCache.add(name);
        }
        return uuid;
    }
    
    /**
     * 异步获取玩家的UUID
     * 
     * @param name 玩家名称
     * @return 玩家UUID的 CompletableFuture，若不存在返回 null
     */
    public CompletableFuture<UUID> getUUIDAsync(String name) {
        UUID cached = uuidCache.get(name);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (missingCache.contains(name)) {
            return CompletableFuture.completedFuture(null);
        }

        return databaseManager.getUUIDAsync(name)
                .thenApply(uuid -> {
                    if (uuid != null) {
                        cacheUuid(name, uuid);
                    } else if (!authProviderCache.containsKey(name)) {
                        missingCache.add(name);
                    }
                    return uuid;
                });
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
        return updateAuthProviderAsync(name, uuid, authProvider).join();
    }
    
    /**
     * 异步更新玩家的认证提供者
     * 
     * @param name 玩家名称
     * @param uuid 玩家UUID
     * @param authProvider 新的认证提供者名称
     * @return 是否更新成功的 CompletableFuture
     */
    public CompletableFuture<Boolean> updateAuthProviderAsync(String name, UUID uuid, String authProvider) {
        if (name == null || authProvider == null) {
            LOGGER.warning("IdentityGuard: Name or authProvider is null");
            return CompletableFuture.completedFuture(false);
        }
        
        // 检查玩家是否存在
        return databaseManager.getUUIDAsync(name)
                .thenCompose(storedUuid -> {
                    if (storedUuid != null) {
                        // 玩家已存在，使用存储的UUID，只更新认证方式
                        return databaseManager.updateAuthProviderAsync(name, storedUuid, authProvider)
                                .thenApply(updated -> {
                                    if (updated) {
                                        cacheIdentity(name, storedUuid, authProvider);
                                        LOGGER.info("IdentityGuard: Auth provider updated for " + name + " to " + authProvider);
                                    } else {
                                        LOGGER.warning("IdentityGuard: Failed to update auth provider for " + name);
                                    }
                                    return updated;
                                });
                    } else if (uuid != null) {
                        // 玩家不存在但提供了UUID，存储新身份
                        return databaseManager.storeIdentityAsync(name, uuid, authProvider)
                                .thenApply(stored -> {
                                    if (stored) {
                                        cacheIdentity(name, uuid, authProvider);
                                        LOGGER.info("IdentityGuard: New identity registered: " + name + " -> " + uuid + " (" + authProvider + ")");
                                    } else {
                                        LOGGER.warning("IdentityGuard: Failed to store new identity: " + name + " -> " + uuid + " (" + authProvider + ")");
                                    }
                                    return stored;
                                });
                    } else {
                        // 玩家不存在且没有提供UUID
                        LOGGER.warning("IdentityGuard: Player not found and no UUID provided for " + name);
                        return CompletableFuture.completedFuture(false);
                    }
                });
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

    public UUID getCachedUUID(String name) {
        return uuidCache.get(name);
    }

    public String getCachedAuthProvider(String name) {
        return authProviderCache.get(name);
    }

    public Set<String> getKnownPlayerNames() {
        Set<String> names = new HashSet<>(uuidCache.keySet());
        names.addAll(authProviderCache.keySet());
        return names;
    }

    public void warmupIdentity(String name) {
        getUUIDAsync(name);
        getAuthProviderAsync(name);
    }

    private void cacheIdentity(String name, UUID uuid, String authProvider) {
        cacheUuid(name, uuid);
        cacheAuthProvider(name, authProvider);
    }

    private void cacheUuid(String name, UUID uuid) {
        if (uuid != null) {
            uuidCache.put(name, uuid);
            missingCache.remove(name);
        }
    }

    private void cacheAuthProvider(String name, String authProvider) {
        if (authProvider != null) {
            authProviderCache.put(name, authProvider);
            missingCache.remove(name);
        }
    }
}
