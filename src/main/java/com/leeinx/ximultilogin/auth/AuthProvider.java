package com.leeinx.ximultilogin.auth;

import com.mojang.authlib.GameProfile;

/**
 * 验证提供者接口
 * 定义统一的身份验证方法，所有验证提供者都需要实现此接口
 */
public interface AuthProvider {

    /**
     * 验证玩家身份
     *
     * @param name     玩家名称
     * @param serverId 服务器唯一标识符
     * @return 验证成功返回 GameProfile，验证失败返回 null
     */
    GameProfile authenticate(String name, String serverId);

    /**
     * 获取验证提供者的名称
     * 用于日志输出和调试
     *
     * @return 验证提供者名称
     */
    String getName();

    /**
     * 检查验证提供者是否启用
     *
     * @return 是否启用
     */
    boolean isEnabled();
}
