package com.Leeinx.ximultilogin.auth.providers;

import com.Leeinx.ximultilogin.auth.AuthProviderUnavailableException;
import com.Leeinx.ximultilogin.auth.AuthlibAdapter;

import java.lang.reflect.InvocationTargetException;

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
        } catch (AuthProviderUnavailableException e) {
            warning("Authentication service unavailable: " + e.getMessage());
            throw e;
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
            Throwable rootCause = unwrapReflectionException(e);
            if (isServiceUnavailable(rootCause)) {
                warning("Mojang session service unavailable: " + describeThrowable(rootCause));
                throw new AuthProviderUnavailableException("Mojang session service unavailable", rootCause);
            }
            warning("Exception calling hasJoinedServer: " + describeThrowable(rootCause));
            e.printStackTrace();
            return null;
        }
    }

    private Throwable unwrapReflectionException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getTargetException() != null) {
            current = ((InvocationTargetException) current).getTargetException();
        }
        return current == null ? throwable : current;
    }

    private boolean isServiceUnavailable(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 8) {
            String className = current.getClass().getName();
            String simpleName = current.getClass().getSimpleName();
            if ("AuthenticationUnavailableException".equals(simpleName)
                    || "com.mojang.authlib.exceptions.AuthenticationUnavailableException".equals(className)) {
                return true;
            }
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.NoRouteToHostException
                    || current instanceof javax.net.ssl.SSLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String describeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }
}
