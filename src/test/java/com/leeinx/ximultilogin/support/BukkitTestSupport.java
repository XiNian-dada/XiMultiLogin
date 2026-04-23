package com.Leeinx.ximultilogin.support;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.BeforeClass;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.logging.Logger;

import static org.mockito.Mockito.when;

public abstract class BukkitTestSupport {

    @BeforeClass
    public static void installBukkitServer() {
        Server server = Mockito.mock(Server.class);
        BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);

        when(server.getLogger()).thenReturn(Logger.getLogger("XiMultiLogin-Test"));
        when(server.getName()).thenReturn("XiMultiLogin-Test");
        when(server.getVersion()).thenReturn("XiMultiLogin-Test");
        when(server.getOnlinePlayers()).thenReturn(Collections.emptyList());
        when(server.getScheduler()).thenReturn(scheduler);

        Bukkit.setServer(server);
    }
}
