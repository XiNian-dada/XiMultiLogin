package com.Leeinx.ximultilogin.reflection;

import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * XiReflection (Proxy Adapter Edition)
 * 1. 解决 Record 不可变问题 (Record Reconstruction)
 * 2. 解决 ClassLoader 隔离导致的类型不匹配问题 (Dynamic Proxy)
 */
public class XiReflection {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static Object unsafeInstance;

    public static void init() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafeInstance = theUnsafe.get(null);
            LOGGER.info("XiReflection: Unsafe initialized.");
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Failed to get Unsafe.");
        }
    }

    public static Object getMinecraftServer() {
        try {
            Object server = Bukkit.getServer();
            Method getServerMethod = server.getClass().getMethod("getServer");
            return getServerMethod.invoke(server);
        } catch (Exception e) {
            return null;
        }
    }

    public static Field getFieldByType(Class<?> clazz, Class<?> fieldType) {
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (isFieldMatch(field, fieldType)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static boolean isFieldMatch(Field field, Class<?> targetType) {
        if (targetType.isAssignableFrom(field.getType())) return true;
        return field.getType().getName().equals(targetType.getName());
    }

    public static Object[] findSessionServiceHolder(Object minecraftServer, Class<?> sessionType) {
        Class<?> clazz = minecraftServer.getClass();
        
        // 1. Direct
        Field directField = getFieldByType(clazz, sessionType);
        if (directField != null) return new Object[]{ minecraftServer, directField, false };

        // 2. Wrapper Scan
        LOGGER.info("XiReflection: Scanning wrappers...");
        while (clazz != null && clazz != Object.class) {
            for (Field wrapperField : clazz.getDeclaredFields()) {
                if (shouldSkip(wrapperField)) continue;
                try {
                    wrapperField.setAccessible(true);
                    Object wrapperObj = wrapperField.get(minecraftServer);
                    if (wrapperObj == null) continue;

                    Field innerField = getFieldByType(wrapperObj.getClass(), sessionType);
                    if (innerField != null) {
                        LOGGER.info("XiReflection: Found container: " + wrapperField.getName() + " (IsRecord: " + wrapperObj.getClass().isRecord() + ")");
                        return new Object[]{ minecraftServer, wrapperField, wrapperObj.getClass().isRecord() };
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static boolean shouldSkip(Field f) {
        return f.getType().isPrimitive() || Modifier.isStatic(f.getModifiers()) || f.getType().equals(String.class);
    }

    public static Object getSessionService(Object minecraftServer) {
        try {
            Class<?> sessionType = Class.forName("com.mojang.authlib.minecraft.MinecraftSessionService");
            Object[] info = findSessionServiceHolder(minecraftServer, sessionType);
            if (info != null) {
                Field wrapperField = (Field) info[1];
                Object wrapper = wrapperField.get(info[0]);
                Field inner = getFieldByType(wrapper.getClass(), sessionType);
                return inner.get(wrapper);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static boolean setSessionService(Object minecraftServer, Object newService) {
        try {
            Class<?> sessionType = Class.forName("com.mojang.authlib.minecraft.MinecraftSessionService");
            Object[] info = findSessionServiceHolder(minecraftServer, sessionType);
            
            if (info == null) {
                LOGGER.severe("XiReflection: Holder not found.");
                return false;
            }

            Object serverInstance = info[0];
            Field wrapperField = (Field) info[1];
            boolean isRecord = (boolean) info[2];

            wrapperField.setAccessible(true);
            Object oldWrapper = wrapperField.get(serverInstance);

            if (isRecord) {
                LOGGER.info("XiReflection: Reconstructing Record with Proxy...");
                return reconstructAndSwapRecord(serverInstance, wrapperField, oldWrapper, newService, sessionType);
            } else {
                LOGGER.info("XiReflection: Injecting normal field...");
                Field inner = getFieldByType(oldWrapper.getClass(), sessionType);
                return injectField(oldWrapper, inner, newService);
            }
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean reconstructAndSwapRecord(Object targetHolder, Field holderField, Object oldRecord, Object newSessionService, Class<?> sessionType) {
        try {
            Class<?> recordClass = oldRecord.getClass();
            RecordComponent[] components = recordClass.getRecordComponents();
            Object[] newArgs = new Object[components.length];
            Class<?>[] paramTypes = new Class<?>[components.length];

            for (int i = 0; i < components.length; i++) {
                RecordComponent rc = components[i];
                paramTypes[i] = rc.getType();
                
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(oldRecord);

                // 判断是否是 SessionService 字段 (通过类名判断，忽略 ClassLoader 差异)
                if (rc.getType().getName().equals(sessionType.getName())) {
                    LOGGER.info("XiReflection: Swapping " + rc.getName() + " with Loose Proxy.");
                    
                    // ★★★ 核心修复：智能参数适配代理 (Smart Parameter Adapter Proxy) ★★★
                    // 使用 Record 组件期待的 ClassLoader 和 接口类型
                    Object proxy = Proxy.newProxyInstance(
                        rc.getType().getClassLoader(),
                        new Class<?>[]{ rc.getType() }, // 强制使用 Record 要求的接口
                        (proxyObj, method, args) -> {
                            try {
                                // 尝试直接调用，使用松散匹配
                                Method targetMethod = findMethodLoose(newSessionService.getClass(), method.getName(), args == null ? 0 : args.length);
                                
                                if (targetMethod != null) {
                                    targetMethod.setAccessible(true);
                                    try {
                                        // 尝试直接调用
                                        return targetMethod.invoke(newSessionService, args);
                                    } catch (IllegalArgumentException e) {
                                        // 参数类型不匹配，尝试智能适配
                                        LOGGER.info("XiReflection: Parameter type mismatch, trying smart adaptation...");
                                        
                                        // 检查是否是 hasJoinedServer 方法，且第一个参数是 GameProfile
                                        if (method.getName().equals("hasJoinedServer") && args != null && args.length == 3) {
                                            // 尝试从第一个参数(GameProfile)中提取用户名
                                            try {
                                                Object firstArg = args[0];
                                                // 检查是否有 getName 方法
                                                Method getNameMethod = firstArg.getClass().getMethod("getName");
                                                String username = (String) getNameMethod.invoke(firstArg);
                                                
                                                // 创建新的参数数组，使用用户名代替 GameProfile
                                                Object[] adaptedArgs = new Object[]{username, args[1], args[2]};
                                                
                                                // 查找并调用接受 String 的版本
                                                Method stringVersionMethod = findMethodLoose(
                                                    newSessionService.getClass(), 
                                                    "hasJoinedServer", 
                                                    3
                                                );
                                                
                                                if (stringVersionMethod != null) {
                                                    stringVersionMethod.setAccessible(true);
                                                    return stringVersionMethod.invoke(newSessionService, adaptedArgs);
                                                }
                                            } catch (Exception ex) {
                                                LOGGER.warning("XiReflection: Smart adaptation failed: " + ex.getMessage());
                                            }
                                        }
                                        
                                        // 如果智能适配失败，重新抛出原异常
                                        throw e;
                                    }
                                } else {
                                    LOGGER.severe("XiReflection: Method not found in XiSessionService: " + method.getName());
                                    throw new NoSuchMethodException(method.getName());
                                }
                            } catch (Exception e) {
                                LOGGER.severe("XiReflection: Proxy invocation failed: " + e.getMessage());
                                e.printStackTrace();
                                throw e;
                            }
                        }
                    );
                    
                    newArgs[i] = proxy; // 塞入代理对象
                } else {
                    newArgs[i] = value;
                }
            }

            Constructor<?> constructor = recordClass.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            Object newRecord = constructor.newInstance(newArgs);

            return injectField(targetHolder, holderField, newRecord);

        } catch (Exception e) {
            LOGGER.severe("XiReflection: Reconstruction failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ★ 新增辅助方法：松散查找方法
     * 忽略参数的具体 Class 类型，只匹配方法名和参数个数
     * 这能完美绕过类加载器不一致的问题
     */
    private static Method findMethodLoose(Class<?> clazz, String methodName, int paramCount) {
        // 遍历所有 public 方法
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        // 如果找不到 public 的，尝试找 declared (private/protected)
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean injectField(Object target, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (IllegalAccessException e) {
            if (unsafeInstance != null) {
                try {
                    Method offsetM = unsafeInstance.getClass().getMethod("objectFieldOffset", Field.class);
                    Method putM = unsafeInstance.getClass().getMethod("putObject", Object.class, long.class, Object.class);
                    long offset = (long) offsetM.invoke(unsafeInstance, field);
                    putM.invoke(unsafeInstance, target, offset, value);
                    LOGGER.info("XiReflection: Unsafe injection success.");
                    return true;
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }
        return false;
    }
}