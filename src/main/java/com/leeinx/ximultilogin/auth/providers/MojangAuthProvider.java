package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProvider;

/**
 * Mojang 官方认证提供者
 * 实现 Mojang 官方的验证流程
 */
public class MojangAuthProvider extends BaseAuthProvider {

    private final Object sessionService;

    /**
     * 构造 MojangAuthProvider
     * 
     * @param sessionService Minecraft 会话服务
     * @param enabled        是否启用
     */
    public MojangAuthProvider(Object sessionService, boolean enabled) {
        super("MOJANG", enabled);
        this.sessionService = sessionService;
    }

    /**
     * 执行 Mojang 官方认证
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 认证成功返回 GameProfile，认证失败返回 null
     */
    @Override
    public Object authenticate(String username, String serverId) {
        if (!enabled) {
            info("Provider is disabled");
            return null;
        }

        try {
            info("Authenticating " + username + " with Mojang");
            // 使用反射调用 hasJoinedServer 方法
            Object profile = callHasJoinedServer(sessionService, username, serverId, null);
            if (profile != null) {
                info("Authentication successful for " + username);
            } else {
                info("Authentication failed for " + username);
            }
            return profile;
        } catch (Exception e) {
            warning("Exception during authentication: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射调用 hasJoinedServer 方法
     * 智能匹配不同的方法签名
     * 
     * @param sessionService Minecraft 会话服务
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @param ipAddress IP地址
     * @return 认证成功返回 GameProfile，认证失败返回 null
     */
    private Object callHasJoinedServer(Object sessionService, String username, String serverId, String ipAddress) {
        try {
            info("SessionService class: " + sessionService.getClass().getName());
            info("SessionService class loader: " + sessionService.getClass().getClassLoader());
            
            // 打印所有方法，以便调试
            info("Available methods:");
            for (java.lang.reflect.Method method : sessionService.getClass().getMethods()) {
                if (method.getName().equals("hasJoinedServer")) {
                    StringBuilder params = new StringBuilder();
                    for (Class<?> paramType : method.getParameterTypes()) {
                        params.append(paramType.getName()).append(", ");
                    }
                    if (params.length() > 0) {
                        params.setLength(params.length() - 2);
                    }
                    info("  - " + method.getName() + "(" + params + ")");
                }
            }
            
            // 尝试不同的方法签名
            // 1. 尝试 (GameProfile, String, InetAddress) 签名
            try {
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", gameProfileClass, String.class, java.net.InetAddress.class);
                info("Using method signature: (GameProfile, String, InetAddress)");
                
                // 创建一个临时的 GameProfile 对象
                Object gameProfile = null;
                try {
                    // 尝试使用 GameProfile 构造函数
                    java.lang.reflect.Constructor<?> constructor = gameProfileClass.getConstructor(java.util.UUID.class, String.class);
                    gameProfile = constructor.newInstance(null, username);
                    info("Created temporary GameProfile for " + username);
                } catch (Exception e) {
                    warning("Failed to create GameProfile: " + e.getMessage());
                    return null;
                }
                
                // 转换IP地址
                java.net.InetAddress inetAddress = null;
                if (ipAddress != null) {
                    inetAddress = java.net.InetAddress.getByName(ipAddress);
                }
                
                return method.invoke(sessionService, gameProfile, serverId, inetAddress);
            } catch (NoSuchMethodException e1) {
                // 2. 尝试 (String, String, String) 签名
                try {
                    java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class, String.class);
                    info("Using method signature: (String, String, String)");
                    return method.invoke(sessionService, username, serverId, ipAddress);
                } catch (NoSuchMethodException e2) {
                    // 3. 尝试 (String, String, java.net.InetAddress) 签名
                    try {
                        java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class, java.net.InetAddress.class);
                        info("Using method signature: (String, String, InetAddress)");
                        // 转换IP地址
                        java.net.InetAddress inetAddress = null;
                        if (ipAddress != null) {
                            inetAddress = java.net.InetAddress.getByName(ipAddress);
                        }
                        return method.invoke(sessionService, username, serverId, inetAddress);
                    } catch (NoSuchMethodException e3) {
                        // 4. 尝试 (String, String) 签名
                        try {
                            java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class);
                            info("Using method signature: (String, String)");
                            return method.invoke(sessionService, username, serverId);
                        } catch (NoSuchMethodException e4) {
                            warning("No matching hasJoinedServer method found");
                            return null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            warning("Exception calling hasJoinedServer: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
