package com.leeinx.ximultilogin.guard;

import com.leeinx.ximultilogin.database.SQLiteDatabaseManager;
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
            // 创建 IdentityGuard
            IdentityGuard identityGuard = new IdentityGuard(tempDir);
            
            // 测试初始状态
            assertEquals("Initial identity count should be 0", 0, identityGuard.getIdentityCount());
            assertFalse("Name should not be locked initially", identityGuard.isLocked("testuser"));

            // 测试验证新身份
            UUID uuid1 = UUID.randomUUID();
            boolean firstVerify = identityGuard.verifyIdentity("testuser", uuid1, "MOJANG");
            assertTrue("First verification should succeed", firstVerify);
            assertTrue("Name should be locked after first verification", identityGuard.isLocked("testuser"));

            // 测试验证相同身份
            boolean secondVerify = identityGuard.verifyIdentity("testuser", uuid1, "MOJANG");
            assertTrue("Second verification with same UUID should succeed", secondVerify);

            // 测试验证不同身份
            UUID uuid2 = UUID.randomUUID();
            boolean thirdVerify = identityGuard.verifyIdentity("testuser", uuid2, "MOJANG");
            assertFalse("Verification with different UUID should fail", thirdVerify);

            // 测试身份计数
            assertEquals("Identity count should be 1", 1, identityGuard.getIdentityCount());

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
            // 创建 IdentityGuard
            IdentityGuard identityGuard = new IdentityGuard(tempDir);

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
