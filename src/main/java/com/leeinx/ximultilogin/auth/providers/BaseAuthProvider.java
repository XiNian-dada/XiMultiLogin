package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProvider;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * 认证提供者基类
 * 提取认证提供者的公共代码
 */
public abstract class BaseAuthProvider implements AuthProvider {

    protected static final Logger LOGGER = Bukkit.getLogger();
    protected final boolean enabled;
    protected final String name;

    /**
     * 构造 BaseAuthProvider
     * 
     * @param name    提供者名称
     * @param enabled 是否启用
     */
    public BaseAuthProvider(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    /**
     * 执行认证
     * 子类必须实现此方法
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 认证成功返回游戏档案对象，认证失败返回 null
     */
    @Override
    public abstract Object authenticate(String username, String serverId);

    /**
     * 获取提供者名称
     * 
     * @return 提供者名称
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 检查提供者是否启用
     * 
     * @return 是否启用
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 记录信息日志
     * 
     * @param message 日志消息
     */
    protected void info(String message) {
        LOGGER.info(name + "AuthProvider: " + message);
    }

    /**
     * 记录警告日志
     * 
     * @param message 日志消息
     */
    protected void warning(String message) {
        LOGGER.warning(name + "AuthProvider: " + message);
    }

    /**
     * 记录严重错误日志
     * 
     * @param message 日志消息
     */
    protected void severe(String message) {
        LOGGER.severe(name + "AuthProvider: " + message);
    }
}