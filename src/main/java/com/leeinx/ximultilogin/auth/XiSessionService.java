package com.Leeinx.ximultilogin.auth;

import com.Leeinx.ximultilogin.auth.providers.MojangAuthProvider;
import com.Leeinx.ximultilogin.auth.providers.YggdrasilAuthProvider;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 验证链管理器
 * 根据配置的验证链顺序依次尝试验证
 */
public class XiSessionService {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final List<AuthProvider> providers;
    private final Object originalSessionService;
    private final IdentityGuard identityGuard;

    /**
     * 构造 XiSessionService
     * 
     * @param originalSessionService 原始的 SessionService 实例
     * @param configManager          配置管理器
     * @param identityGuard          身份守护者
     */
    public XiSessionService(Object originalSessionService, ConfigManager configManager, IdentityGuard identityGuard) {
        this.originalSessionService = originalSessionService;
        this.identityGuard = identityGuard;
        this.providers = new ArrayList<>();
        initializeProviders(configManager);
    }

    /**
     * 初始化验证提供者列表
     * 
     * @param configManager 配置管理器
     */
    private void initializeProviders(ConfigManager configManager) {
        List<ConfigManager.ProviderConfig> pipelineConfig = configManager.getPipelineConfig();

        for (ConfigManager.ProviderConfig providerConfig : pipelineConfig) {
            if (!providerConfig.isEnabled()) {
                continue;
            }

            AuthProvider provider = createProvider(providerConfig);
            if (provider != null) {
                providers.add(provider);
                LOGGER.info("XiSessionService: Added provider to pipeline: " + provider.getName());
            }
        }

        LOGGER.info("XiSessionService: Initialized " + providers.size() + " providers in pipeline");
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
     * 这是核心验证方法，会按顺序尝试所有验证提供者
     * 
     * @param username   玩家名称
     * @param serverId   服务器唯一标识符
     * @param ipAddress  IP地址（可为null）
     * @return 验证成功返回 GameProfile，验证失败返回 null
     */
    public Object hasJoinedServer(String username, String serverId, java.net.InetAddress ipAddress) {
        LOGGER.info("XiSessionService: Authenticating player " + username + " with serverId " + serverId);

        if (providers.isEmpty()) {
            LOGGER.warning("XiSessionService: No providers available in pipeline");
            return null;
        }

        int attempts = 0;
        for (AuthProvider provider : providers) {
            attempts++;
            LOGGER.info("XiSessionService: Attempt " + attempts + "/" + providers.size() + ": Using " + provider.getName());

            try {
                Object profile = provider.authenticate(username, serverId);
                if (profile != null) {
                    // 验证身份锁定
                    boolean identityVerified = verifyIdentityWithReflection(profile, provider.getName());
                    if (identityVerified) {
                        LOGGER.info("XiSessionService: Authentication successful with " + provider.getName() + " for " + username);
                        return profile;
                    } else {
                        // 身份验证失败，拒绝登录
                        String profileName = getProfileNameWithReflection(profile);
                        LOGGER.warning("XiSessionService: Login rejected for user " + profileName + ": UUID mismatch caused by authentication mode switch.");
                        // 继续下一个提供者，可能是同一用户使用不同验证方式
                    }
                } else {
                    LOGGER.info("XiSessionService: Authentication failed with " + provider.getName() + ", trying next provider");
                }
            } catch (Exception e) {
                // 捕获所有异常，确保单个提供者的崩溃不会影响后续提供者
                LOGGER.warning("XiSessionService: Exception during authentication with " + provider.getName() + ": " + e.getMessage());
                // 继续下一个提供者
            }
        }

        LOGGER.info("XiSessionService: All providers failed to authenticate " + username);
        return null;
    }

    /**
     * 使用反射验证身份
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
            java.util.UUID id = (java.util.UUID) idObj;
            return identityGuard.verifyIdentity(name, id, providerName);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception verifying identity: " + e.getMessage());
            return false;
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
