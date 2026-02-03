package com.Leeinx.ximultilogin.guard;

import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.database.SQLiteDatabaseManager;
import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * IdentityGuard 身份守护者测试
 */
public class IdentityGuardTest {

    /**
     * 测试 IdentityGuard 基本功能
     */
    @Test
    public void testIdentityGuardBasic() {
        // 创建临时文件用于测试
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "ximultilogin-test-identity");
        tempDir.mkdirs();

        try {
            // 创建临时配置文件
            File configFile = new File(tempDir, "config.yml");
            
            // 创建简单的配置管理器（使用反射绕过插件依赖）
            ConfigManager configManager = createTestConfigManager(configFile);
            
            // 创建 IdentityGuard
            IdentityGuard identityGuard = new IdentityGuard(configManager);
            
            // 测试初始状态
            assertNull("Name should not be locked initially", identityGuard.getAuthProvider("testuser"));

            // 测试验证新身份
            UUID uuid1 = UUID.randomUUID();
            boolean firstVerify = identityGuard.verifyIdentity("testuser", uuid1, "MOJANG");
            assertTrue("First verification should succeed", firstVerify);
            assertNotNull("Name should be locked after first verification", identityGuard.getAuthProvider("testuser"));

            // 测试验证相同身份
            boolean secondVerify = identityGuard.verifyIdentity("testuser", uuid1, "MOJANG");
            assertTrue("Second verification with same UUID should succeed", secondVerify);

            // 测试验证不同身份
            UUID uuid2 = UUID.randomUUID();
            boolean thirdVerify = identityGuard.verifyIdentity("testuser", uuid2, "MOJANG");
            assertTrue("Verification with different UUID should succeed (auth provider updated)", thirdVerify);

        } finally {
            // 清理临时文件
            deleteDirectory(tempDir);
        }
    }

    /**
     * 测试 IdentityGuard 空值处理
     */
    @Test
    public void testIdentityGuardNullValues() {
        // 创建临时文件用于测试
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "ximultilogin-test-null");
        tempDir.mkdirs();

        try {
            // 创建临时配置文件
            File configFile = new File(tempDir, "config.yml");
            
            // 创建简单的配置管理器
            ConfigManager configManager = createTestConfigManager(configFile);
            
            // 创建 IdentityGuard
            IdentityGuard identityGuard = new IdentityGuard(configManager);

            // 测试空名称
            boolean nullNameVerify = identityGuard.verifyIdentity(null, UUID.randomUUID(), "MOJANG");
            assertFalse("Verification with null name should fail", nullNameVerify);

            // 测试空 UUID
            boolean nullUuidVerify = identityGuard.verifyIdentity("testuser", null, "MOJANG");
            assertFalse("Verification with null UUID should fail", nullUuidVerify);

            // 测试两者都空
            boolean nullBothVerify = identityGuard.verifyIdentity(null, null, "MOJANG");
            assertFalse("Verification with both null should fail", nullBothVerify);
            
            // 测试空认证提供者
            boolean nullProviderVerify = identityGuard.verifyIdentity("testuser", UUID.randomUUID(), null);
            assertFalse("Verification with null provider should fail", nullProviderVerify);

        } finally {
            // 清理临时文件
            deleteDirectory(tempDir);
        }
    }

    /**
     * 创建测试用的配置管理器
     */
    private ConfigManager createTestConfigManager(File configFile) {
        try {
            // 使用反射创建 ConfigManager 实例
            Class<?> configManagerClass = Class.forName("com.Leeinx.ximultilogin.config.ConfigManager");
            
            // 创建一个模拟的 JavaPlugin 实例
            Class<?> mockPluginClass = Class.forName("org.bukkit.plugin.java.JavaPlugin");
            java.lang.reflect.Constructor<?> constructor = mockPluginClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object mockPlugin = constructor.newInstance();
            
            // 设置数据文件夹
            java.lang.reflect.Field dataFolderField = mockPluginClass.getDeclaredField("dataFolder");
            dataFolderField.setAccessible(true);
            dataFolderField.set(mockPlugin, configFile.getParentFile());
            
            // 创建 ConfigManager 实例
            java.lang.reflect.Constructor<?> configConstructor = configManagerClass.getConstructor(mockPluginClass);
            return (ConfigManager) configConstructor.newInstance(mockPlugin);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test ConfigManager", e);
        }
    }

    /**
     * 删除目录
     *
     * @param directory 要删除的目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
