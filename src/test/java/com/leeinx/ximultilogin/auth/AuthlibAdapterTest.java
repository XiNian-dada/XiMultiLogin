package com.Leeinx.ximultilogin.auth;

import com.Leeinx.ximultilogin.support.BukkitTestSupport;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AuthlibAdapterTest extends BukkitTestSupport {

    public static class RecordStyleProfile {
        private final UUID id;
        private final String name;

        public RecordStyleProfile(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }
    }

    @Test
    public void testExtractName() {
        assertEquals("Steve", AuthlibAdapter.extractName("Steve"));
        assertNull(AuthlibAdapter.extractName(null));

        UUID uuid = UUID.randomUUID();
        GameProfile gameProfile = new GameProfile(uuid, "Alex");
        assertEquals("Alex", AuthlibAdapter.extractName(gameProfile));

        ProfileResult profileResult = new ProfileResult(gameProfile);
        assertEquals("Alex", AuthlibAdapter.extractName(profileResult));

        RecordStyleProfile recordStyle = new RecordStyleProfile(uuid, "Herobrine");
        assertEquals("Herobrine", AuthlibAdapter.extractName(recordStyle));
    }

    @Test
    public void testExtractUuid() {
        assertNull(AuthlibAdapter.extractUuid(null));

        UUID uuid = UUID.randomUUID();
        GameProfile gameProfile = new GameProfile(uuid, "Alex");
        assertEquals(uuid, AuthlibAdapter.extractUuid(gameProfile));

        ProfileResult profileResult = new ProfileResult(gameProfile);
        assertEquals(uuid, AuthlibAdapter.extractUuid(profileResult));

        RecordStyleProfile recordStyle = new RecordStyleProfile(uuid, "Herobrine");
        assertEquals(uuid, AuthlibAdapter.extractUuid(recordStyle));
    }

    @Test
    public void testCreateGameProfileAndAuthResult() {
        UUID uuid = UUID.randomUUID();
        Object profile = AuthlibAdapter.createGameProfile(uuid, "Steve");
        assertNotNull(profile);
        assertEquals("Steve", AuthlibAdapter.extractName(profile));
        assertEquals(uuid, AuthlibAdapter.extractUuid(profile));

        Object authResult = AuthlibAdapter.createAuthResult(uuid, "Steve");
        assertNotNull(authResult);
        assertEquals("Steve", AuthlibAdapter.extractName(authResult));
        assertEquals(uuid, AuthlibAdapter.extractUuid(authResult));
    }

    @Test
    public void testAdaptReturnValue() {
        assertNull(AuthlibAdapter.adaptReturnValue(null, String.class));
        assertEquals("test", AuthlibAdapter.adaptReturnValue("test", null));
        assertEquals("test", AuthlibAdapter.adaptReturnValue("test", Void.TYPE));
        assertEquals("test", AuthlibAdapter.adaptReturnValue("test", String.class));

        UUID uuid = UUID.randomUUID();
        GameProfile gameProfile = new GameProfile(uuid, "Steve");

        // Adapt to GameProfile class
        Object adaptedProfile = AuthlibAdapter.adaptReturnValue(gameProfile, GameProfile.class);
        assertEquals(gameProfile, adaptedProfile);

        ProfileResult resultWrapper = new ProfileResult(gameProfile);
        Object extractedProfile = AuthlibAdapter.adaptReturnValue(resultWrapper, GameProfile.class);
        assertEquals("Steve", AuthlibAdapter.extractName(extractedProfile));

        // Adapt to ProfileResult
        Object adaptedResult = AuthlibAdapter.adaptReturnValue(gameProfile, ProfileResult.class);
        assertNotNull(adaptedResult);
        assertTrue(adaptedResult instanceof ProfileResult);

        // Adapt to Optional
        Object optionalResult = AuthlibAdapter.adaptReturnValue(gameProfile, Optional.class);
        assertTrue(optionalResult instanceof Optional);
        Optional<?> opt = (Optional<?>) optionalResult;
        assertTrue(opt.isPresent());
        assertEquals("Steve", AuthlibAdapter.extractName(opt.get()));

        assertNull(AuthlibAdapter.adaptReturnValue(null, Optional.class));

        // Adapt to CompletableFuture
        Object futureResult = AuthlibAdapter.adaptReturnValue("asyncValue", CompletableFuture.class);
        assertTrue(futureResult instanceof CompletableFuture);
        assertEquals("asyncValue", ((CompletableFuture<?>) futureResult).join());
    }

    @Test
    public void testCopyPropertiesAndPropertyLookups() {
        assertFalse(AuthlibAdapter.hasProperty(null, "textures"));
        assertNull(AuthlibAdapter.findProperty(null, "textures"));

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        GameProfile source = new GameProfile(uuid1, "Source");
        GameProfile target = new GameProfile(uuid2, "Target");

        source.getProperties().put("textures", new Property("textures", "eyJ0ZXh0dXJlcyI6e319"));
        assertTrue(AuthlibAdapter.hasProperty(source, "textures"));
        assertNotNull(AuthlibAdapter.findProperty(source, "textures"));

        assertFalse(AuthlibAdapter.hasProperty(target, "textures"));
        AuthlibAdapter.copyProperties(source, target);
        assertTrue(AuthlibAdapter.hasProperty(target, "textures"));
    }

    @Test
    public void testReplaceIdentity() {
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        GameProfile original = new GameProfile(oldUuid, "OldName");
        original.getProperties().put("textures", new Property("textures", "dummy_texture"));

        Object replaced = AuthlibAdapter.replaceIdentity(original, newUuid, "NewName");
        assertNotNull(replaced);
        assertEquals("NewName", AuthlibAdapter.extractName(replaced));
        assertEquals(newUuid, AuthlibAdapter.extractUuid(replaced));
        assertTrue(AuthlibAdapter.hasProperty(replaced, "textures"));
    }
}
