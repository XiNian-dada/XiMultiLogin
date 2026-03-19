package com.Leeinx.ximultilogin.platform;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Runtime scheduler bridge for Bukkit/Spigot/Paper/Folia.
 * Uses reflection so the plugin can keep compiling against old Bukkit APIs.
 */
public class SchedulerBridge {

    private static final Logger LOGGER = Bukkit.getLogger();

    private final JavaPlugin plugin;
    private final boolean modernSchedulersAvailable;
    private final boolean foliaServer;

    public SchedulerBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        Object server = Bukkit.getServer();
        this.modernSchedulersAvailable =
                hasMethod(server.getClass(), "getAsyncScheduler")
                        && hasMethod(server.getClass(), "getGlobalRegionScheduler");
        this.foliaServer = detectFolia(server);
    }

    public boolean hasModernSchedulers() {
        return modernSchedulersAvailable;
    }

    public boolean isFoliaServer() {
        return foliaServer;
    }

    public void runAsync(Runnable task) {
        if (modernSchedulersAvailable && runWithAsyncScheduler(task)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runGlobal(Runnable task) {
        if (modernSchedulersAvailable && runWithGlobalScheduler(task)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Entity) {
            runForEntity((Entity) sender, task);
            return;
        }
        runGlobal(task);
    }

    public void runForEntity(Entity entity, Runnable task) {
        if (modernSchedulersAvailable) {
            boolean scheduled = runWithEntityScheduler(entity, task);
            if (scheduled || foliaServer) {
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public <T> CompletableFuture<T> supplyForEntity(Entity entity, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (modernSchedulersAvailable) {
            try {
                Method getScheduler = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getScheduler.invoke(entity);
                Method run = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                Consumer<Object> consumer = scheduledTask -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable throwable) {
                        future.completeExceptionally(throwable);
                    }
                };
                Runnable retired = () -> future.complete(null);
                Object scheduledTask = run.invoke(entityScheduler, plugin, consumer, retired);
                if (scheduledTask != null || foliaServer) {
                    if (scheduledTask == null) {
                        future.complete(null);
                    }
                    return future;
                }
            } catch (NoSuchMethodException ignored) {
                if (foliaServer) {
                    future.complete(null);
                    return future;
                }
            } catch (Exception e) {
                if (foliaServer) {
                    LOGGER.warning("SchedulerBridge: Failed entity supplier scheduling: " + e.getMessage());
                    future.complete(null);
                    return future;
                }
            }
        }

        runForEntity(entity, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public void cancelPluginTasks() {
        if (!foliaServer) {
            try {
                Bukkit.getScheduler().cancelTasks(plugin);
            } catch (UnsupportedOperationException e) {
                LOGGER.warning("SchedulerBridge: Bukkit scheduler task cancellation is not supported here");
            }
        }

        if (!modernSchedulersAvailable) {
            return;
        }

        try {
            Object asyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
            Method cancelTasks = asyncScheduler.getClass().getMethod("cancelTasks", Plugin.class);
            cancelTasks.invoke(asyncScheduler, plugin);
        } catch (Exception e) {
            LOGGER.warning("SchedulerBridge: Failed to cancel async scheduler tasks: " + e.getMessage());
        }
    }

    private boolean runWithAsyncScheduler(Runnable task) {
        try {
            Object asyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
            Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            runNow.invoke(asyncScheduler, plugin, consumer);
            return true;
        } catch (Exception e) {
            LOGGER.warning("SchedulerBridge: Falling back from async scheduler: " + e.getMessage());
            return false;
        }
    }

    private boolean runWithGlobalScheduler(Runnable task) {
        try {
            Object globalScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method run = globalScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            run.invoke(globalScheduler, plugin, consumer);
            return true;
        } catch (Exception e) {
            LOGGER.warning("SchedulerBridge: Falling back from global scheduler: " + e.getMessage());
            return false;
        }
    }

    private boolean runWithEntityScheduler(Entity entity, Runnable task) {
        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(entity);
            Method run = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Object scheduledTask = run.invoke(entityScheduler, plugin, consumer, null);
            return scheduledTask != null;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            LOGGER.warning("SchedulerBridge: Falling back from entity scheduler: " + e.getMessage());
            return false;
        }
    }

    private boolean hasMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private boolean detectFolia(Object server) {
        String serverName = Bukkit.getName();
        if (serverName != null && serverName.toLowerCase().contains("folia")) {
            return true;
        }
        String version = Bukkit.getVersion();
        if (version != null && version.toLowerCase().contains("folia")) {
            return true;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, server.getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
