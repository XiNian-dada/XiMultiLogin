package com.Leeinx.ximultilogin.config;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 消息管理器
 * 负责加载和管理插件的所有消息配置
 */
public class MessageManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final JavaPlugin plugin;
    private File messagesFile;
    private FileConfiguration messages;
    private final Map<String, String> messageCache = new HashMap<>();

    /**
     * 构造 MessageManager
     *
     * @param plugin 插件实例
     */
    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    /**
     * 加载消息配置
     */
    public void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
            LOGGER.info("XiMultiLogin: Created default messages.yml");
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        messageCache.clear(); // 清空缓存
        loadMessagesToCache();
        LOGGER.info("XiMultiLogin: Messages loaded successfully");
    }

    /**
     * 保存消息配置
     */
    public void saveMessages() {
        try {
            messages.save(messagesFile);
            LOGGER.info("XiMultiLogin: Messages saved successfully");
        } catch (IOException e) {
            LOGGER.severe("XiMultiLogin: Failed to save messages: " + e.getMessage());
        }
    }

    /**
     * 加载消息到缓存
     */
    private void loadMessagesToCache() {
        loadSectionToCache("", messages);
    }

    /**
     * 递归加载配置节到缓存
     *
     * @param prefix 前缀
     * @param section 配置节
     */
    private void loadSectionToCache(String prefix, ConfigurationSection section) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);

            if (value instanceof ConfigurationSection) {
                loadSectionToCache(fullKey, (ConfigurationSection) value);
            } else if (value instanceof String) {
                messageCache.put(fullKey, (String) value);
            }
        }
    }

    /**
     * 获取消息
     *
     * @param key 消息键
     * @param replacements 变量替换
     * @return 处理后的消息
     */
    public String getMessage(String key, Object... replacements) {
        // 从缓存获取消息
        String message = messageCache.get(key);
        if (message == null) {
            // 缓存未命中，从配置中获取
            message = messages.getString(key);
            if (message == null) {
                // 配置中也没有，返回默认消息
                message = "&cMessage not found: " + key;
                LOGGER.warning("XiMultiLogin: Message not found: " + key);
            } else {
                // 存入缓存
                messageCache.put(key, message);
            }
        }

        // 处理变量替换
        if (replacements != null && replacements.length > 0) {
            message = replaceVariables(message, replacements);
        }

        // 处理颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * 替换消息中的变量
     *
     * @param message 原始消息
     * @param replacements 变量替换，格式为 {key, value, key, value, ...}
     * @return 替换后的消息
     */
    private String replaceVariables(String message, Object... replacements) {
        if (replacements.length % 2 != 0) {
            LOGGER.warning("XiMultiLogin: Invalid number of replacements");
            return message;
        }

        String result = message;
        for (int i = 0; i < replacements.length; i += 2) {
            String key = replacements[i].toString();
            String value = replacements[i + 1].toString();
            result = result.replace("{" + key + "}", value);
        }
        return result;
    }

    /**
     * 获取消息配置
     *
     * @return 消息配置
     */
    public FileConfiguration getMessages() {
        return messages;
    }

    /**
     * 重新加载消息配置
     */
    public void reloadMessages() {
        loadMessages();
        LOGGER.info("XiMultiLogin: Messages reloaded");
    }
}
