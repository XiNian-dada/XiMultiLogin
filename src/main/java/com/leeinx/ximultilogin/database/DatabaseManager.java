package com.Leeinx.ximultilogin.database;

import java.util.UUID;

/**
 * 数据库管理器接口
 * 定义数据库操作的通用方法
 */
public interface DatabaseManager {

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
     * 存储身份映射
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 是否存储成功
     */
    boolean storeIdentity(String name, UUID uuid, String authProvider);
    
    /**
     * 更新玩家的认证提供者
     * 
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @param authProvider 认证提供者名称
     * @return 是否更新成功
     */
    boolean updateAuthProvider(String name, UUID uuid, String authProvider);

    /**
     * 获取玩家的 UUID
     * 
     * @param name 玩家名称
     * @return 玩家 UUID，若不存在返回 null
     */
    UUID getUUID(String name);

    /**
     * 获取玩家的认证提供者
     * 
     * @param name 玩家名称
     * @return 认证提供者名称，若不存在返回 null
     */
    String getAuthProvider(String name);

    /**
     * 检查玩家是否存在
     * 
     * @param name 玩家名称
     * @return 是否存在
     */
    boolean exists(String name);

    /**
     * 删除玩家身份
     * 
     * @param name 玩家名称
     * @return 是否删除成功
     */
    boolean deleteIdentity(String name);
}
