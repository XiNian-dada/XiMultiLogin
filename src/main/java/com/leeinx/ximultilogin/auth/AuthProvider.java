package com.Leeinx.ximultilogin.auth;

/**
 * 认证提供者接口
 * 定义认证提供者的通用方法
 */
public interface AuthProvider {

    /**
     * 执行认证
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 认证成功返回游戏档案对象，认证失败返回 null
     */
    Object authenticate(String username, String serverId);

    /**
     * 获取提供者名称
     * 
     * @return 提供者名称
     */
    String getName();

    /**
     * 检查提供者是否启用
     * 
     * @return 是否启用
     */
    boolean isEnabled();
}
