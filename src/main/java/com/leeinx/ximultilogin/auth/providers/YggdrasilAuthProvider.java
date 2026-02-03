package com.leeinx.ximultilogin.auth.providers;

import com.leeinx.ximultilogin.auth.AuthProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Yggdrasil 验证提供者
 * 使用 HttpURLConnection 实现 Yggdrasil 协议验证，支持自定义 API URL
 */
public class YggdrasilAuthProvider implements AuthProvider {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static final int CONNECT_TIMEOUT = 3000; // 3秒连接超时
    private static final int READ_TIMEOUT = 5000;    // 5秒读取超时

    private final String name;
    private final String apiUrl;
    private final boolean enabled;

    /**
     * 构造 Yggdrasil 验证提供者
     *
     * @param name     验证提供者名称
     * @param apiUrl   Yggdrasil API URL
     * @param enabled  是否启用
     */
    public YggdrasilAuthProvider(String name, String apiUrl, boolean enabled) {
        this.name = name;
        this.apiUrl = apiUrl;
        this.enabled = enabled;
    }

    @Override
    public GameProfile authenticate(String name, String serverId) {
        if (!enabled) {
            return null;
        }

        try {
            LOGGER.info("YggdrasilAuthProvider (" + this.name + "): Authenticating player " + name + " with serverId " + serverId);

            // 构建请求 URL
            String urlString = apiUrl + "/sessionserver/session/minecraft/hasJoined?username=" + name + "&serverId=" + serverId;
            URL url = new URL(urlString);

            // 创建 HTTP 连接
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "XiMultiLogin/1.0");

            // 获取响应码
            int responseCode = connection.getResponseCode();
            LOGGER.info("YggdrasilAuthProvider (" + this.name + "): Response code: " + responseCode);

            // 读取响应内容
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == HttpURLConnection.HTTP_OK ? connection.getInputStream() : connection.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();

            // 处理响应
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseBody = response.toString();
                LOGGER.info("YggdrasilAuthProvider (" + this.name + "): Response body: " + responseBody);
                return parseGameProfile(responseBody, name);
            } else {
                LOGGER.info("YggdrasilAuthProvider (" + this.name + "): Authentication failed with response code " + responseCode);
                return null;
            }
        } catch (Exception e) {
            // 捕获所有异常，确保不会影响后续验证提供者
            LOGGER.warning("YggdrasilAuthProvider (" + this.name + "): Exception during authentication for " + name + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析 JSON 响应为 GameProfile
     * 使用正则表达式解析，避免引入外部依赖
     *
     * @param json 响应 JSON
     * @param name 玩家名称
     * @return 解析后的 GameProfile
     */
    private GameProfile parseGameProfile(String json, String name) {
        try {
            // 提取 UUID
            String uuidPattern = "\"id\":\"([0-9a-fA-F\\-]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(uuidPattern);
            java.util.regex.Matcher matcher = pattern.matcher(json);
            String uuidString = null;
            if (matcher.find()) {
                uuidString = matcher.group(1);
            }

            if (uuidString == null) {
                LOGGER.warning("YggdrasilAuthProvider (" + this.name + "): Failed to parse UUID from response");
                return null;
            }

            // 标准化 UUID 格式（移除连字符）
            String normalizedUuid = uuidString.replace("-", "");
            UUID uuid = UUID.fromString(
                    normalizedUuid.substring(0, 8) + "-" +
                    normalizedUuid.substring(8, 12) + "-" +
                    normalizedUuid.substring(12, 16) + "-" +
                    normalizedUuid.substring(16, 20) + "-" +
                    normalizedUuid.substring(20)
            );

            // 创建 GameProfile
            GameProfile profile = new GameProfile(uuid, name);

            // 提取 properties（如 skin）
            String propertiesPattern = "\"properties\":\\[([\\s\\S]*?)\\]";
            pattern = java.util.regex.Pattern.compile(propertiesPattern);
            matcher = pattern.matcher(json);
            if (matcher.find()) {
                String propertiesJson = matcher.group(1);
                // 提取每个 property
                String propertyPattern = "\"name\":\"([^\"]+)\",\"value\":\"([^\"]+)\"(,\"signature\":\"([^\"]+)\")?";
                pattern = java.util.regex.Pattern.compile(propertyPattern);
                matcher = pattern.matcher(propertiesJson);
                while (matcher.find()) {
                    String propName = matcher.group(1);
                    String propValue = matcher.group(2);
                    String propSignature = matcher.group(4); // 可能为 null
                    if (propSignature != null) {
                        profile.getProperties().put(propName, new Property(propName, propValue, propSignature));
                    } else {
                        profile.getProperties().put(propName, new Property(propName, propValue));
                    }
                }
            }

            LOGGER.info("YggdrasilAuthProvider (" + this.name + "): Authentication successful for " + name + ", UUID: " + uuid);
            return profile;
        } catch (Exception e) {
            LOGGER.warning("YggdrasilAuthProvider (" + this.name + "): Exception parsing GameProfile: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
