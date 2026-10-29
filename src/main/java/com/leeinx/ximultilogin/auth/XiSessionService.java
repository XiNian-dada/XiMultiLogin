package com.Leeinx.ximultilogin.auth;

import com.Leeinx.ximultilogin.auth.providers.MojangAuthProvider;
import com.Leeinx.ximultilogin.auth.providers.YggdrasilAuthProvider;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import com.Leeinx.ximultilogin.listener.PlayerLoginListener;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 验证链管理器
 * 根据配置的验证链顺序依次尝试验证
 */
public class XiSessionService {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static final long PROFILE_PROPERTY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10);

    private enum ProviderAuthStatus {
        SUCCESS,
        FAILED,
        UNAVAILABLE
    }

    private static final class ProviderAuthResult {
        private final AuthProvider provider;
        private final Object profile;
        private final ProviderAuthStatus status;

        private ProviderAuthResult(AuthProvider provider, Object profile, ProviderAuthStatus status) {
            this.provider = provider;
            this.profile = profile;
            this.status = status;
        }

        private static ProviderAuthResult success(AuthProvider provider, Object profile) {
            return new ProviderAuthResult(provider, profile, ProviderAuthStatus.SUCCESS);
        }

        private static ProviderAuthResult failed(AuthProvider provider) {
            return new ProviderAuthResult(provider, null, ProviderAuthStatus.FAILED);
        }

        private static ProviderAuthResult unavailable(AuthProvider provider) {
            return new ProviderAuthResult(provider, null, ProviderAuthStatus.UNAVAILABLE);
        }

        private boolean isSuccess() {
            return status == ProviderAuthStatus.SUCCESS && profile != null;
        }

        private boolean isUnavailable() {
            return status == ProviderAuthStatus.UNAVAILABLE;
        }
    }

    private static final class CachedProfile {
        private final Object profile;
        private final long cachedAt;

        private CachedProfile(Object profile) {
            this.profile = profile;
            this.cachedAt = System.currentTimeMillis();
        }

        private boolean isExpired(long now) {
            return now - cachedAt > PROFILE_PROPERTY_CACHE_TTL_MS;
        }
    }

    private final List<AuthProvider> providers;
    private final java.util.Map<String, AuthProvider> providerMap;
    private final java.util.Map<String, CachedProfile> authenticatedProfileCache;
    private final Object originalSessionService;
    private final IdentityGuard identityGuard;
    private final ConfigManager configManager;
    private final PlayerLoginListener loginListener;
    private final ExecutorService authExecutor;
    private final ScheduledExecutorService timeoutScheduler;

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
        this.authenticatedProfileCache = new ConcurrentHashMap<>();
        this.authExecutor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "XiMultiLogin-Auth-Thread");
                    t.setDaemon(true);
                    return t;
                }
        );
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "XiMultiLogin-Auth-Timeout");
            t.setDaemon(true);
            return t;
        });
        initializeProviders(configManager);
    }

    private void initializeProviders(ConfigManager configManager) {
        LOGGER.info("XiSessionService: Initializing providers...");
        List<ConfigManager.ProviderConfig> pipelineConfig = configManager.getPipelineConfig();

        LOGGER.info("XiSessionService: Pipeline config size: " + pipelineConfig.size());

        for (ConfigManager.ProviderConfig providerConfig : pipelineConfig) {
            LOGGER.info("XiSessionService: Processing provider config: " + providerConfig.getName()
                    + " (type: " + providerConfig.getType() + ", enabled: " + providerConfig.isEnabled() + ")");

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
            LOGGER.warning("XiSessionService: WARNING - No providers available in pipeline");
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

    public Object hasJoinedServer(String username, String serverId, java.net.InetAddress ipAddress) {
        try {
            return hasJoinedServerAsync(username, serverId, ipAddress).join();
        } catch (Exception e) {
            LOGGER.severe("XiSessionService: Exception in hasJoinedServer: " + e.getMessage());
            return null;
        }
    }

    public CompletableFuture<Object> hasJoinedServerAsync(String username, String serverId, java.net.InetAddress ipAddress) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("XiSessionService: Authenticating player " + username + " (async)");

        if (providers.isEmpty()) {
            long endTime = System.currentTimeMillis();
            LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (no providers)");
            return CompletableFuture.completedFuture(null);
        }

        return identityGuard.getAuthProviderAsync(username)
                .thenCompose(storedAuthProvider -> {
                    if (storedAuthProvider == null) {
                        return tryAllProvidersAsync(username, serverId)
                                .thenApply(result -> {
                                    long endTime = System.currentTimeMillis();
                                    LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (pipeline mode)");
                                    return result;
                                });
                    }

                    LOGGER.info("XiSessionService: Player " + username + " is LOCKED to provider: " + storedAuthProvider);

                    AuthProvider lockedProvider = providerMap.get(storedAuthProvider);
                    if (lockedProvider == null) {
                        LOGGER.warning("XiSessionService: Player locked to " + storedAuthProvider + " but that provider is missing from config");
                        LOGGER.warning("XiSessionService: Falling back to full pipeline (Safety Mechanism)");
                        return tryAllProvidersAsync(username, serverId)
                                .thenApply(result -> {
                                    long endTime = System.currentTimeMillis();
                                    LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (fallback to pipeline)");
                                    return result;
                                });
                    }

                    return authenticateLockedProviderAsync(username, serverId, storedAuthProvider, lockedProvider)
                            .thenApply(result -> {
                                long endTime = System.currentTimeMillis();
                                LOGGER.info("XiSessionService: Authentication completed in " + (endTime - startTime) + "ms (strict mode)");
                                return result;
                            });
                });
    }

    /**
     * 尝试所有认证提供者（异步）
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 验证结果的 CompletableFuture
     */
    private CompletableFuture<Object> tryAllProvidersAsync(String username, String serverId) {
        LOGGER.info("XiSessionService: New player detected. Trying all providers...");

        return authenticateProvidersAsync(providers, username, serverId)
                .thenCompose(results -> {
                    List<ProviderAuthResult> successfulResults = getSuccessfulResults(results, null);
                    if (configManager.isBlockSameIdConflict() && successfulResults.size() > 1) {
                        LOGGER.warning("XiSessionService: Multiple providers matched first login for " + username
                                + " (providers: " + joinProviderNames(successfulResults)
                                + "). Binding will follow the first successful provider in pipeline order.");
                    }

                    ProviderAuthResult firstSuccessfulResult = findFirstSuccessfulResult(results);
                    if (firstSuccessfulResult != null) {
                        LOGGER.info("XiSessionService: First-time auth successful via " + firstSuccessfulResult.provider.getName());
                        return finalizeSuccessfulAuthentication(username, firstSuccessfulResult);
                    }

                    List<ProviderAuthResult> unavailableResults = getUnavailableResults(results);
                    if (!unavailableResults.isEmpty()) {
                        String unavailableProviders = joinProviderNames(unavailableResults);
                        LOGGER.warning("XiSessionService: Authentication service unavailable for " + username
                                + " (providers: " + unavailableProviders + ")");
                        return deferAuthUnavailable(username, unavailableProviders);
                    }

                    LOGGER.info("XiSessionService: All providers failed to authenticate " + username);
                    if (loginListener != null) {
                        loginListener.recordAuthFailure(username, "all_providers_failed", null);
                    }

                    if (configManager.isAllowCracked()) {
                        LOGGER.info("XiSessionService: Allowing cracked player " + username + " to join");
                        Object temporaryProfile = createTemporaryProfile(username);
                        if (temporaryProfile != null) {
                            boolean identityVerified = verifyIdentityWithReflection(temporaryProfile, "CRACKED");
                            if (identityVerified) {
                                LOGGER.info("XiSessionService: Temporary identity created for cracked player " + username);
                                return CompletableFuture.completedFuture(temporaryProfile);
                            }
                        }
                    }

                    LOGGER.info("XiSessionService: Deferring rejection for all providers failed case");
                    return CompletableFuture.completedFuture(createTemporaryProfile(username));
                });
    }

    private CompletableFuture<Object> authenticateLockedProviderAsync(
            String username,
            String serverId,
            String storedAuthProvider,
            AuthProvider lockedProvider
    ) {
        if (!configManager.isBlockSameIdConflict()) {
            return authenticateProviderAsync(lockedProvider, username, serverId)
                    .thenCompose(result -> {
                        if (result.isSuccess()) {
                            LOGGER.info("XiSessionService: Strict auth successful via " + storedAuthProvider);
                            return finalizeSuccessfulAuthentication(username, result);
                        }

                        if (result.isUnavailable()) {
                            LOGGER.warning("XiSessionService: Strict auth unavailable. Player locked to "
                                    + storedAuthProvider + " but provider cannot be reached");
                            return deferAuthUnavailable(username, storedAuthProvider);
                        }

                        LOGGER.warning("XiSessionService: Strict auth FAILED. Player locked to " + storedAuthProvider + " but verification failed");
                        return deferAuthFailure(username, "strict_auth_failed", storedAuthProvider);
                    });
        }

        List<AuthProvider> providersToCheck = new ArrayList<>();
        providersToCheck.add(lockedProvider);
        for (AuthProvider provider : providers) {
            if (!provider.getName().equals(lockedProvider.getName())) {
                providersToCheck.add(provider);
            }
        }

        return authenticateProvidersAsync(providersToCheck, username, serverId)
                .thenCompose(results -> {
                    ProviderAuthResult lockedResult = results.isEmpty()
                            ? ProviderAuthResult.failed(lockedProvider)
                            : results.get(0);
                    List<ProviderAuthResult> conflictingResults = getSuccessfulResults(results, lockedProvider.getName());
                    if (!conflictingResults.isEmpty()) {
                        String conflictProviders = joinProviderNames(conflictingResults);
                        LOGGER.warning("XiSessionService: Provider conflict detected for locked player " + username
                                + " (locked: " + storedAuthProvider + ", conflicts: " + conflictProviders + ")");
                        return deferProviderConflict(username, storedAuthProvider, conflictProviders);
                    }

                    if (lockedResult.isSuccess()) {
                        LOGGER.info("XiSessionService: Strict auth successful via " + storedAuthProvider);
                        return finalizeSuccessfulAuthentication(username, lockedResult);
                    }

                    if (lockedResult.isUnavailable()) {
                        LOGGER.warning("XiSessionService: Strict auth unavailable. Player locked to "
                                + storedAuthProvider + " but provider cannot be reached");
                        return deferAuthUnavailable(username, storedAuthProvider);
                    }

                    LOGGER.warning("XiSessionService: Strict auth FAILED. Player locked to " + storedAuthProvider + " but verification failed");
                    return deferAuthFailure(username, "strict_auth_failed", storedAuthProvider);
                });
    }

    private CompletableFuture<ProviderAuthResult> authenticateProviderAsync(AuthProvider provider, String username, String serverId) {
        CompletableFuture<ProviderAuthResult> authFuture = CompletableFuture.supplyAsync(() -> {
            try {
                long providerStartTime = System.currentTimeMillis();
                Object profile = provider.authenticate(username, serverId);
                long providerEndTime = System.currentTimeMillis();
                LOGGER.info("XiSessionService: Provider " + provider.getName() + " took " + (providerEndTime - providerStartTime) + "ms");
                if (profile != null) {
                    return ProviderAuthResult.success(provider, profile);
                }
                return ProviderAuthResult.failed(provider);
            } catch (AuthProviderUnavailableException e) {
                LOGGER.warning("XiSessionService: Provider " + provider.getName() + " unavailable: " + e.getMessage());
                return ProviderAuthResult.unavailable(provider);
            } catch (Exception e) {
                LOGGER.warning("XiSessionService: Exception in provider " + provider.getName() + ": " + e.getMessage());
                return ProviderAuthResult.failed(provider);
            }
        }, authExecutor);

        return withTimeout(
                authFuture,
                5,
                TimeUnit.SECONDS,
                "XiSessionService: Provider " + provider.getName() + " timed out"
        ).thenApply(result -> {
            if (result == null) {
                return ProviderAuthResult.unavailable(provider);
            }
            return result;
        }).exceptionally(ex -> {
            if (ex != null && ex.getMessage() != null) {
                LOGGER.warning("XiSessionService: Exception in provider " + provider.getName() + ": " + ex.getMessage());
            }
            return ProviderAuthResult.failed(provider);
        });
    }

    private CompletableFuture<List<ProviderAuthResult>> authenticateProvidersAsync(
            List<AuthProvider> providersToAuthenticate,
            String username,
            String serverId
    ) {
        List<CompletableFuture<ProviderAuthResult>> providerFutures = new ArrayList<>();
        for (AuthProvider provider : providersToAuthenticate) {
            providerFutures.add(authenticateProviderAsync(provider, username, serverId));
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(providerFutures.toArray(new CompletableFuture[0]));
        CompletableFuture<Void> allOfWithTimeout = withTimeout(
                allOf,
                8,
                TimeUnit.SECONDS,
                "XiSessionService: Authentication process timed out"
        ).handle((ignored, throwable) -> null);

        return allOfWithTimeout.thenApply(v -> {
            List<ProviderAuthResult> results = new ArrayList<>();
            for (int i = 0; i < providersToAuthenticate.size(); i++) {
                ProviderAuthResult result = null;
                try {
                    result = providerFutures.get(i).join();
                } catch (Exception ignored) {
                }
                if (result == null) {
                    result = ProviderAuthResult.failed(providersToAuthenticate.get(i));
                }
                results.add(result);
            }
            return results;
        });
    }

    private List<ProviderAuthResult> getSuccessfulResults(List<ProviderAuthResult> results, String excludedProviderName) {
        List<ProviderAuthResult> successfulResults = new ArrayList<>();
        for (ProviderAuthResult result : results) {
            if (result == null || !result.isSuccess()) {
                continue;
            }
            if (excludedProviderName != null && excludedProviderName.equals(result.provider.getName())) {
                continue;
            }
            successfulResults.add(result);
        }
        return successfulResults;
    }

    private List<ProviderAuthResult> getUnavailableResults(List<ProviderAuthResult> results) {
        List<ProviderAuthResult> unavailableResults = new ArrayList<>();
        for (ProviderAuthResult result : results) {
            if (result != null && result.isUnavailable()) {
                unavailableResults.add(result);
            }
        }
        return unavailableResults;
    }

    private ProviderAuthResult findFirstSuccessfulResult(List<ProviderAuthResult> results) {
        for (ProviderAuthResult result : results) {
            if (result != null && result.isSuccess()) {
                return result;
            }
        }
        return null;
    }

    private CompletableFuture<Object> finalizeSuccessfulAuthentication(String username, ProviderAuthResult result) {
        if (loginListener != null) {
            loginListener.clearAuthFailure(username);
            loginListener.recordAuthSuccess(username, result.provider.getName());
        }
        return takeOverUUIDAsync(result.profile, result.provider.getName());
    }

    private CompletableFuture<Object> deferAuthFailure(String username, String reason, String provider) {
        LOGGER.warning("XiSessionService: Deferring rejection to show custom kick message");
        if (loginListener != null) {
            loginListener.recordAuthFailure(username, reason, provider);
            return CompletableFuture.completedFuture(createTemporaryProfile(username));
        }
        LOGGER.severe("XiSessionService: Login listener unavailable, failing closed for " + username);
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Object> deferAuthUnavailable(String username, String provider) {
        LOGGER.warning("XiSessionService: Deferring rejection to show authentication service unavailable message");
        if (loginListener != null) {
            loginListener.recordAuthUnavailable(username, provider);
            return CompletableFuture.completedFuture(createTemporaryProfile(username));
        }
        LOGGER.severe("XiSessionService: Login listener unavailable, failing closed for " + username);
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Object> deferProviderConflict(String username, String lockedProvider, String conflictProviders) {
        LOGGER.warning("XiSessionService: Deferring rejection to show provider conflict message");
        if (loginListener != null) {
            loginListener.recordProviderConflict(username, lockedProvider, conflictProviders);
            return CompletableFuture.completedFuture(createTemporaryProfile(username));
        }
        LOGGER.severe("XiSessionService: Login listener unavailable, failing closed for " + username);
        return CompletableFuture.completedFuture(null);
    }

    private String joinProviderNames(List<ProviderAuthResult> results) {
        StringBuilder builder = new StringBuilder();
        for (ProviderAuthResult result : results) {
            if (result == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(result.provider.getName());
        }
        return builder.toString();
    }

    private <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit, String timeoutMessage) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        ScheduledFuture<?> scheduledFuture = timeoutScheduler.schedule(() -> {
            if (timeoutFuture.complete(null) && timeoutMessage != null) {
                LOGGER.warning(timeoutMessage);
            }
        }, timeout, unit);

        return future.applyToEither(timeoutFuture, Function.identity())
                .whenComplete((result, throwable) -> scheduledFuture.cancel(false));
    }

    public Object preserveAuthenticatedProfileProperties(String methodName, Object[] args, Object result) {
        if (!isProfilePropertyMethod(methodName)) {
            return result;
        }

        CachedProfile cachedProfile = findCachedProfile(args, result);
        if (cachedProfile == null) {
            return result;
        }

        if (args != null) {
            for (Object arg : args) {
                AuthlibAdapter.copyProperties(cachedProfile.profile, arg);
            }
        }

        if ("getPackedTextures".equals(methodName)) {
            Object cachedTextures = AuthlibAdapter.findProperty(cachedProfile.profile, "textures");
            return cachedTextures != null ? cachedTextures : result;
        }

        if ("getTextures".equals(methodName) && result == null) {
            return null;
        }

        if (result != null) {
            AuthlibAdapter.copyProperties(cachedProfile.profile, result);
            return result;
        }

        return cachedProfile.profile;
    }

    private boolean isProfilePropertyMethod(String methodName) {
        return "fillProfileProperties".equals(methodName)
                || "fetchProfile".equals(methodName)
                || "getPackedTextures".equals(methodName)
                || "getTextures".equals(methodName);
    }

    private CachedProfile findCachedProfile(Object[] args, Object result) {
        CachedProfile cachedProfile = findCachedProfile(result);
        if (cachedProfile != null) {
            return cachedProfile;
        }

        if (args == null) {
            return null;
        }

        for (Object arg : args) {
            cachedProfile = findCachedProfile(arg);
            if (cachedProfile != null) {
                return cachedProfile;
            }
        }
        return null;
    }

    private CachedProfile findCachedProfile(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof java.util.UUID) {
            return getCachedProfile("uuid:" + value.toString());
        }

        Object profile = AuthlibAdapter.extractGameProfile(value);
        if (profile == null) {
            return null;
        }

        java.util.UUID uuid = AuthlibAdapter.extractUuid(profile);
        if (uuid != null) {
            CachedProfile cachedProfile = getCachedProfile("uuid:" + uuid.toString());
            if (cachedProfile != null) {
                return cachedProfile;
            }
        }

        String name = AuthlibAdapter.extractName(profile);
        if (name != null) {
            return getCachedProfile("name:" + normalizeProfileName(name));
        }
        return null;
    }

    private CachedProfile getCachedProfile(String key) {
        CachedProfile cachedProfile = authenticatedProfileCache.get(key);
        if (cachedProfile == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (cachedProfile.isExpired(now)) {
            authenticatedProfileCache.remove(key, cachedProfile);
            return null;
        }
        return cachedProfile;
    }

    private Object cacheAuthenticatedProfile(Object profile, String providerName) {
        if (profile == null || !AuthlibAdapter.hasProperty(profile, "textures")) {
            return profile;
        }

        Object gameProfile = AuthlibAdapter.extractGameProfile(profile);
        if (gameProfile == null) {
            return profile;
        }

        CachedProfile cachedProfile = new CachedProfile(gameProfile);
        java.util.UUID uuid = AuthlibAdapter.extractUuid(gameProfile);
        if (uuid != null) {
            authenticatedProfileCache.put("uuid:" + uuid.toString(), cachedProfile);
        }

        String name = AuthlibAdapter.extractName(gameProfile);
        if (name != null) {
            authenticatedProfileCache.put("name:" + normalizeProfileName(name), cachedProfile);
        }

        cleanupExpiredCachedProfiles();
        LOGGER.info("XiSessionService: Cached authenticated profile textures for " + name + " (provider: " + providerName + ")");
        return profile;
    }

    private void cleanupExpiredCachedProfiles() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, CachedProfile> entry : authenticatedProfileCache.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                authenticatedProfileCache.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private String normalizeProfileName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * 为盗版玩家创建临时GameProfile
     * 
     * @param username 玩家名称
     * @return 临时GameProfile，创建失败返回 null
     */
    private Object createTemporaryProfile(String username) {
        try {
            java.util.UUID uuid = identityGuard.getUUID(username);
            if (uuid == null) {
                uuid = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:".concat(username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            Object gameProfile = AuthlibAdapter.createAuthResult(uuid, username);
            LOGGER.info("XiSessionService: Created temporary profile for " + username + " with UUID " + uuid);
            return gameProfile;
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Failed to create temporary profile: " + e.getMessage());
            return null;
        }
    }

    public Object hasJoinedServer(Object profile, String serverId, java.net.InetAddress ipAddress) {
        try {
            String username = getProfileNameWithReflection(profile);
            LOGGER.info("XiSessionService: Authenticating player " + username + " (with GameProfile) with serverId " + serverId);
            return hasJoinedServer(username, serverId, ipAddress);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception in hasJoinedServer(GameProfile): " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射验证身份并接管UUID
     * 
     * @param profile      GameProfile 对象
     * @param providerName 提供者名称
     * @return 验证是否成功
     */
    private boolean verifyIdentityWithReflection(Object profile, String providerName) {
        try {
            String name = AuthlibAdapter.extractName(profile);
            java.util.UUID incomingUuid = AuthlibAdapter.extractUuid(profile);
            if (name == null || incomingUuid == null) {
                LOGGER.warning("XiSessionService: Missing profile identity data during verification");
                return false;
            }
            return identityGuard.verifyIdentity(name, incomingUuid, providerName);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception verifying identity: " + e.getMessage());
            return false;
        }
    }

    /**
     * 接管UUID并创建新的GameProfile
     * 
     * @param profile      原始GameProfile
     * @param providerName 提供者名称
     * @return 带有固定UUID的GameProfile
     */
    private Object takeOverUUID(Object profile, String providerName) {
        try {
            String name = AuthlibAdapter.extractName(profile);
            java.util.UUID incomingUuid = AuthlibAdapter.extractUuid(profile);

            if (name == null || incomingUuid == null) {
                LOGGER.warning("XiSessionService: Missing profile identity data during UUID takeover");
                return profile;
            }

            java.util.UUID fixedUuid = identityGuard.getOrCreateIdentity(name, incomingUuid, providerName);
            if (fixedUuid == null) {
                LOGGER.warning("XiSessionService: Failed to get fixed UUID for " + name);
                return profile;
            }

            if (fixedUuid.equals(incomingUuid)) {
                LOGGER.info("XiSessionService: UUID already fixed for " + name + ": " + fixedUuid);
                return profile;
            }

            Object newProfile = AuthlibAdapter.replaceIdentity(profile, fixedUuid, name);
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
     * @param profile      原始GameProfile
     * @param providerName 提供者名称
     * @return 带有固定UUID的GameProfile的 CompletableFuture
     */
    private CompletableFuture<Object> takeOverUUIDAsync(Object profile, String providerName) {
        try {
            String name = AuthlibAdapter.extractName(profile);
            java.util.UUID incomingUuid = AuthlibAdapter.extractUuid(profile);

            if (name == null || incomingUuid == null) {
                LOGGER.warning("XiSessionService: Missing profile identity data during async UUID takeover");
                return CompletableFuture.completedFuture(cacheAuthenticatedProfile(profile, providerName));
            }

            return identityGuard.getOrCreateIdentityAsync(name, incomingUuid, providerName)
                    .thenApply(fixedUuid -> {
                        if (fixedUuid == null) {
                            LOGGER.warning("XiSessionService: Failed to get fixed UUID for " + name);
                            return cacheAuthenticatedProfile(profile, providerName);
                        }

                        if (fixedUuid.equals(incomingUuid)) {
                            LOGGER.info("XiSessionService: UUID already fixed for " + name + ": " + fixedUuid);
                            return cacheAuthenticatedProfile(profile, providerName);
                        }

                        try {
                            Object newProfile = AuthlibAdapter.replaceIdentity(profile, fixedUuid, name);
                            LOGGER.info("XiSessionService: UUID taken over for " + name + ": " + incomingUuid + " -> " + fixedUuid);
                            return cacheAuthenticatedProfile(newProfile, providerName);
                        } catch (Exception e) {
                            LOGGER.warning("XiSessionService: Exception rebuilding auth result: " + e.getMessage());
                            return cacheAuthenticatedProfile(profile, providerName);
                        }
                    });
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception taking over UUID: " + e.getMessage());
            return CompletableFuture.completedFuture(cacheAuthenticatedProfile(profile, providerName));
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
            return AuthlibAdapter.createGameProfile(uuid, name);
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Failed to create GameProfile: " + e.getMessage());
            return null;
        }
    }

    private String getProfileNameWithReflection(Object profile) {
        String name = AuthlibAdapter.extractName(profile);
        if (name != null) {
            return name;
        }
        LOGGER.warning("XiSessionService: Exception getting profile name: unsupported auth result type");
        return "unknown";
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
     * 注册新的验证提供者（支持动态扩展或测试注入）
     *
     * @param provider 验证提供者
     */
    public void registerProvider(AuthProvider provider) {
        if (provider != null) {
            providers.add(provider);
            providerMap.put(provider.getName(), provider);
        }
    }

    /**
     * 获取身份守护者
     * 
     * @return 身份守护者
     */
    public IdentityGuard getIdentityGuard() {
        return identityGuard;
    }

    public void close() {
        authenticatedProfileCache.clear();
        authExecutor.shutdownNow();
        timeoutScheduler.shutdownNow();
    }
}
