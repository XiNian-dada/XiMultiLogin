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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

public class XiSessionService {

    private static final Logger LOGGER = Bukkit.getLogger();

    private static final class ProviderAuthResult {
        private final AuthProvider provider;
        private final Object profile;

        private ProviderAuthResult(AuthProvider provider, Object profile) {
            this.provider = provider;
            this.profile = profile;
        }

        private boolean isSuccess() {
            return profile != null;
        }
    }

    private final List<AuthProvider> providers;
    private final java.util.Map<String, AuthProvider> providerMap;
    private final Object originalSessionService;
    private final IdentityGuard identityGuard;
    private final ConfigManager configManager;
    private final PlayerLoginListener loginListener;
    private final ExecutorService authExecutor;
    private final ScheduledExecutorService timeoutScheduler;

    public XiSessionService(Object originalSessionService, ConfigManager configManager, IdentityGuard identityGuard, PlayerLoginListener loginListener) {
        this.originalSessionService = originalSessionService;
        this.identityGuard = identityGuard;
        this.configManager = configManager;
        this.loginListener = loginListener;
        this.providers = new ArrayList<>();
        this.providerMap = new java.util.HashMap<>();
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
                            ? new ProviderAuthResult(lockedProvider, null)
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

                    LOGGER.warning("XiSessionService: Strict auth FAILED. Player locked to " + storedAuthProvider + " but verification failed");
                    return deferAuthFailure(username, "strict_auth_failed", storedAuthProvider);
                });
    }

    private CompletableFuture<ProviderAuthResult> authenticateProviderAsync(AuthProvider provider, String username, String serverId) {
        return withTimeout(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        long providerStartTime = System.currentTimeMillis();
                        Object profile = provider.authenticate(username, serverId);
                        long providerEndTime = System.currentTimeMillis();
                        LOGGER.info("XiSessionService: Provider " + provider.getName() + " took " + (providerEndTime - providerStartTime) + "ms");
                        return profile;
                    } catch (Exception e) {
                        LOGGER.warning("XiSessionService: Exception in provider " + provider.getName() + ": " + e.getMessage());
                        return null;
                    }
                }, authExecutor),
                5,
                TimeUnit.SECONDS,
                "XiSessionService: Provider " + provider.getName() + " timed out"
        ).exceptionally(ex -> {
            if (ex != null && ex.getMessage() != null) {
                LOGGER.warning("XiSessionService: Exception in provider " + provider.getName() + ": " + ex.getMessage());
            }
            return null;
        }).thenApply(profile -> new ProviderAuthResult(provider, profile));
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
                    result = new ProviderAuthResult(providersToAuthenticate.get(i), null);
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

    private CompletableFuture<Object> takeOverUUIDAsync(Object profile, String providerName) {
        try {
            String name = AuthlibAdapter.extractName(profile);
            java.util.UUID incomingUuid = AuthlibAdapter.extractUuid(profile);

            if (name == null || incomingUuid == null) {
                LOGGER.warning("XiSessionService: Missing profile identity data during async UUID takeover");
                return CompletableFuture.completedFuture(profile);
            }

            return identityGuard.getOrCreateIdentityAsync(name, incomingUuid, providerName)
                    .thenApply(fixedUuid -> {
                        if (fixedUuid == null) {
                            LOGGER.warning("XiSessionService: Failed to get fixed UUID for " + name);
                            return profile;
                        }

                        if (fixedUuid.equals(incomingUuid)) {
                            LOGGER.info("XiSessionService: UUID already fixed for " + name + ": " + fixedUuid);
                            return profile;
                        }

                        try {
                            Object newProfile = AuthlibAdapter.replaceIdentity(profile, fixedUuid, name);
                            LOGGER.info("XiSessionService: UUID taken over for " + name + ": " + incomingUuid + " -> " + fixedUuid);
                            return newProfile;
                        } catch (Exception e) {
                            LOGGER.warning("XiSessionService: Exception rebuilding auth result: " + e.getMessage());
                            return profile;
                        }
                    });
        } catch (Exception e) {
            LOGGER.warning("XiSessionService: Exception taking over UUID: " + e.getMessage());
            return CompletableFuture.completedFuture(profile);
        }
    }

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

    public Object getOriginalSessionService() {
        return originalSessionService;
    }

    public List<AuthProvider> getProviders() {
        return providers;
    }

    public IdentityGuard getIdentityGuard() {
        return identityGuard;
    }

    public void close() {
        authExecutor.shutdownNow();
        timeoutScheduler.shutdownNow();
    }
}
