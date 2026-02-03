package com.leeinx.ximultilogin.database;

import java.util.UUID;

/**
 * 数据库管理器接口
 * 定义统一的数据库操作方法，支持不同类型的数据库实现
 */
public interface DatabaseManager {

    /**
     * 初始化数据库连接
     *
     * @return 初始化是否成功
     */
    boolean initialize();

    /**
     * 关闭数据库连接
     */
    void close();

    /**
     * 检查名称是否已存在
     *
     * @param name 玩家名称
     * @return 是否已存在
     */
    boolean exists(String name);

    /**
     * 获取名称对应的 UUID
     *
     * @param name 玩家名称
     * @return 对应的 UUID
     */
    UUID getUUID(String name);

    /**
     * 存储名称和 UUID 的映射
     *
     * @param name 玩家名称
     * @param uuid 玩家 UUID
     * @return 存储是否成功
     */
    boolean storeIdentity(String name, UUID uuid);

    /**
     * 更新名称对应的 UUID
     *
     * @param name 玩家名称
     * @param uuid 新的 UUID
     * @return 更新是否成功
     */
    boolean updateIdentity(String name, UUID uuid);

    /**
     * 删除名称和 UUID 的映射
     *
     * @param name 玩家名称
     * @return 删除是否成功
     */
    boolean deleteIdentity(String name);

    /**
     * 获取存储的身份数量
     *
     * @return 身份数量
     */
    int getIdentityCount();

    /**
     * 获取数据库类型
     *
     * @return 数据库类型
     */
    String getDatabaseType();
}
