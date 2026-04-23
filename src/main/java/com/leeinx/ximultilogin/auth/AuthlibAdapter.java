package com.Leeinx.ximultilogin.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class AuthlibAdapter {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static final String GAME_PROFILE_CLASS = "com.mojang.authlib.GameProfile";
    private static final String PROFILE_RESULT_CLASS = "com.mojang.authlib.yggdrasil.ProfileResult";
    private static final String PROPERTY_CLASS = "com.mojang.authlib.properties.Property";
    private static final String PROPERTY_MAP_CLASS = "com.mojang.authlib.properties.PropertyMap";
    private static final String MULTIMAP_CLASS = "com.google.common.collect.Multimap";
    private static final Object MISSING = new Object();

    private AuthlibAdapter() {
    }

    public static String extractName(Object authResult) {
        if (authResult instanceof CharSequence) {
            return authResult.toString();
        }

        String directName = readString(authResult, "getName", "name");
        if (directName != null) {
            return directName;
        }

        Object profile = extractGameProfile(authResult);
        if (profile != null && profile != authResult) {
            return readString(profile, "getName", "name");
        }
        return null;
    }

    public static UUID extractUuid(Object authResult) {
        UUID directUuid = readUuid(authResult, "getId", "id");
        if (directUuid != null) {
            return directUuid;
        }

        Object profile = extractGameProfile(authResult);
        if (profile != null && profile != authResult) {
            return readUuid(profile, "getId", "id");
        }
        return null;
    }

    public static Object createAuthResult(UUID uuid, String name) {
        return createAuthResult(uuid, name, null, null);
    }

    public static Object createAuthResult(UUID uuid, String name, JsonArray properties, Object template) {
        Object gameProfile = createGameProfile(uuid, name, createProperties(properties));
        if (gameProfile == null) {
            return null;
        }

        Object wrapped = wrapGameProfile(gameProfile, template);
        return wrapped != null ? wrapped : gameProfile;
    }

    public static Object replaceIdentity(Object authResult, UUID uuid, String name) {
        Object gameProfile = createGameProfile(uuid, name, collectProperties(authResult));
        if (gameProfile == null) {
            return authResult;
        }
        Object wrapped = wrapGameProfile(gameProfile, authResult);
        return wrapped != null ? wrapped : gameProfile;
    }

    public static Object adaptReturnValue(Object value, Class<?> expectedType) {
        if (value == null || expectedType == null || expectedType == Void.TYPE) {
            return value;
        }
        if (expectedType.isInstance(value)) {
            return value;
        }

        if (matchesClassName(expectedType, GAME_PROFILE_CLASS)) {
            return extractGameProfile(value);
        }

        if (matchesClassName(expectedType, PROFILE_RESULT_CLASS)) {
            Object profile = extractGameProfile(value);
            if (profile == null) {
                return null;
            }

            Object template = isGameProfileObject(value) ? null : value;
            Object wrapped = wrapGameProfile(profile, template);
            if (wrapped != null && expectedType.isInstance(wrapped)) {
                return wrapped;
            }
            return null;
        }

        if (matchesClassName(expectedType, PROPERTY_CLASS)) {
            if (matchesClassName(value.getClass(), PROPERTY_CLASS)) {
                return value;
            }
            return findProperty(value, "textures");
        }

        if (Optional.class.isAssignableFrom(expectedType)) {
            if (value instanceof Optional) {
                return value;
            }

            Object profile = extractGameProfile(value);
            if (profile == null) {
                return Optional.empty();
            }

            Object wrapped = wrapGameProfile(profile, value);
            return Optional.of(wrapped != null ? wrapped : profile);
        }

        if (java.util.concurrent.CompletableFuture.class.isAssignableFrom(expectedType)) {
            if (value instanceof java.util.concurrent.CompletableFuture) {
                return value;
            }
            return java.util.concurrent.CompletableFuture.completedFuture(value);
        }

        return value;
    }

    public static Object extractGameProfile(Object authResult) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        return extractGameProfile(authResult, visited);
    }

    public static void copyProperties(Object source, Object target) {
        Object sourceProfile = extractGameProfile(source);
        Object targetProfile = extractGameProfile(target);
        if (sourceProfile == null || targetProfile == null) {
            return;
        }
        if (sourceProfile == targetProfile) {
            return;
        }

        Object sourceProperties = getPropertyContainer(sourceProfile);
        Object targetProperties = getPropertyContainer(targetProfile);
        if (sourceProperties == null || targetProperties == null) {
            return;
        }

        List<Object> copiedProperties = new ArrayList<Object>();
        for (Object property : getPropertyValues(sourceProperties)) {
            if (property != null) {
                copiedProperties.add(property);
            }
        }
        if (copiedProperties.isEmpty()) {
            return;
        }

        clearPropertyContainer(targetProperties);
        for (Object property : copiedProperties) {
            String propertyName = readString(property, "getName", "name");
            if (propertyName == null) {
                continue;
            }
            putProperty(targetProperties, propertyName, property);
        }
    }

    public static boolean hasProperty(Object authResult, String propertyName) {
        return findProperty(authResult, propertyName) != null;
    }

    public static Object findProperty(Object authResult, String propertyName) {
        if (authResult == null || propertyName == null) {
            return null;
        }

        Object profile = extractGameProfile(authResult);
        if (profile == null) {
            return null;
        }

        Object properties = getPropertyContainer(profile);
        if (properties == null) {
            return null;
        }

        for (Object property : getPropertyValues(properties)) {
            String currentName = readString(property, "getName", "name");
            if (propertyName.equals(currentName)) {
                return property;
            }
        }
        return null;
    }

    public static void applyJsonProperties(Object target, JsonArray jsonProperties) {
        Object targetProfile = extractGameProfile(target);
        if (targetProfile == null || jsonProperties == null) {
            return;
        }

        Object targetProperties = getPropertyContainer(targetProfile);
        if (targetProperties == null) {
            return;
        }

        for (int i = 0; i < jsonProperties.size(); i++) {
            JsonObject propertyJson = jsonProperties.get(i).getAsJsonObject();
            String propertyName = propertyJson.get("name").getAsString();
            String propertyValue = propertyJson.get("value").getAsString();
            String propertySignature = propertyJson.has("signature") ? propertyJson.get("signature").getAsString() : null;
            Object property = createProperty(propertyName, propertyValue, propertySignature);
            if (property != null) {
                putProperty(targetProperties, propertyName, property);
            }
        }
    }

    private static void applyPropertyObjects(Object target, Collection<Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        Object targetProfile = extractGameProfile(target);
        if (targetProfile == null) {
            return;
        }

        Object targetProperties = getPropertyContainer(targetProfile);
        if (targetProperties == null) {
            return;
        }

        for (Object property : properties) {
            String propertyName = readString(property, "getName", "name");
            if (propertyName != null) {
                putProperty(targetProperties, propertyName, property);
            }
        }
    }

    public static Object createGameProfile(UUID uuid, String name) {
        return createGameProfile(uuid, name, Collections.emptyList());
    }

    private static Object createGameProfile(UUID uuid, String name, Collection<Object> properties) {
        try {
            Class<?> gameProfileClass = Class.forName(GAME_PROFILE_CLASS);
            Object propertyMap = createPropertyMap(properties);

            if (propertyMap != null) {
                try {
                    Constructor<?> constructor = gameProfileClass.getConstructor(UUID.class, String.class, propertyMap.getClass());
                    return constructor.newInstance(uuid, name, propertyMap);
                } catch (NoSuchMethodException ignored) {
                }
            }

            try {
                Constructor<?> constructor = gameProfileClass.getConstructor(UUID.class, String.class);
                Object profile = constructor.newInstance(uuid, name);
                applyPropertyObjects(profile, properties);
                return profile;
            } catch (NoSuchMethodException ignored) {
            }

            for (Constructor<?> constructor : gameProfileClass.getDeclaredConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[0] == UUID.class
                        && parameterTypes[1] == String.class) {
                    constructor.setAccessible(true);
                    Object profile = constructor.newInstance(uuid, name);
                    applyPropertyObjects(profile, properties);
                    return profile;
                }

                if (parameterTypes.length == 3
                        && parameterTypes[0] == UUID.class
                        && parameterTypes[1] == String.class
                        && propertyMap != null
                        && parameterTypes[2].getName().equals(propertyMap.getClass().getName())) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(uuid, name, propertyMap);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("AuthlibAdapter: Failed to create GameProfile: " + e.getMessage());
        }
        return null;
    }

    private static Object wrapGameProfile(Object gameProfile, Object template) {
        if (gameProfile == null) {
            return null;
        }

        if (template != null) {
            if (isGameProfileObject(template)) {
                return gameProfile;
            }

            Object rebuilt = rebuildWithTemplate(template, gameProfile);
            if (rebuilt != null) {
                return rebuilt;
            }
        }

        Object profileResult = createProfileResult(gameProfile);
        if (profileResult != null) {
            return profileResult;
        }

        return template == null || isGameProfileObject(template) ? gameProfile : null;
    }

    private static Object rebuildWithTemplate(Object template, Object replacementProfile) {
        Object rebuilt = rebuildRecord(template, replacementProfile);
        if (rebuilt != null) {
            return rebuilt;
        }
        return rebuildUsingConstructors(template, replacementProfile);
    }

    private static Object rebuildRecord(Object template, Object replacementProfile) {
        try {
            Class<?> templateClass = template.getClass();
            Method getRecordComponents = Class.class.getMethod("getRecordComponents");
            Object[] components = (Object[]) getRecordComponents.invoke(templateClass);
            if (components == null || components.length == 0) {
                return null;
            }

            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            boolean replaced = false;

            for (int i = 0; i < components.length; i++) {
                Object component = components[i];
                Method getType = component.getClass().getMethod("getType");
                Method getAccessor = component.getClass().getMethod("getAccessor");
                Class<?> parameterType = (Class<?>) getType.invoke(component);
                Method accessor = (Method) getAccessor.invoke(component);

                parameterTypes[i] = parameterType;
                if (matchesClassName(parameterType, GAME_PROFILE_CLASS)) {
                    arguments[i] = replacementProfile;
                    replaced = true;
                } else {
                    accessor.setAccessible(true);
                    arguments[i] = accessor.invoke(template);
                }
            }

            if (!replaced) {
                return null;
            }

            Constructor<?> constructor = templateClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object rebuildUsingConstructors(Object template, Object replacementProfile) {
        Constructor<?>[] constructors = template.getClass().getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));

        for (Constructor<?> constructor : constructors) {
            Object[] arguments = buildTemplateArguments(template, constructor.getParameterTypes(), replacementProfile);
            if (arguments == null) {
                continue;
            }

            try {
                constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Object createProfileResult(Object gameProfile) {
        try {
            Class<?> profileResultClass = Class.forName(PROFILE_RESULT_CLASS);
            Constructor<?>[] constructors = profileResultClass.getDeclaredConstructors();
            Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));

            for (Constructor<?> constructor : constructors) {
                Object[] arguments = buildFreshArguments(constructor.getParameterTypes(), gameProfile);
                if (arguments == null) {
                    continue;
                }

                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (Exception ignored) {
                }
            }
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception e) {
            LOGGER.warning("AuthlibAdapter: Failed to create ProfileResult: " + e.getMessage());
        }
        return null;
    }

    private static Object[] buildFreshArguments(Class<?>[] parameterTypes, Object replacementProfile) {
        Object[] arguments = new Object[parameterTypes.length];
        boolean replaced = false;

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (matchesClassName(parameterType, GAME_PROFILE_CLASS)) {
                arguments[i] = replacementProfile;
                replaced = true;
                continue;
            }

            Object defaultValue = defaultValue(parameterType);
            if (defaultValue == MISSING) {
                return null;
            }
            arguments[i] = defaultValue;
        }

        return replaced ? arguments : null;
    }

    private static Object[] buildTemplateArguments(Object template, Class<?>[] parameterTypes, Object replacementProfile) {
        Object[] arguments = new Object[parameterTypes.length];
        boolean replaced = false;

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (matchesClassName(parameterType, GAME_PROFILE_CLASS)) {
                arguments[i] = replacementProfile;
                replaced = true;
                continue;
            }

            Object extractedValue = extractValueByType(template, parameterType);
            if (extractedValue == MISSING) {
                extractedValue = defaultValue(parameterType);
            }
            if (extractedValue == MISSING) {
                return null;
            }
            arguments[i] = extractedValue;
        }

        return replaced ? arguments : null;
    }

    private static Object extractValueByType(Object template, Class<?> parameterType) {
        for (Method method : getCandidateMethods(template.getClass())) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!method.getReturnType().getName().equals(parameterType.getName())) {
                continue;
            }
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            try {
                method.setAccessible(true);
                return method.invoke(template);
            } catch (Exception ignored) {
            }
        }

        for (Field field : getCandidateFields(template.getClass())) {
            if (!field.getType().getName().equals(parameterType.getName())) {
                continue;
            }

            try {
                field.setAccessible(true);
                return field.get(template);
            } catch (Exception ignored) {
            }
        }

        return MISSING;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == null) {
            return MISSING;
        }
        if (!type.isPrimitive()) {
            if (type == String.class) {
                return "";
            }
            if (Optional.class.isAssignableFrom(type)) {
                return Optional.empty();
            }
            if (Set.class.isAssignableFrom(type)) {
                return Collections.emptySet();
            }
            if (List.class.isAssignableFrom(type)) {
                return Collections.emptyList();
            }
            if (Collection.class.isAssignableFrom(type)) {
                return Collections.emptyList();
            }
            if (Map.class.isAssignableFrom(type)) {
                return Collections.emptyMap();
            }
            if (type.isArray()) {
                return Array.newInstance(type.getComponentType(), 0);
            }
            if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                return constants != null && constants.length > 0 ? constants[0] : null;
            }
            return null;
        }
        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Byte.TYPE) {
            return (byte) 0;
        }
        if (type == Short.TYPE) {
            return (short) 0;
        }
        if (type == Integer.TYPE) {
            return 0;
        }
        if (type == Long.TYPE) {
            return 0L;
        }
        if (type == Float.TYPE) {
            return 0F;
        }
        if (type == Double.TYPE) {
            return 0D;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        return MISSING;
    }

    private static Collection<Object> createProperties(JsonArray jsonProperties) {
        if (jsonProperties == null || jsonProperties.size() == 0) {
            return Collections.emptyList();
        }

        List<Object> properties = new ArrayList<Object>();
        for (int i = 0; i < jsonProperties.size(); i++) {
            JsonObject propertyJson = jsonProperties.get(i).getAsJsonObject();
            String propertyName = propertyJson.get("name").getAsString();
            String propertyValue = propertyJson.get("value").getAsString();
            String propertySignature = propertyJson.has("signature") ? propertyJson.get("signature").getAsString() : null;
            Object property = createProperty(propertyName, propertyValue, propertySignature);
            if (property != null) {
                properties.add(property);
            }
        }
        return properties;
    }

    private static Collection<Object> collectProperties(Object source) {
        Object sourceProfile = extractGameProfile(source);
        if (sourceProfile == null) {
            return Collections.emptyList();
        }

        Object sourceProperties = getPropertyContainer(sourceProfile);
        if (sourceProperties == null) {
            return Collections.emptyList();
        }

        List<Object> properties = new ArrayList<Object>();
        for (Object property : getPropertyValues(sourceProperties)) {
            if (property != null) {
                properties.add(property);
            }
        }
        return properties;
    }

    private static Object createEmptyPropertyMap() {
        return createPropertyMap(Collections.emptyList());
    }

    private static Object createPropertyMap(Collection<Object> properties) {
        try {
            Class<?> propertyMapClass = Class.forName(PROPERTY_MAP_CLASS);
            try {
                Constructor<?> constructor = propertyMapClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object propertyMap = constructor.newInstance();
                for (Object property : properties) {
                    String propertyName = readString(property, "getName", "name");
                    if (propertyName != null) {
                        putProperty(propertyMap, propertyName, property);
                    }
                }
                return propertyMap;
            } catch (NoSuchMethodException ignored) {
            }

            Class<?> multimapClass = Class.forName(MULTIMAP_CLASS);
            Class<?> linkedHashMultimapClass = Class.forName("com.google.common.collect.LinkedHashMultimap");
            Method createMethod = linkedHashMultimapClass.getMethod("create");
            Object backingMap = createMethod.invoke(null);
            for (Object property : properties) {
                String propertyName = readString(property, "getName", "name");
                if (propertyName != null) {
                    putProperty(backingMap, propertyName, property);
                }
            }

            Constructor<?> constructor = propertyMapClass.getDeclaredConstructor(multimapClass);
            constructor.setAccessible(true);
            return constructor.newInstance(backingMap);
        } catch (Exception e) {
            LOGGER.warning("AuthlibAdapter: Failed to create PropertyMap: " + e.getMessage());
            return null;
        }
    }

    private static Object createProperty(String name, String value, String signature) {
        try {
            Class<?> propertyClass = Class.forName(PROPERTY_CLASS);
            if (signature != null) {
                try {
                    Constructor<?> constructor = propertyClass.getConstructor(String.class, String.class, String.class);
                    return constructor.newInstance(name, value, signature);
                } catch (NoSuchMethodException ignored) {
                }
            }

            Constructor<?> constructor = propertyClass.getConstructor(String.class, String.class);
            return constructor.newInstance(name, value);
        } catch (Exception e) {
            LOGGER.warning("AuthlibAdapter: Failed to create Property: " + e.getMessage());
            return null;
        }
    }

    private static Object extractGameProfile(Object authResult, Set<Object> visited) {
        if (authResult == null || visited.contains(authResult)) {
            return null;
        }
        visited.add(authResult);

        if (authResult instanceof Optional) {
            Optional<?> optional = (Optional<?>) authResult;
            return optional.isPresent() ? extractGameProfile(optional.get(), visited) : null;
        }

        if (isGameProfileObject(authResult)) {
            return authResult;
        }

        for (String accessorName : new String[]{"profile", "getProfile", "gameProfile", "getGameProfile"}) {
            Object nested = invokeNoArg(authResult, accessorName);
            Object extracted = extractGameProfile(nested, visited);
            if (extracted != null) {
                return extracted;
            }
        }

        for (String fieldName : new String[]{"profile", "gameProfile"}) {
            Object nested = readField(authResult, fieldName);
            Object extracted = extractGameProfile(nested, visited);
            if (extracted != null) {
                return extracted;
            }
        }

        for (Field field : getCandidateFields(authResult.getClass())) {
            if (!matchesClassName(field.getType(), GAME_PROFILE_CLASS)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object nested = field.get(authResult);
                Object extracted = extractGameProfile(nested, visited);
                if (extracted != null) {
                    return extracted;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static boolean isGameProfileObject(Object value) {
        return value != null && matchesClassName(value.getClass(), GAME_PROFILE_CLASS);
    }

    private static boolean matchesClassName(Class<?> type, String expectedClassName) {
        return type != null && expectedClassName.equals(type.getName());
    }

    private static String readString(Object target, String... methodNames) {
        Object value = invokeAnyNoArg(target, methodNames);
        return value instanceof String ? (String) value : null;
    }

    private static UUID readUuid(Object target, String... methodNames) {
        Object value = invokeAnyNoArg(target, methodNames);
        return value instanceof UUID ? (UUID) value : null;
    }

    private static Object invokeAnyNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }

        for (String methodName : methodNames) {
            Object value = invokeNoArg(target, methodName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
        }

        for (Method method : getCandidateMethods(target.getClass())) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                try {
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        for (Field field : getCandidateFields(target.getClass())) {
            if (!field.getName().equals(fieldName)) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Object getPropertyContainer(Object profile) {
        return invokeAnyNoArg(profile, "getProperties", "properties");
    }

    private static void clearPropertyContainer(Object propertyContainer) {
        if (propertyContainer == null) {
            return;
        }

        try {
            Method clearMethod = propertyContainer.getClass().getMethod("clear");
            clearMethod.invoke(propertyContainer);
        } catch (Exception ignored) {
        }
    }

    private static Collection<?> getPropertyValues(Object propertyContainer) {
        if (propertyContainer == null) {
            return Collections.emptyList();
        }

        Object values = invokeNoArg(propertyContainer, "values");
        if (values instanceof Collection) {
            return (Collection<?>) values;
        }
        if (values instanceof Iterable) {
            List<Object> copiedValues = new ArrayList<Object>();
            for (Object value : (Iterable<?>) values) {
                copiedValues.add(value);
            }
            return copiedValues;
        }
        return Collections.emptyList();
    }

    private static void putProperty(Object propertyContainer, String propertyName, Object property) {
        if (propertyContainer == null || propertyName == null || property == null) {
            return;
        }

        for (Method method : getCandidateMethods(propertyContainer.getClass())) {
            if (!method.getName().equals("put") || method.getParameterCount() != 2) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(propertyContainer, propertyName, property);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Method> getCandidateMethods(Class<?> type) {
        List<Method> methods = new ArrayList<Method>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        methods.addAll(Arrays.asList(type.getMethods()));
        return methods;
    }

    private static List<Field> getCandidateFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
