package com.Leeinx.ximultilogin.auth;

import com.Leeinx.ximultilogin.auth.providers.MojangAuthProvider;
import com.Leeinx.ximultilogin.auth.providers.YggdrasilAuthProvider;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import com.Leeinx.ximultilogin.listener.PlayerLoginListener;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 验证链管理器
 * 根据配置的验证链顺序依次尝试验证
 */
public class XiSessionService {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final List<AuthProvider> providers;
    private final java.util.Map<String, AuthProvider> providerMap; // 提供者名称到实例的映射
    private final Object originalSessionService;
    private final IdentityGuard identityGuard;
    private final ConfigManager configManager;
    private final PlayerLoginListener loginListener;

    /**
     * 构造 XiSessionService
     * 
     * @param originalSessionService 原始的 SessionService 实例
     * @param configManager          配置管理器
     * @param identityGuard          身份守护者
     * @param loginListener          登录监听器
     */
    public XiSessionService(Object originalSessionService, ConfigManager configManager, IdentityGuard identityGuard, PlayerLoginListener loginListener) {
        this.originalSessionService = originalSessionService;
        this.identityGuard = identityGuard;
        this.configManager = configManager;
        this.loginListener = loginListener;
        this.providers = new ArrayList<>();
        this.providerMap = new java.util.HashMap<>();
        initializeProviders(configManager);
    }

    /**
     * 初始化验证提供者列表
     * 
     * @param configManager 配置管理器
     */
    private void initializeProviders(ConfigManager configManager) {
        LOGGER.info("XiSessionService: Initializing providers...");
        List<ConfigManager.ProviderConfig> pipelineConfig = configManager.getPipelineConfig();
        
        LOGGER.info("XiSessionService: Pipeline config size: " + pipelineConfig.size());

        for (ConfigManager.ProviderConfig providerConfig : pipelineConfig) {
            LOGGER.info("XiSessionService: Processing provider config: " + providerConfig.getName() + 
                        " (type: " + providerConfig.getType() + ", enabled: " + providerConfig.isEnabled() + ")");
            
            if (!providerConfig.isEnabled()) {
                LOGGER.info("XiSessionService: Provider " + providerConfig.getName() + " is disabled, skipping");
                continue;
            }

            AuthProvider provider = createProvider(providerConfig);
            if (provider != null) {
                providers.add(provider);
                providerMap.put(provider.getName(), provider);
                LOGGER.info("XiSessionService: Added provider to pipeline: " + provider.getName());
            } else {
                LOGGER.warning("XiSessionService: Failed to create provider: " + providerConfig.getName());
            }
        }

        LOGGER.info("XiSessionService: Initialized " + providers.size() + " providers in pipeline");
        if (providers.isEmpty()) {
            LOGGER.warning("XiSessionService: WARNING - No providers available in pipeline!");
        }
    }

    /**
     * 根据配置创建验证提供者
     * 
     * @param providerConfig 提供者配置
     * @return 创建的验证提供者
     */
    private AuthProvider createProvider(ConfigManager.ProviderConfig providerConfig) {
        String type = providerConfig.getType().toUpperCase();

        switch (type) {
            case "MOJANG":
                return new MojangAuthProvider(originalSessionService, providerConfig.isEnabled());
            case "YGGDRASIL":
                return new YggdrasilAuthProvider(
                        providerConfig.getName(),
                        providerConfig.getApiUrl(),
                        providerConfig.isEnabled()
                );
            default:
                LOGGER.warning("XiSessionService: Unknown provider type: " + type);
                return null;
        }
    }

