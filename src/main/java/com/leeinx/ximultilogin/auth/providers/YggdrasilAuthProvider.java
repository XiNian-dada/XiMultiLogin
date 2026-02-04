package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class YggdrasilAuthProvider extends BaseAuthProvider {

    private final String apiUrl;
    private final SkinCache skinCache;

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

        public SkinCache() {
            this.cache = new ConcurrentHashMap<>();
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
         * @param key 缓存键 (username:provider)
         * @param data 皮肤数据
         */
        public void put(String key, SkinData data) {
            cache.put(key, data);
        }

        /**
         * 清除缓存
         */
        public void clear() {
            cache.clear();
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

    @Override
    public Object authenticate(String username, String serverId) {
        if (!enabled) return null;

        try {
            // 生成缓存键
            String cacheKey = username + ":" + name;
            
            // 检查缓存
            SkinCache.SkinData cachedSkinData = skinCache.get(cacheKey);
            if (cachedSkinData != null) {
                info("Using cached skin data for " + username);
                // 使用缓存的数据创建临时 GameProfile
                UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                Object profile = createGameProfile(offlineUUID, username);
                if (profile != null) {
                    addPropertiesToProfile(profile, cachedSkinData.getProperties());
                }
                return profile;
            }

            // URL 处理
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
                    while ((inputLine = in.readLine()) != null) response.append(inputLine);

                    // ★★★ 核心修复：使用 Gson 解析 JSON，不再手写字符串截取 ★★★
                    Object profile = parseResponseWithGson(response.toString(), username);
                    
                    // 缓存皮肤数据
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
                // 204 代表验证未通过（账号密码错或未购买）
                info("204 No Content (Verify Failed)");
            } else {
                info("HTTP " + responseCode);
            }
        } catch (Exception e) {
            warning("Network/Parse Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private Object parseResponseWithGson(String jsonString, String originalName) {
        try {
            // 使用 Spigot 自带的 Gson 解析器
            JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
            
            // 1. 提取 UUID (兼容带横线和不带横线)
            String idStr = json.get("id").getAsString();
            UUID uuid = parseUUID(idStr);
            
            // 2. 提取名称
            String name = json.has("name") ? json.get("name").getAsString() : originalName;
            
            // 3. 反射创建 GameProfile
            Object profile = createGameProfile(uuid, name);
            if (profile == null) return null;

            // 4. 提取皮肤属性 (Properties)
            if (json.has("properties")) {
                JsonArray props = json.getAsJsonArray("properties");
                addPropertiesToProfile(profile, props);
            }
            
            return profile;

        } catch (Exception e) {
            warning("JSON Parse Failed: " + e.getMessage());
            LOGGER.warning("JSON Debug: " + jsonString); // 打印出来方便调试
            return null;
        }
    }

    private UUID parseUUID(String id) {
        if (id == null) return null;
        try {
            if (id.contains("-")) return UUID.fromString(id);
            // 补全 UUID 连字符
            return UUID.fromString(id.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return null;
        }
    }

    // 反射创建 GameProfile (保持不变)
    private Object createGameProfile(UUID uuid, String name) {
        try {
            Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
            return gpClass.getConstructor(UUID.class, String.class).newInstance(uuid, name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 反射填充 Properties (保持不变)
    private void addPropertiesToProfile(Object profile, JsonArray jsonProps) {
        try {
            Class<?> gpClass = profile.getClass();
            Object propertyMap = gpClass.getMethod("getProperties").invoke(profile);
            java.lang.reflect.Method putMethod = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            
            Class<?> propClass = Class.forName("com.mojang.authlib.properties.Property");
            java.lang.reflect.Constructor<?> propCons = propClass.getConstructor(String.class, String.class, String.class);
            java.lang.reflect.Constructor<?> propCons2 = propClass.getConstructor(String.class, String.class);

            for (int i = 0; i < jsonProps.size(); i++) {
                JsonObject p = jsonProps.get(i).getAsJsonObject();
                String pName = p.get("name").getAsString();
                String pValue = p.get("value").getAsString();
                
                Object propertyObj;
                if (p.has("signature")) {
                    propertyObj = propCons.newInstance(pName, pValue, p.get("signature").getAsString());
                } else {
                    propertyObj = propCons2.newInstance(pName, pValue);
                }
                putMethod.invoke(propertyMap, pName, propertyObj);
            }
        } catch (Exception e) {
            warning("Failed to add properties: " + e.getMessage());
        }
    }
}
