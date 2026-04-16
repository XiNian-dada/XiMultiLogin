package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthlibAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class YggdrasilAuthProvider extends BaseAuthProvider {

    private final String apiUrl;
    private final SkinCache skinCache;

    public YggdrasilAuthProvider(String name, String apiUrl, boolean enabled) {
        super(name, enabled);
        this.apiUrl = apiUrl;
        this.skinCache = new SkinCache();
    }

    private static class SkinCache {
        private final ConcurrentHashMap<String, SkinData> cache;
        private static final long CACHE_DURATION = 30 * 60 * 1000;

        SkinCache() {
            this.cache = new ConcurrentHashMap<String, SkinData>();
        }

        public SkinData get(String key) {
            SkinData data = cache.get(key);
            if (data != null && !data.isExpired()) {
                return data;
            }
            cache.remove(key);
            return null;
        }

        public void put(String key, SkinData data) {
            cache.put(key, data);
        }

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
