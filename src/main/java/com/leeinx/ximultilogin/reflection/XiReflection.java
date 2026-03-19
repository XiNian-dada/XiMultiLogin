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
                        boolean isRecord = isRecordClass(wrapperObj.getClass());
                        LOGGER.info("XiReflection: Found container: " + wrapperField.getName() + " (IsRecord: " + isRecord + ")");
                        return new Object[]{ minecraftServer, wrapperField, isRecord };
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 检查一个类是否是 Java Record 类
     * 使用反射方式检测，兼容 Java 8+
     * 
     * @param clazz 要检查的类
     * @return 如果是 Record 类返回 true，否则返回 false
     */
    private static boolean isRecordClass(Class<?> clazz) {
        try {
            // 尝试调用 isRecord() 方法（Class 类的方法）
            Method isRecordMethod = Class.class.getMethod("isRecord");
            return (boolean) isRecordMethod.invoke(clazz);
        } catch (Exception e) {
            // 如果方法不存在，说明不是 Record 类
            return false;
        }
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
            Object adaptedService = adaptSessionServiceValue(newService, sessionType);
            if (adaptedService == null) {
                LOGGER.severe("XiReflection: Failed to adapt replacement session service.");
                return false;
            }

            if (isRecord) {
                LOGGER.info("XiReflection: Reconstructing Record with Proxy...");
                boolean replaced = reconstructAndSwapRecord(serverInstance, wrapperField, oldWrapper, adaptedService, sessionType);
                if (replaced) {
                    LOGGER.info("XiReflection: Session service replacement succeeded via record reconstruction.");
                }
                return replaced;
            } else {
                LOGGER.info("XiReflection: Injecting normal field...");
                Field inner = getFieldByType(oldWrapper.getClass(), sessionType);
                boolean replaced = injectField(oldWrapper, inner, adaptedService);
                if (replaced) {
                    LOGGER.info("XiReflection: Session service replacement succeeded via direct field injection.");
                }
                return replaced;
            }
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean reconstructAndSwapRecord(Object targetHolder, Field holderField, Object oldRecord, Object replacementValue, Class<?> sessionType) {
        try {
            Class<?> recordClass = oldRecord.getClass();
            
            // 使用反射获取 Record 组件
            Object[] components = getRecordComponents(recordClass);
            if (components == null) {
                LOGGER.severe("XiReflection: Failed to get record components");
                return false;
            }
            
            Object[] newArgs = new Object[components.length];
            Class<?>[] paramTypes = new Class<?>[components.length];
            Field sessionField = getFieldByType(recordClass, sessionType);
            String sessionFieldName = sessionField == null ? null : sessionField.getName();
            Object currentSessionValue = null;

            if (sessionField != null) {
                sessionField.setAccessible(true);
                currentSessionValue = sessionField.get(oldRecord);
            }

            boolean replacedAnyComponent = false;

            for (int i = 0; i < components.length; i++) {
                Object rc = components[i];
                
                // 使用反射获取 Record 组件的类型和访问方法
                Class<?> componentType = getRecordComponentType(rc);
                Method accessor = getRecordComponentAccessor(rc);
                String componentName = getRecordComponentName(rc);
                
                paramTypes[i] = componentType;
                Object value = readRecordComponentValue(oldRecord, rc, componentName, accessor);

                if (shouldReplaceRecordComponent(componentType, sessionType, componentName, sessionFieldName, value, currentSessionValue)) {
                    LOGGER.info("XiReflection: Swapping " + componentName + " with adapted session service.");
                    newArgs[i] = replacementValue;
                    replacedAnyComponent = true;
                } else {
                    newArgs[i] = value;
                }
            }

            if (!replacedAnyComponent) {
                LOGGER.severe("XiReflection: Failed to locate record component for session service replacement.");
                return false;
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
     * 使用反射获取 Record 组件
     * 兼容 Java 8+
     * 
     * @param recordClass Record 类
     * @return Record 组件数组
     */
    private static Object[] getRecordComponents(Class<?> recordClass) {
        try {
            // getRecordComponents() 是 Class 类的方法，不是 Record 类的方法
            Method getRecordComponentsMethod = Class.class.getMethod("getRecordComponents");
            return (Object[]) getRecordComponentsMethod.invoke(recordClass);
        } catch (Exception e) {
            LOGGER.warning("XiReflection: Failed to get record components: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射获取 Record 组件的类型
     * 
     * @param recordComponent Record 组件
     * @return 组件类型
     */
    private static Class<?> getRecordComponentType(Object recordComponent) {
        try {
            Method getTypeMethod = recordComponent.getClass().getMethod("getType");
            return (Class<?>) getTypeMethod.invoke(recordComponent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get record component type", e);
        }
    }

    /**
     * 使用反射获取 Record 组件的访问方法
     * 
     * @param recordComponent Record 组件
     * @return 访问方法
     */
    private static Method getRecordComponentAccessor(Object recordComponent) {
        Class<?> componentType = getRecordComponentType(recordComponent);
        try {
            Method getAccessorMethod = recordComponent.getClass().getMethod("getAccessor");
            Method accessor = (Method) getAccessorMethod.invoke(recordComponent);
            if (isMatchingComponentAccessor(accessor, componentType)) {
                return accessor;
            }
        } catch (Exception ignored) {
        }

        try {
            String componentName = getRecordComponentName(recordComponent);
            Class<?> declaringRecord = getDeclaringRecordClass(recordComponent);
            if (declaringRecord != null) {
                try {
                    Method declaredMethod = declaringRecord.getDeclaredMethod(componentName);
                    if (isMatchingComponentAccessor(declaredMethod, componentType)) {
                        declaredMethod.setAccessible(true);
                        return declaredMethod;
                    }
                } catch (NoSuchMethodException ignored) {
                }

                for (Method method : declaringRecord.getDeclaredMethods()) {
                    if (method.getName().equals(componentName)
                            && method.getParameterCount() == 0
                            && isMatchingComponentAccessor(method, componentType)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("XiReflection: Failed to resolve record component accessor: " + e.getMessage());
        }

        return null;
    }

    /**
     * 使用反射获取 Record 组件的名称
     * 
     * @param recordComponent Record 组件
     * @return 组件名称
     */
    private static String getRecordComponentName(Object recordComponent) {
        try {
            Method getNameMethod = recordComponent.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(recordComponent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get record component name", e);
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

    private static Object adaptSessionServiceValue(Object service, Class<?> sessionType) {
        if (service == null) {
            return null;
        }
        if (sessionType.isInstance(service)) {
            return service;
        }
        if (!sessionType.isInterface()) {
            LOGGER.severe("XiReflection: Session service type is not an interface: " + sessionType.getName());
            return null;
        }

        return Proxy.newProxyInstance(
                sessionType.getClassLoader(),
                new Class<?>[]{sessionType},
                (proxyObj, method, args) -> invokeAdaptedSessionMethod(service, method, args)
        );
    }

    private static Object invokeAdaptedSessionMethod(Object targetService, Method method, Object[] args) throws Exception {
        try {
            Method targetMethod = findMethodLoose(targetService.getClass(), method.getName(), args == null ? 0 : args.length);

            if (targetMethod != null) {
                targetMethod.setAccessible(true);
                try {
                    return targetMethod.invoke(targetService, args);
                } catch (IllegalArgumentException e) {
                    LOGGER.info("XiReflection: Parameter type mismatch, trying smart adaptation...");

                    if (method.getName().equals("hasJoinedServer") && args != null && args.length == 3) {
                        try {
                            Object firstArg = args[0];
                            Method getNameMethod = firstArg.getClass().getMethod("getName");
                            String username = (String) getNameMethod.invoke(firstArg);
                            Object[] adaptedArgs = new Object[]{username, args[1], args[2]};
                            Method stringVersionMethod = findMethodLoose(targetService.getClass(), "hasJoinedServer", 3);

                            if (stringVersionMethod != null) {
                                stringVersionMethod.setAccessible(true);
                                return stringVersionMethod.invoke(targetService, adaptedArgs);
                            }
                        } catch (Exception ex) {
                            LOGGER.warning("XiReflection: Smart adaptation failed: " + ex.getMessage());
                        }
                    }

                    throw e;
                }
            }

            LOGGER.severe("XiReflection: Method not found in XiSessionService: " + method.getName());
            throw new NoSuchMethodException(method.getName());
        } catch (Exception e) {
            LOGGER.severe("XiReflection: Proxy invocation failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private static boolean shouldReplaceRecordComponent(Class<?> componentType, Class<?> sessionType, String componentName, String sessionFieldName, Object value, Object currentSessionValue) {
        if (!componentType.getName().equals(sessionType.getName())) {
            return false;
        }
        if (sessionFieldName != null && sessionFieldName.equals(componentName)) {
            return true;
        }
        if (currentSessionValue != null && value == currentSessionValue) {
            return true;
        }
        return sessionFieldName == null && currentSessionValue == null;
    }

    private static boolean isMatchingComponentAccessor(Method accessor, Class<?> componentType) {
        return accessor != null
                && accessor.getParameterCount() == 0
                && accessor.getReturnType().getName().equals(componentType.getName());
    }

    private static Object readRecordComponentValue(Object oldRecord, Object recordComponent, String componentName, Method accessor) throws Exception {
        Class<?> componentType = getRecordComponentType(recordComponent);
        if (accessor != null) {
            accessor.setAccessible(true);
            return accessor.invoke(oldRecord);
        }

        Field field = findFieldByName(oldRecord.getClass(), componentName);
        if (field != null) {
            field.setAccessible(true);
            return field.get(oldRecord);
        }

        Method fallbackAccessor = findZeroArgMethod(oldRecord.getClass(), componentName);
        if (isMatchingComponentAccessor(fallbackAccessor, componentType)) {
            fallbackAccessor.setAccessible(true);
            return fallbackAccessor.invoke(oldRecord);
        }

        throw new NoSuchFieldException("No accessor or field for record component " + componentName + " in " + oldRecord.getClass().getName());
    }

    private static Class<?> getDeclaringRecordClass(Object recordComponent) {
        try {
            Method declaringRecordMethod = recordComponent.getClass().getMethod("getDeclaringRecord");
            return (Class<?>) declaringRecordMethod.invoke(recordComponent);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field findFieldByName(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findZeroArgMethod(Class<?> clazz, String methodName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean injectField(Object target, Field field, Object value) {
        if (field == null) {
            LOGGER.severe("XiReflection: Target field is null during injection.");
            return false;
        }
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
