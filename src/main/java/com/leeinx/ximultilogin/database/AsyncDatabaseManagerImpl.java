package com.Leeinx.ximultilogin.database;

import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * 异步数据库管理器实现
 * 包装现有的 DatabaseManager，提供异步操作支持
 */
public class AsyncDatabaseManagerImpl implements AsyncDatabaseManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final DatabaseManager delegate;
    private final ExecutorService executorService;

    /**
     * 构造 AsyncDatabaseManagerImpl
     * 
     * @param delegate 底层的 DatabaseManager 实现
     * @param threadCount 线程池大小
     */
    public AsyncDatabaseManagerImpl(DatabaseManager delegate, int threadCount) {
        this.delegate = delegate;
        // 创建固定大小的线程池
        int finalThreadCount = Math.max(2, threadCount);
        this.executorService = Executors.newFixedThreadPool(finalThreadCount, r -> {
            Thread thread = new Thread(r, "XiMultiLogin-DB-Thread");
            thread.setDaemon(true);
            return thread;
        });
        LOGGER.info("AsyncDatabaseManager: Initialized with " + finalThreadCount + " threads");
    }
    
    /**
     * 构造 AsyncDatabaseManagerImpl（使用默认线程池大小）
     * 
     * @param delegate 底层的 DatabaseManager 实现
     */
    public AsyncDatabaseManagerImpl(DatabaseManager delegate) {
        this(delegate, Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    @Override
    public boolean initialize() {
        return delegate.initialize();
    }

    @Override
    public void close() {
        executorService.shutdown();
        delegate.close();
        LOGGER.info("AsyncDatabaseManager: Closed");
    }

    @Override
    public CompletableFuture<Boolean> storeIdentityAsync(String name, UUID uuid, String authProvider) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.storeIdentity(name, uuid, authProvider);
            } catch (Exception e) {
                LOGGER.severe("AsyncDatabaseManager: Error storing identity: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    @Override
    public boolean storeIdentity(String name, UUID uuid, String authProvider) {
        return delegate.storeIdentity(name, uuid, authProvider);
    }

    @Override
    public CompletableFuture<Boolean> updateAuthProviderAsync(String name, UUID uuid, String authProvider) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.updateAuthProvider(name, uuid, authProvider);
            } catch (Exception e) {
                LOGGER.severe("AsyncDatabaseManager: Error updating auth provider: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    @Override
    public boolean updateAuthProvider(String name, UUID uuid, String authProvider) {
        return delegate.updateAuthProvider(name, uuid, authProvider);
    }

    @Override
    public CompletableFuture<UUID> getUUIDAsync(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.getUUID(name);
            } catch (Exception e) {
                LOGGER.severe("AsyncDatabaseManager: Error getting UUID: " + e.getMessage());
                return null;
            }
        }, executorService);
    }

    @Override
    public UUID getUUID(String name) {
        return delegate.getUUID(name);
    }

    @Override
    public CompletableFuture<String> getAuthProviderAsync(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.getAuthProvider(name);
            } catch (Exception e) {
                LOGGER.severe("AsyncDatabaseManager: Error getting auth provider: " + e.getMessage());
                return null;
            }
        }, executorService);
    }

    @Override
    public String getAuthProvider(String name) {
        return delegate.getAuthProvider(name);
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.exists(name);
            } catch (Exception e) {
                LOGGER.severe("AsyncDatabaseManager: Error checking existence: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    @Override
    public boolean exists(String name) {
        return delegate.exists(name);
    }

    @Override
    public CompletableFuture<Boolean> deleteIdentityAsync(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.deleteIdentity(name);
            } catch (Exception e) {
                LOGGER.severe("AsyncDatabaseManager: Error deleting identity: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    @Override
    public boolean deleteIdentity(String name) {
        return delegate.deleteIdentity(name);
    }
}