package com.Leeinx.ximultilogin.listener;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.config.MessageManager;
import com.Leeinx.ximultilogin.support.BukkitTestSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlayerLoginListenerTest extends BukkitTestSupport {

    private MessageManager messageManager;
    private PlayerLoginListener listener;

    @Before
    public void setUp() {
        XiMultiLogin plugin = mock(XiMultiLogin.class);
        messageManager = mock(MessageManager.class);

        when(plugin.getMessageManager()).thenReturn(messageManager);
        listener = new PlayerLoginListener(plugin);
    }

    @Test
    public void asyncPreLoginDisallowsPlayerAndClearsFailureRecord() throws Exception {
        when(messageManager.getMessage("login.strict_auth_failed", "provider", "MOJANG")).thenReturn("strict kick");

        listener.recordAuthFailure("Steve", "strict_auth_failed", "MOJANG");

        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "Steve",
                InetAddress.getLoopbackAddress(),
                UUID.randomUUID()
        );
        listener.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        assertEquals("strict kick", event.getKickMessage());

        AsyncPlayerPreLoginEvent nextEvent = new AsyncPlayerPreLoginEvent(
                "Steve",
                InetAddress.getLoopbackAddress(),
                UUID.randomUUID()
        );
        listener.onAsyncPlayerPreLogin(nextEvent);

        assertNotEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, nextEvent.getLoginResult());
    }

    @Test
    public void playerLoginSafetyNetDisallowsPendingFailure() throws Exception {
        when(messageManager.getMessage("login.all_providers_failed")).thenReturn("all failed");

        listener.recordAuthFailure("Alex", "all_providers_failed", null);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alex");

        PlayerLoginEvent event = new PlayerLoginEvent(
                player,
                "localhost",
                InetAddress.getLoopbackAddress()
        );
        listener.onPlayerLogin(event);

        assertEquals(PlayerLoginEvent.Result.KICK_OTHER, event.getResult());
        assertEquals("all failed", event.getKickMessage());
    }

    @Test
    public void playerJoinSendsSuccessMessageOnlyOnce() {
        when(messageManager.getMessage("login.success", "provider", "MOJANG")).thenReturn("welcome back");

        listener.recordAuthSuccess("Steve", "MOJANG");

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Steve");

        PlayerJoinEvent firstJoin = new PlayerJoinEvent(player, null);
        listener.onPlayerJoin(firstJoin);
        listener.onPlayerJoin(new PlayerJoinEvent(player, null));

        verify(player, times(1)).sendMessage("welcome back");
    }
}
