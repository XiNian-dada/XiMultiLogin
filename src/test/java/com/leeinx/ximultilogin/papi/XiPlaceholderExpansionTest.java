package com.Leeinx.ximultilogin.papi;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import com.Leeinx.ximultilogin.platform.SchedulerBridge;
import org.bukkit.OfflinePlayer;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class XiPlaceholderExpansionTest {

    private IdentityGuard identityGuard;
    private SchedulerBridge schedulerBridge;
    private XiPlaceholderExpansion expansion;
    private OfflinePlayer player;

    @Before
    public void setUp() {
        XiMultiLogin plugin = mock(XiMultiLogin.class);
        identityGuard = mock(IdentityGuard.class);
        schedulerBridge = mock(SchedulerBridge.class);
        player = mock(OfflinePlayer.class);

        when(plugin.getIdentityGuard()).thenReturn(identityGuard);
        when(plugin.getSchedulerBridge()).thenReturn(schedulerBridge);
        when(player.getName()).thenReturn("Steve");

        expansion = new XiPlaceholderExpansion(plugin);
    }

    @Test
    public void nonFoliaRequestsReadIdentityDirectly() {
        UUID uuid = UUID.randomUUID();

        when(schedulerBridge.isFoliaServer()).thenReturn(false);
        when(identityGuard.getAuthProvider("Steve")).thenReturn("MOJANG");
        when(identityGuard.getUUID("Steve")).thenReturn(uuid);

        expansion = new XiPlaceholderExpansion(mockPlugin(false));

        assertEquals("MOJANG", expansion.onRequest(player, "auth"));
        assertEquals(uuid.toString(), expansion.onRequest(player, "uuid"));

        verify(identityGuard, never()).warmupIdentity("Steve");
    }

    @Test
    public void foliaRequestsUseCachedIdentityWhenAvailable() {
        UUID uuid = UUID.randomUUID();

        when(schedulerBridge.isFoliaServer()).thenReturn(true);
        when(identityGuard.getCachedAuthProvider("Steve")).thenReturn("CRACKED");
        when(identityGuard.getCachedUUID("Steve")).thenReturn(uuid);

        expansion = new XiPlaceholderExpansion(mockPlugin(true));

        assertEquals("CRACKED", expansion.onRequest(player, "auth"));
        assertEquals("盗版", expansion.onRequest(player, "status"));
        assertEquals(uuid.toString(), expansion.onRequest(player, "uuid"));

        verify(identityGuard, never()).warmupIdentity("Steve");
    }

    @Test
    public void foliaCacheMissReturnsFallbackAndWarmsIdentity() {
        when(schedulerBridge.isFoliaServer()).thenReturn(true);
        when(identityGuard.getCachedAuthProvider("Steve")).thenReturn(null);
        when(identityGuard.getCachedUUID("Steve")).thenReturn(null);

        expansion = new XiPlaceholderExpansion(mockPlugin(true));

        assertEquals("Unknown", expansion.onRequest(player, "auth"));
        assertEquals("Unknown", expansion.onRequest(player, "uuid"));

        verify(identityGuard, atLeastOnce()).warmupIdentity("Steve");
    }

    private XiMultiLogin mockPlugin(boolean folia) {
        XiMultiLogin plugin = mock(XiMultiLogin.class);
        when(schedulerBridge.isFoliaServer()).thenReturn(folia);
        when(plugin.getIdentityGuard()).thenReturn(identityGuard);
        when(plugin.getSchedulerBridge()).thenReturn(schedulerBridge);
        return plugin;
    }
}
