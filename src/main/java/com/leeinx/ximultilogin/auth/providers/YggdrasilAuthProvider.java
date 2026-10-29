package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProviderUnavailableException;
import com.Leeinx.ximultilogin.auth.AuthlibAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yggdrasil 协议认证提供者
 * 用于支持第三方皮肤站（如 LittleSkin、Blessing Skin 等）
 */
public class YggdrasilAuthProvider extends BaseAuthProvider {

    private final String apiUrl;
    private final SkinCache skinCache;

    /**
     * 构造 YggdrasilAuthProvider
     * 
     * @param name    提供者名称
     * @param apiUrl  第三方皮肤站 Yggdrasil API 地址
     * @param enabled 是否启用
     */
    public YggdrasilAuthProvider(String name, String apiUrl, boolean enabled) {
        super(name, enabled);
        this.apiUrl = apiUrl;
        this.skinCache = new SkinCache();
    }

    /**
     * 皮肤缓存类
     * 缓存玩家皮肤属性，减少重复获取
     */
    private static class SkinCache {
        private final ConcurrentHashMap<String, SkinData> cache;
        private static final long CACHE_DURATION = 30 * 60 * 1000; // 30分钟缓存

        SkinCache() {
            this.cache = new ConcurrentHashMap<String, SkinData>();
        }

        /**
         * 获取缓存的皮肤数据
         * 
         * @param key 缓存键 (username:provider)
         * @return 皮肤数据，若不存在或已过期返回 null
         */
        public SkinData get(String key) {
            SkinData data = cache.get(key);
            if (data != null && !data.isExpired()) {
                return data;
            }
            cache.remove(key);
            return null;
        }

        /**
         * 缓存皮肤数据
         * 
         * @param key  缓存键 (username:provider)
         * @param data 皮肤数据
         */
        public void put(String key, SkinData data) {
            cache.put(key, data);
        }

        /**
         * 皮肤数据类
         */
        public static class SkinData {
            private final JsonArray properties;
            private final long timestamp;

            public SkinData(JsonArray properties) {
                this.properties = properties;
                this.timestamp = System.currentTimeMillis();
            }

            public JsonArray getProperties() {
                return properties;
            }

            public boolean isExpired() {
                return System.currentTimeMillis() - timestamp > CACHE_DURATION;
            }
        }
    }

    /**
     * 执行 Yggdrasil 认证
     * 
     * @param username 玩家名称
     * @param serverId 服务器唯一标识符
     * @return 认证成功返回包装后的 GameProfile，认证失败返回 null
     */
    @Override
    public Object authenticate(String username, String serverId) {
        if (!enabled) {
            return null;
        }

        try {
            String cacheKey = username + ":" + name;
            SkinCache.SkinData cachedSkinData = skinCache.get(cacheKey);
            if (cachedSkinData != null) {
                info("Using cached skin data for " + username);
                java.util.UUID offlineUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                return AuthlibAdapter.createAuthResult(offlineUuid, username, cachedSkinData.getProperties(), null);
            }

            String cleanApiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            String endpoint = cleanApiUrl + "/sessionserver/session/minecraft/hasJoined";
            String encodedUsername = java.net.URLEncoder.encode(username, StandardCharsets.UTF_8.toString());
            String encodedServerId = java.net.URLEncoder.encode(serverId, StandardCharsets.UTF_8.toString());
            String urlString = endpoint + "?username=" + encodedUsername + "&serverId=" + encodedServerId;
            URL url = new URL(urlString);

            info("Authenticating " + username + " with API Root: " + cleanApiUrl);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    Object profile = parseResponseWithGson(response.toString(), username);

                    try {
                        JsonObject json = new JsonParser().parse(response.toString()).getAsJsonObject();
                        if (json.has("properties")) {
                            JsonArray props = json.getAsJsonArray("properties");
                            skinCache.put(cacheKey, new SkinCache.SkinData(props));
                            info("Cached skin data for " + username);
                        }
                    } catch (Exception e) {
                        warning("Failed to cache skin data: " + e.getMessage());
                    }

                    return profile;
                }
            } else if (responseCode == 204) {
                info("204 No Content (Verify Failed)");
            } else if (responseCode == 429 || responseCode >= 500) {
                throw new AuthProviderUnavailableException(name + " authentication service returned HTTP " + responseCode, null);
            } else {
                info("HTTP " + responseCode);
            }
        } catch (AuthProviderUnavailableException e) {
            warning("Authentication service unavailable: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            warning("Network Error: " + e.getMessage());
            throw new AuthProviderUnavailableException(name + " authentication service unavailable", e);
        } catch (Exception e) {
            warning("Network/Parse Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private Object parseResponseWithGson(String jsonString, String originalName) {
        try {
            JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
            String idStr = json.get("id").getAsString();
            java.util.UUID uuid = parseUUID(idStr);
            String name = json.has("name") ? json.get("name").getAsString() : originalName;

            JsonArray properties = json.has("properties") ? json.getAsJsonArray("properties") : null;
            return AuthlibAdapter.createAuthResult(uuid, name, properties, null);
        } catch (Exception e) {
            warning("JSON Parse Failed: " + e.getMessage());
            LOGGER.warning("JSON Debug: " + jsonString);
            return null;
        }
    }

    private java.util.UUID parseUUID(String id) {
        if (id == null) {
            return null;
        }
        try {
            if (id.contains("-")) {
                return java.util.UUID.fromString(id);
            }
            return java.util.UUID.fromString(id.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return null;
        }
    }
}
