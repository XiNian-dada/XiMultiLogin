package com.Leeinx.ximultilogin.auth;

import com.Leeinx.ximultilogin.auth.providers.BaseAuthProvider;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import com.Leeinx.ximultilogin.listener.PlayerLoginListener;
import com.Leeinx.ximultilogin.support.BukkitTestSupport;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class XiSessionServiceTest extends BukkitTestSupport {

    private ConfigManager configManager;
    private IdentityGuard identityGuard;
    private PlayerLoginListener loginListener;
    private XiSessionService sessionService;

    @Before
    public void setUp() {
        configManager = mock(ConfigManager.class);
        identityGuard = mock(IdentityGuard.class);
        loginListener = mock(PlayerLoginListener.class);

        when(configManager.getPipelineConfig()).thenReturn(Collections.emptyList());
        when(configManager.isBlockSameIdConflict()).thenReturn(false);
        when(configManager.isAllowCracked()).thenReturn(false);

        sessionService = new XiSessionService(new Object(), configManager, identityGuard, loginListener);
    }

    @After
    public void tearDown() {
        if (sessionService != null) {
            sessionService.close();
        }
    }

    @Test
    public void testPreserveAuthenticatedProfileProperties() {
        UUID uuid = UUID.randomUUID();
        GameProfile profileWithSkin = new GameProfile(uuid, "SkinPlayer");
        profileWithSkin.getProperties().put("textures", new Property("textures", "base64encodedTexture"));

        when(identityGuard.getAuthProviderAsync("SkinPlayer")).thenReturn(CompletableFuture.completedFuture("MOJANG"));
        when(identityGuard.getOrCreateIdentityAsync(eq("SkinPlayer"), any(UUID.class), eq("MOJANG")))
                .thenReturn(CompletableFuture.completedFuture(uuid));

        AuthProvider mojangProvider = new BaseAuthProvider("MOJANG", true) {
            @Override
            public Object authenticate(String username, String serverId) {
                return profileWithSkin;
            }
        };
        sessionService.registerProvider(mojangProvider);

        Object authResult = sessionService.hasJoinedServer("SkinPlayer", "testServerId", null);
        assertNotNull(authResult);

        // Test preserveAuthenticatedProfileProperties for getPackedTextures
        Object packedTextures = sessionService.preserveAuthenticatedProfileProperties(
                "getPackedTextures",
                new Object[]{uuid},
                null
        );
        assertNotNull(packedTextures);
        assertTrue(packedTextures instanceof Property);
        assertEquals("textures", ((Property) packedTextures).getName());
        assertEquals("base64encodedTexture", ((Property) packedTextures).getValue());

        // Test fillProfileProperties restoration
        GameProfile emptyProfile = new GameProfile(uuid, "SkinPlayer");
        sessionService.preserveAuthenticatedProfileProperties(
                "fillProfileProperties",
                new Object[]{emptyProfile},
                emptyProfile
        );
        assertTrue(emptyProfile.getProperties().containsKey("textures"));
    }

    @Test
    public void testLockedProviderAuthenticationSuccess() {
        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, "Steve");

        when(identityGuard.getAuthProviderAsync("Steve")).thenReturn(CompletableFuture.completedFuture("MOJANG"));
        when(identityGuard.getOrCreateIdentityAsync(eq("Steve"), any(UUID.class), eq("MOJANG")))
                .thenReturn(CompletableFuture.completedFuture(uuid));

        AuthProvider mojangProvider = new BaseAuthProvider("MOJANG", true) {
            @Override
            public Object authenticate(String username, String serverId) {
                return profile;
            }
        };
        sessionService.registerProvider(mojangProvider);

        Object result = sessionService.hasJoinedServer("Steve", "server1", null);
        assertNotNull(result);
        assertEquals("Steve", AuthlibAdapter.extractName(result));

        verify(loginListener).clearAuthFailure("Steve");
        verify(loginListener).recordAuthSuccess("Steve", "MOJANG");
    }

    @Test
    public void testLockedProviderUnavailableTriggersUnavailableKick() {
        when(identityGuard.getAuthProviderAsync("Steve")).thenReturn(CompletableFuture.completedFuture("MOJANG"));

        AuthProvider mojangProvider = new BaseAuthProvider("MOJANG", true) {
            @Override
            public Object authenticate(String username, String serverId) {
                throw new AuthProviderUnavailableException("Mojang session server timeout", null);
            }
        };
        sessionService.registerProvider(mojangProvider);

        Object result = sessionService.hasJoinedServer("Steve", "server1", null);
        assertNotNull(result);
        verify(loginListener).recordAuthUnavailable("Steve", "MOJANG");
    }

    @Test
    public void testBlockSameIdConflictDetection() {
        when(configManager.isBlockSameIdConflict()).thenReturn(true);

        UUID mojangUuid = UUID.randomUUID();
        UUID skinUuid = UUID.randomUUID();

        when(identityGuard.getAuthProviderAsync("ConflictSteve")).thenReturn(CompletableFuture.completedFuture("MOJANG"));
        when(identityGuard.getOrCreateIdentityAsync(eq("ConflictSteve"), any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mojangUuid));

        AuthProvider mojangProvider = new BaseAuthProvider("MOJANG", true) {
            @Override
            public Object authenticate(String username, String serverId) {
                return new GameProfile(mojangUuid, username);
            }
        };

        AuthProvider hairuoProvider = new BaseAuthProvider("HairuoSKY", true) {
            @Override
            public Object authenticate(String username, String serverId) {
                return new GameProfile(skinUuid, username);
            }
        };

        sessionService.registerProvider(mojangProvider);
        sessionService.registerProvider(hairuoProvider);

        Object result = sessionService.hasJoinedServer("ConflictSteve", "server1", null);
        assertNotNull(result);
        verify(loginListener).recordProviderConflict("ConflictSteve", "MOJANG", "HairuoSKY");
    }

    @Test
    public void testAllProvidersFailedForFirstTimeUser() {
        when(identityGuard.getAuthProviderAsync("NewPlayer")).thenReturn(CompletableFuture.completedFuture(null));

        AuthProvider mojangProvider = new BaseAuthProvider("MOJANG", true) {
            @Override
            public Object authenticate(String username, String serverId) {
                return null;
            }
        };
        sessionService.registerProvider(mojangProvider);

        Object result = sessionService.hasJoinedServer("NewPlayer", "server1", null);
        assertNotNull(result);
        verify(loginListener).recordAuthFailure("NewPlayer", "all_providers_failed", null);
    }
}
