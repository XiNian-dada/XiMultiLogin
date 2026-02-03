package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProvider;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Yggdrasil 认证提供者
 * 实现 Yggdrasil 协议认证（支持皮肤站）
 */
public class YggdrasilAuthProvider implements AuthProvider {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final String name;
    private final String apiUrl;
    private final boolean enabled;

    /**
     * 构造 YggdrasilAuthProvider
     * 
     * @param name    提供者名称
     * @param apiUrl  Yggdrasil API 地址
     * @param enabled 是否启用
     */
    public YggdrasilAuthProvider(String name, String apiUrl, boolean enabled) {
        this.name = name;
        this.apiUrl = apiUrl;
        this.enabled = enabled;
    }

    /**
     * 执行 Yggdrasil 认证
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 认证成功返回 GameProfile，认证失败返回 null
     */
    @Override
    public Object authenticate(String username, String serverId) {
        if (!enabled) {
            LOGGER.info(name + "AuthProvider: Provider is disabled");
            return null;
        }

        try {
            LOGGER.info(name + "AuthProvider: Authenticating " + username + " with " + apiUrl);
            
            // 构建请求URL
            URL url = new URL(apiUrl + "/sessionserver/session/minecraft/hasJoined");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // 设置请求参数
            String params = "username=" + username + "&serverId=" + serverId;
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(params.getBytes().length));
            connection.setDoOutput(true);
            
            // 发送请求
            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            // 读取响应
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    
                    // 解析响应
                    return parseResponse(response.toString(), username);
                }
            } else {
                LOGGER.info(name + "AuthProvider: Authentication failed for " + username + ", response code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            LOGGER.warning(name + "AuthProvider: Exception during authentication: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析 Yggdrasil 响应
     * 
     * @param response 响应内容
     * @param username 玩家名称
     * @return 解析成功返回 GameProfile，解析失败返回 null
     */
    private Object parseResponse(String response, String username) {
        try {
            // 简单的 JSON 解析（实际项目中应该使用 JSON 库）
            // 这里使用字符串操作来提取信息
            
            // 提取 UUID
            String uuidStr = extractValue(response, "id");
            if (uuidStr == null) {
                return null;
            }
            UUID uuid = UUID.fromString(uuidStr.replaceAll("[\\s]", ""));
            
            // 提取名称
            String name = extractValue(response, "name");
            if (name == null) {
                name = username;
            } else {
                name = name.replaceAll("[\\s]", "");
            }
            
            // 使用反射创建 GameProfile
            return createGameProfile(uuid, name);
        } catch (Exception e) {
            LOGGER.warning(name + "AuthProvider: Exception parsing response: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射创建 GameProfile 对象
     * 
     * @param uuid  UUID
     * @param name  用户名
     * @return GameProfile 对象
     */
    private Object createGameProfile(UUID uuid, String name) {
        try {
            // 加载 GameProfile 类
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            // 获取构造函数
            java.lang.reflect.Constructor<?> constructor = gameProfileClass.getConstructor(UUID.class, String.class);
            // 创建实例
            return constructor.newInstance(uuid, name);
        } catch (Exception e) {
            LOGGER.warning(name + "AuthProvider: Exception creating GameProfile: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从 JSON 字符串中提取值
     * 
     * @param json  JSON 字符串
     * @param key  键名
     * @return 提取的值
     */
    private String extractValue(String json, String key) {
        String searchKey = key + ": ";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        // 跳过引号
        if (startIndex < json.length() && json.charAt(startIndex) == '"') {
            startIndex++;
        }
        int endIndex = json.indexOf('"', startIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf(',', startIndex);
        }
        if (endIndex == -1) {
            endIndex = json.indexOf('}', startIndex);
        }
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex).trim();
    }

    /**
     * 获取提供者名称
     * 
     * @return 提供者名称
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 检查提供者是否启用
     * 
     * @return 是否启用
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
