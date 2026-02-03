package com.leeinx.ximultilogin.auth;

import com.leeinx.ximultilogin.auth.providers.MojangAuthProvider;
import com.leeinx.ximultilogin.auth.providers.YggdrasilAuthProvider;
import com.leeinx.ximultilogin.config.ConfigManager;
import com.leeinx.ximultilogin.guard.IdentityGuard;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 验证链管理器
 * 实现 MinecraftSessionService 接口，根据配置的验证链顺序依次尝试验证
 */
public class XiSessionService implements MinecraftSessionService {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final List<AuthProvider> providers;
    private final MinecraftSessionService originalSessionService;
    private final IdentityGuard identityGuard;

    /**
     * 构造 XiSessionService
     *
     * @param originalSessionService 原始的 SessionService 实例
     * @param configManager          配置管理器
     * @param identityGuard          身份守护者
     */
    public XiSessionService(MinecraftSessionService originalSessionService, ConfigManager configManager, IdentityGuard identityGuard) {
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
     * @throws AuthenticationUnavailableException 当所有验证提供者都不可用时
     */
    @Override
    public GameProfile hasJoinedServer(String username, String serverId, java.net.InetAddress ipAddress) throws AuthenticationUnavailableException {
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
                GameProfile profile = provider.authenticate(username, serverId);
                if (profile != null) {
                    // 验证身份锁定
                    boolean identityVerified = identityGuard.verifyIdentity(profile.getName(), profile.getId(), provider.getName());
                    if (identityVerified) {
                        LOGGER.info("XiSessionService: Authentication successful with " + provider.getName() + " for " + username);
                        return profile;
                    } else {
                        // 身份验证失败，拒绝登录
                        LOGGER.warning("XiSessionService: Login rejected for user " + profile.getName() + ": UUID mismatch caused by authentication mode switch.");
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
     * 原始方法，直接调用原始 SessionService
     */
    @Override
    public void joinServer(GameProfile gameProfile, String s, String s1) throws AuthenticationUnavailableException {
        originalSessionService.joinServer(gameProfile, s, s1);
    }

    /**
     * 原始方法，直接调用原始 SessionService
     */
    @Override
    public GameProfile fillProfileProperties(GameProfile gameProfile, boolean b) {
        return originalSessionService.fillProfileProperties(gameProfile, b);
    }

    /**
     * 获取原始的 SessionService
     *
     * @return 原始 SessionService
     */
    public MinecraftSessionService getOriginalSessionService() {
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
