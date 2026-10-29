package com.Leeinx.ximultilogin.config;

import com.Leeinx.ximultilogin.support.BukkitTestSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigManagerTest extends BukkitTestSupport {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDefaultConfigValues() throws IOException {
        File dataFolder = folder.newFolder("pluginData");
        File configFile = new File(dataFolder, "config.yml");

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("pipeline:\n" +
                    "  - type: MOJANG\n" +
                    "    enabled: true\n" +
                    "allow_cracked: false\n" +
                    "debug: false\n");
        }

        ConfigManager configManager = new ConfigManager(null, dataFolder);

        assertFalse(configManager.isAllowCracked());
        assertFalse(configManager.isDebug());
        assertFalse(configManager.isBlockSameIdConflict());
        assertNotNull(configManager.getDatabaseConfig());
        assertEquals("SQLite", configManager.getDatabaseConfig().getType());
        assertNotNull(configManager.getPipelineConfig());
        assertEquals(1, configManager.getPipelineConfig().size());
        assertEquals("MOJANG", configManager.getPipelineConfig().get(0).getType());
    }

    @Test
    public void testBlockSameIdConflictEnabled() throws IOException {
        File dataFolder = folder.newFolder("pluginDataConflict");
        File configFile = new File(dataFolder, "config.yml");

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("security:\n" +
                    "  block_same_id_conflict: true\n" +
                    "allow_cracked: true\n");
        }

        ConfigManager configManager = new ConfigManager(null, dataFolder);

        assertTrue(configManager.isBlockSameIdConflict());
        assertTrue(configManager.isAllowCracked());
    }
}