    /**
     * 验证玩家是否已加入服务器
     * 这是核心验证方法，严格使用玩家的历史登录方式
     * 
     * @param username   玩家名称
     * @param serverId   服务器唯一标识符
     * @param ipAddress  IP地址（可为null）
     * @return 验证成功返回 GameProfile，验证失败返回 null
     */
    public Object hasJoinedServer(String username, String serverId, java.net.InetAddress ipAddress) {
        try {
            // 调用异步方法并等待结果
            return hasJoinedServerAsync(username, serverId, ipAddress).join();
        } catch (Exception e) {
            LOGGER.severe("XiSessionService: Exception in hasJoinedServer: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 异步验证玩家是否已加入服务器
     * 这是核心验证方法，严格使用玩家的历史登录方式
     * 
     * @param username   玩家名称
     * @param serverId   服务器唯一标识符
     * @param ipAddress  IP地址（可为null）
     * @return 验证结果的 CompletableFuture
     */
    public CompletableFuture<Object> hasJoinedServerAsync(String username, String serverId, java.net.InetAddress ipAddress) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("XiSessionService: Authenticating player " + username + " (async)");

        if (providers.isEmpty()) {
            long endTime = System.currentTimeMillis();
            LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (no providers)");
            return CompletableFuture.completedFuture(null);
        }

        // 1. 检查历史记录 (Strict Mode)
        return identityGuard.getAuthProviderAsync(username)
                .thenCompose(storedAuthProvider -> {
                    if (storedAuthProvider != null) {
                        // ★★★ 严格锁定逻辑 ★★★
                        // 如果有记录，只尝试这一个。成功就进，失败就踢，绝不尝试其他。
                        LOGGER.info("XiSessionService: Player " + username + " is LOCKED to provider: " + storedAuthProvider);
                        
                        AuthProvider provider = providerMap.get(storedAuthProvider);
                        if (provider != null) {
                            try {
                                long providerStartTime = System.currentTimeMillis();
                                Object profile = provider.authenticate(username, serverId);
                                long providerEndTime = System.currentTimeMillis();
                                LOGGER.info("XiSessionService: Provider " + provider.getName() + " took " + (providerEndTime - providerStartTime) + "ms");
                                
                                if (profile != null) {
                                    // 验证成功，记录成功的提供者
                                    if (loginListener != null) {
                                        loginListener.clearAuthFailure(username);
                                        loginListener.recordAuthSuccess(username, provider.getName());
                                    }
                                    
                                    // 验证成功，接管 UUID
                                    LOGGER.info("XiSessionService: Strict auth successful via " + storedAuthProvider);
                                    return takeOverUUIDAsync(profile, provider.getName())
                                            .thenApply(result -> {
                                                long endTime = System.currentTimeMillis();
                                                LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (strict mode)");
                                                return result;
                                            });
                                } else {
                                    // 验证失败 -> 延迟拒绝（返回临时Profile）
                                    LOGGER.warning("XiSessionService: Strict auth FAILED. Player locked to " + storedAuthProvider + " but verification failed.");
                                    LOGGER.warning("XiSessionService: Deferring rejection to show custom kick message.");
                                    
                                    // 记录认证失败原因，用于显示自定义消息
                                    if (loginListener != null) {
                                        loginListener.recordAuthFailure(username, "strict_auth_failed", storedAuthProvider);
                                    }
                                    
                                    // 返回临时Profile，诱导NMS放行，然后在AsyncPlayerPreLoginEvent中踢出
                                    Object tempProfile = createTemporaryProfile(username);
                                    long endTime = System.currentTimeMillis();
                                    LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (strict mode failed)");
                                    return CompletableFuture.completedFuture(tempProfile);
                                }
                            } catch (Exception e) {
                                LOGGER.severe("XiSessionService: Provider error: " + e.getMessage());
                                
                                // 记录认证失败原因
                                if (loginListener != null) {
                                    loginListener.recordAuthFailure(username, "strict_auth_failed", storedAuthProvider);
                                }
                                
                                // 返回临时Profile，诱导NMS放行，然后在AsyncPlayerPreLoginEvent中踢出
                                Object tempProfile = createTemporaryProfile(username);
                                long endTime = System.currentTimeMillis();
                                LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (strict mode error)");
                                return CompletableFuture.completedFuture(tempProfile);
                            }
                        } else {
                            // 如果锁定的 Provider 被删了或者改名了
                            LOGGER.warning("XiSessionService: Player locked to " + storedAuthProvider + " but that provider is missing from config!");
                            LOGGER.warning("XiSessionService: Falling back to full pipeline (Safety Mechanism).");
                            // 只有这种极端配置错误情况，才允许回退，否则死循环进不去
                            return tryAllProvidersAsync(username, serverId)
                                    .thenApply(result -> {
                                        long endTime = System.currentTimeMillis();
                                        LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (fallback to pipeline)");
                                        return result;
                                    });
                        }
                    } else {
                        // 2. 新玩家逻辑 (遍历尝试)
                        return tryAllProvidersAsync(username, serverId)
                                .thenApply(result -> {
                                    long endTime = System.currentTimeMillis();
                                    LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (pipeline mode)");
                                    return result;
                                });
                    }
                });
    }
    
    // 认证线程池
    private static final java.util.concurrent.ExecutorService AUTH_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "XiMultiLogin-Auth-Thread");
                t.setDaemon(true);
                return t;
            }
    );
    
    /**
     * 尝试所有认证提供者（异步）
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 验证结果的 CompletableFuture
     */
    private CompletableFuture<Object> tryAllProvidersAsync(String username, String serverId) {
        LOGGER.info("XiSessionService: New player detected. Trying all providers...");
        
        // 并行尝试所有认证提供者
        List<CompletableFuture<Object>> providerFutures = new ArrayList<>();
        
        for (AuthProvider provider : providers) {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 设置5秒超时（Java 8 兼容方式）
                    CompletableFuture<Object> providerFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return provider.authenticate(username, serverId);
                        } catch (Exception e) {
                            LOGGER.warning("XiSessionService: Exception in provider " + provider.getName() + ": " + e.getMessage());
                            return null;
                        }
                    });
                    
                    // 实现超时逻辑
                    CompletableFuture<Object> timeoutFuture = new CompletableFuture<>();
                    new Thread(() -> {
                        try {
                            Thread.sleep(5000);
                            if (!providerFuture.isDone()) {
                                timeoutFuture.complete(null);
                                LOGGER.warning("XiSessionService: Provider " + provider.getName() + " timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                    
                    return providerFuture.applyToEither(timeoutFuture, result -> result).join();
                } catch (Exception e) {
                    LOGGER.warning("XiSessionService: Exception in provider " + provider.getName() + ": " + e.getMessage());
                    return null;
                }
            }, AUTH_EXECUTOR);
            providerFutures.add(future);
        }
        
        // 等待所有结果完成，最多等待8秒（Java 8 兼容方式）
        CompletableFuture<Void> allOf = CompletableFuture.allOf(providerFutures.toArray(new CompletableFuture[0]));
        CompletableFuture<Void> allOfWithTimeout = new CompletableFuture<>();
        
        // 实现超时逻辑
        new Thread(() -> {
            try {
                Thread.sleep(8000);
                if (!allOf.isDone()) {
                    allOfWithTimeout.complete(null);
                    LOGGER.warning("XiSessionService: Authentication process timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        allOf.thenAccept(v -> {
            if (!allOfWithTimeout.isDone()) {
                allOfWithTimeout.complete(null);
            }
        }).exceptionally(ex -> {
            if (!allOfWithTimeout.isDone()) {
                allOfWithTimeout.complete(null);
            }
            return null;
        });
        
        return allOfWithTimeout.thenCompose(v -> {
            // 找到第一个成功的认证提供者
            for (int i = 0; i < providerFutures.size(); i++) {
                try {
                    Object profile = providerFutures.get(i).get();
                    if (profile != null) {
                        AuthProvider provider = providers.get(i);
                        // 验证成功，记录成功的提供者
                        if (loginListener != null) {
                            loginListener.clearAuthFailure(username);
                            loginListener.recordAuthSuccess(username, provider.getName());
                        }
                        
                        LOGGER.info("XiSessionService: First-time auth successful via " + provider.getName());
                        return takeOverUUIDAsync(profile, provider.getName());
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            }
            
            // 所有认证方式都失败
            LOGGER.info("XiSessionService: All providers failed to authenticate " + username);
            
            // 记录所有认证方式都失败
            if (loginListener != null) {
                loginListener.recordAuthFailure(username, "all_providers_failed", null);
            }
            
            // 检查是否允许盗版玩家
            if (configManager.isAllowCracked()) {
                LOGGER.info("XiSessionService: Allowing cracked player " + username + " to join");
                // 为盗版玩家创建临时身份
                Object temporaryProfile = createTemporaryProfile(username);
                if (temporaryProfile != null) {
                    // 验证临时身份
                    boolean identityVerified = verifyIdentityWithReflection(temporaryProfile, "CRACKED");
                    if (identityVerified) {
                        LOGGER.info("XiSessionService: Temporary identity created for cracked player " + username);
                        return CompletableFuture.completedFuture(temporaryProfile);
                    }
                }
            }
            
            // 所有认证方式都失败，返回临时Profile，诱导NMS放行，然后在AsyncPlayerPreLoginEvent中踢出
            LOGGER.info("XiSessionService: Deferring rejection for all providers failed case.");
            return CompletableFuture.completedFuture(createTemporaryProfile(username));
        });
    }
    
    /**
     * 为盗版玩家创建临时GameProfile
     * 
     * @param username 玩家名称
     * @return 临时GameProfile，创建失败返回 null
     */
    private Object createTemporaryProfile(String username) {
        try {
            // 使用反射创建临时GameProfile
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            
            // 尝试获取真实UUID（如果存在）
            java.util.UUID uuid = identityGuard.getUUID(username);
            
            // 如果没有真实UUID，使用基于用户名的UUID
            if (uuid == null) {
                uuid = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:".concat(username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            
            // 创建GameProfile实例
            java.lang.reflect.Constructor<?> constructor = gameProfileClass.getConstructor(java.util.UUID.class, String.class);
            Object gameProfile = constructor.newInstance(uuid, username);
            
            LOGGER.info("XiSessionService: Created temporary profile for " + username + " with UUID " + uuid);
            return gameProfile;
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Failed to create temporary profile: " + e.getMessage());
            return null;
        }
    }

    /**
     * 验证玩家是否已加入服务器（重载方法，接受GameProfile参数）
     * 
     * @param profile    GameProfile对象
     * @param serverId   服务器唯一标识符
     * @param ipAddress  IP地址（可为null）
     * @return 验证成功返回 GameProfile，验证失败返回 null
     */
    public Object hasJoinedServer(Object profile, String serverId, java.net.InetAddress ipAddress) {
        try {
            // 从GameProfile中获取用户名
            String username = getProfileNameWithReflection(profile);
            LOGGER.info("XiSessionService: Authenticating player " + username + " (with GameProfile) with serverId " + serverId);
            
            // 调用原始的验证方法
            return hasJoinedServer(username, serverId, ipAddress);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception in hasJoinedServer(GameProfile): " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射验证身份并接管UUID
     * 
     * @param profile  GameProfile 对象
     * @param providerName 提供者名称
     * @return 验证是否成功
     */
    private boolean verifyIdentityWithReflection(Object profile, String providerName) {
        try {
            // 使用反射获取名称和ID
            String name = (String) profile.getClass().getMethod("getName").invoke(profile);
            Object idObj = profile.getClass().getMethod("getId").invoke(profile);
            // 将Object转换为UUID
            java.util.UUID incomingUuid = (java.util.UUID) idObj;
            
            // 验证身份并获取固定UUID
            return identityGuard.verifyIdentity(name, incomingUuid, providerName);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception verifying identity: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 接管UUID并创建新的GameProfile
     * 
     * @param profile 原始GameProfile
     * @param providerName 提供者名称
     * @return 带有固定UUID的GameProfile
     */
    private Object takeOverUUID(Object profile, String providerName) {
        try {
            // 使用反射获取名称和ID
            String name = (String) profile.getClass().getMethod("getName").invoke(profile);
            Object idObj = profile.getClass().getMethod("getId").invoke(profile);
            java.util.UUID incomingUuid = (java.util.UUID) idObj;
            
            // 获取固定UUID
            java.util.UUID fixedUuid = identityGuard.getOrCreateIdentity(name, incomingUuid, providerName);
            if (fixedUuid == null) {
                LOGGER.warning("XiSessionService: Failed to get fixed UUID for " + name);
                return profile;
            }
            
            // 如果UUID相同，直接返回
            if (fixedUuid.equals(incomingUuid)) {
                LOGGER.info("XiSessionService: UUID already fixed for " + name + ": " + fixedUuid);
                return profile;
            }
            
            // 使用固定UUID创建新的GameProfile
            Object newProfile = createGameProfile(fixedUuid, name);
            LOGGER.info("XiSessionService: UUID taken over for " + name + ": " + incomingUuid + " -> " + fixedUuid);
            return newProfile;
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception taking over UUID: " + e.getMessage());
            return profile;
        }
    }
    
    /**
     * 异步接管UUID并创建新的GameProfile
     * 
     * @param profile 原始GameProfile
     * @param providerName 提供者名称
     * @return 带有固定UUID的GameProfile的 CompletableFuture
     */
    private CompletableFuture<Object> takeOverUUIDAsync(Object profile, String providerName) {
        try {
            // 使用反射获取名称和ID
            String name = (String) profile.getClass().getMethod("getName").invoke(profile);
            Object idObj = profile.getClass().getMethod("getId").invoke(profile);
            java.util.UUID incomingUuid = (java.util.UUID) idObj;
            
            // 异步获取固定UUID
            return identityGuard.getOrCreateIdentityAsync(name, incomingUuid, providerName)
                    .thenApply(fixedUuid -> {
                        if (fixedUuid == null) {
                            LOGGER.warning("XiSessionService: Failed to get fixed UUID for " + name);
                            return profile;
                        }
                        
                        // 如果UUID相同，直接返回
                        if (fixedUuid.equals(incomingUuid)) {
                            LOGGER.info("XiSessionService: UUID already fixed for " + name + ": " + fixedUuid);
                            return profile;
                        }
                        
                        // 使用固定UUID创建新的GameProfile
                        try {
                            Object newProfile = createGameProfile(fixedUuid, name);
                            LOGGER.info("XiSessionService: UUID taken over for " + name + ": " + incomingUuid + " -> " + fixedUuid);
                            return newProfile;
                        } catch (Exception e) {
                            LOGGER.warning("XiSessionService: Exception creating GameProfile: " + e.getMessage());
                            return profile;
                        }
                    });
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception taking over UUID: " + e.getMessage());
            return CompletableFuture.completedFuture(profile);
        }
    }
    
    /**
     * 使用反射创建GameProfile
     * 
     * @param uuid UUID
     * @param name 名称
     * @return GameProfile对象
     */
    private Object createGameProfile(java.util.UUID uuid, String name) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            java.lang.reflect.Constructor<?> constructor = gameProfileClass.getConstructor(java.util.UUID.class, String.class);
            return constructor.newInstance(uuid, name);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Failed to create GameProfile: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射获取游戏档案名称
     * 
     * @param profile  GameProfile 对象
     * @return 游戏档案名称
     */
    private String getProfileNameWithReflection(Object profile) {
        try {
            return (String) profile.getClass().getMethod("getName").invoke(profile);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception getting profile name: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * 获取原始的 SessionService
     * 
     * @return 原始 SessionService
     */
    public Object getOriginalSessionService() {
        return originalSessionService;
    }

    /**
     * 获取验证提供者列表
     * 
     * @return 验证提供者列表
     */
    public List<AuthProvider> getProviders() {
        return providers;
    }

    /**
     * 获取身份守护者
     * 
     * @return 身份守护者
     */
    public IdentityGuard getIdentityGuard() {
        return identityGuard;
    }
}
