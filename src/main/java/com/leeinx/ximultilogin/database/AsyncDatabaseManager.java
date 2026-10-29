package com.Leeinx.ximultilogin.database;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 异步数据库管理器接口
 * 提供异步数据库操作方法
 */
public interface AsyncDatabaseManager {

    /**
     * 初始化数据库
     * 
     * @return 是否初始化成功
     */
    boolean initialize();

    /**
     * 关闭数据库连接
     */
    void close();

    /**
     * 异步存储身份映射
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 存储结果的 CompletableFuture
     */
    CompletableFuture<Boolean> storeIdentityAsync(String name, UUID uuid, String authProvider);
    
    /**
     * 同步存储身份映射
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 是否存储成功
     */
    boolean storeIdentity(String name, UUID uuid, String authProvider);

    /**
     * 异步更新玩家的认证提供者
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 更新结果的 CompletableFuture
     */
    CompletableFuture<Boolean> updateAuthProviderAsync(String name, UUID uuid, String authProvider);
    
    /**
     * 同步更新玩家的认证提供者
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 是否更新成功
     */
    boolean updateAuthProvider(String name, UUID uuid, String authProvider);

    /**
     * 异步获取玩家的 UUID
     * 
     * @param name 玩家名称
     * @return 玩家 UUID 的 CompletableFuture，若不存在返回 null
     */
    CompletableFuture<UUID> getUUIDAsync(String name);
    
    /**
     * 同步获取玩家的 UUID
     * 
     * @param name 玩家名称
     * @return 玩家 UUID，若不存在返回 null
     */
    UUID getUUID(String name);

    /**
     * 异步获取玩家的认证提供者
     * 
     * @param name 玩家名称
     * @return 认证提供者名称的 CompletableFuture，若不存在返回 null
     */
    CompletableFuture<String> getAuthProviderAsync(String name);
    
    /**
     * 同步获取玩家的认证提供者
     * 
     * @param name 玩家名称
     * @return 认证提供者名称，若不存在返回 null
     */
    String getAuthProvider(String name);

    /**
     * 异步检查玩家是否存在
     * 
     * @param name 玩家名称
     * @return 是否存在的 CompletableFuture
     */
    CompletableFuture<Boolean> existsAsync(String name);
    
    /**
     * 同步检查玩家是否存在
     * 
     * @param name 玩家名称
     * @return 是否存在
     */
    boolean exists(String name);

    /**
     * 异步删除玩家身份
     * 
     * @param name 玩家名称
     * @return 删除结果的 CompletableFuture
     */
    CompletableFuture<Boolean> deleteIdentityAsync(String name);
    
    /**
     * 同步删除玩家身份
     * 
     * @param name 玩家名称
     * @return 是否删除成功
     */
    boolean deleteIdentity(String name);
}