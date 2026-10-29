package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthlibAdapter;

public class MojangAuthProvider extends BaseAuthProvider {

    private final Object sessionService;

    public MojangAuthProvider(Object sessionService, boolean enabled) {
        super("MOJANG", enabled);
        this.sessionService = sessionService;
    }

    @Override
    public Object authenticate(String username, String serverId) {
        if (!enabled) {
            info("Provider is disabled");
            return null;
        }

        try {
            info("Authenticating " + username + " with Mojang");
            Object profile = callHasJoinedServer(sessionService, username, serverId, null);
            if (profile != null) {
                info("Authentication successful for " + username);
            } else {
                info("Authentication failed for " + username);
            }
            return profile;
        } catch (Exception e) {
            warning("Exception during authentication: " + e.getMessage());
            return null;
        }
    }

    private Object callHasJoinedServer(Object sessionService, String username, String serverId, String ipAddress) {
        try {
            info("SessionService class: " + sessionService.getClass().getName());
            info("SessionService class loader: " + sessionService.getClass().getClassLoader());

            info("Available methods:");
            for (java.lang.reflect.Method method : sessionService.getClass().getMethods()) {
                if (method.getName().equals("hasJoinedServer")) {
                    StringBuilder params = new StringBuilder();
                    for (Class<?> paramType : method.getParameterTypes()) {
                        params.append(paramType.getName()).append(", ");
                    }
                    if (params.length() > 0) {
                        params.setLength(params.length() - 2);
                    }
                    info("  - " + method.getName() + "(" + params + ")");
                }
            }

            try {
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", gameProfileClass, String.class, java.net.InetAddress.class);
                info("Using method signature: (GameProfile, String, InetAddress)");

                Object gameProfile = AuthlibAdapter.createGameProfile(null, username);
                if (gameProfile == null) {
                    warning("Failed to create GameProfile for " + username);
                    return null;
                }
                info("Created temporary GameProfile for " + username);

                java.net.InetAddress inetAddress = null;
                if (ipAddress != null) {
                    inetAddress = java.net.InetAddress.getByName(ipAddress);
                }

                return method.invoke(sessionService, gameProfile, serverId, inetAddress);
            } catch (NoSuchMethodException e1) {
                try {
                    java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class, String.class);
                    info("Using method signature: (String, String, String)");
                    return method.invoke(sessionService, username, serverId, ipAddress);
                } catch (NoSuchMethodException e2) {
                    try {
                        java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class, java.net.InetAddress.class);
                        info("Using method signature: (String, String, InetAddress)");
                        java.net.InetAddress inetAddress = null;
                        if (ipAddress != null) {
                            inetAddress = java.net.InetAddress.getByName(ipAddress);
                        }
                        return method.invoke(sessionService, username, serverId, inetAddress);
                    } catch (NoSuchMethodException e3) {
                        try {
                            java.lang.reflect.Method method = sessionService.getClass().getMethod("hasJoinedServer", String.class, String.class);
                            info("Using method signature: (String, String)");
                            return method.invoke(sessionService, username, serverId);
                        } catch (NoSuchMethodException e4) {
                            warning("No matching hasJoinedServer method found");
                            return null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            warning("Exception calling hasJoinedServer: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
