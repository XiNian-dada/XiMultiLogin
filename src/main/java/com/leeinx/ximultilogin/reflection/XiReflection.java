package com.leeinx.ximultilogin.reflection;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * 反射工具类，用于处理跨版本 NMS 操作
 * 提供类型-based 字段查找和 NMS 句柄获取功能
 */
public class XiReflection {

    /**
     * 获取日志记录器
     * 处理 Bukkit 未初始化的情况
     *
     * @return 日志记录器
     */
    private static Logger getLogger() {
        try {
            return Bukkit.getLogger();
        } catch (NullPointerException e) {
            // Bukkit 未初始化，使用 Java 默认日志记录器
            return Logger.getLogger(XiReflection.class.getName());
        }
    }

    /**
     * 根据类型查找目标类中的字段
     * 用于处理跨版本字段名称变更的情况
     *
     * @param target    目标类
     * @param fieldType 字段类型
     * @return 找到的字段，如果未找到返回 null
     */
    public static Field getFieldByType(Class<?> target, Class<?> fieldType) {
        if (target == null || fieldType == null) {
            getLogger().warning("XiReflection: Target or field type is null");
            return null;
        }

        try {
            // 遍历所有字段，包括私有字段
            for (Field field : target.getDeclaredFields()) {
                if (field.getType().equals(fieldType)) {
                    // 设置字段为可访问
                    field.setAccessible(true);
                    return field;
                }
            }

            // 如果当前类未找到，递归查找父类
            if (target.getSuperclass() != null) {
                return getFieldByType(target.getSuperclass(), fieldType);
            }

            getLogger().warning("XiReflection: Field of type " + fieldType.getName() + " not found in " + target.getName());
        } catch (SecurityException e) {
            getLogger().severe("XiReflection: Security exception when searching for field: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取服务器的 NMS 句柄
     * 用于后续的 SessionService 注入
     *
     * @param server Bukkit 服务器实例
     * @return 服务器的 NMS 句柄对象
     */
    public static Object getNMSHandle(Server server) {
        if (server == null) {
            getLogger().warning("XiReflection: Server instance is null");
            return null;
        }

        try {
            // 尝试通过不同版本的字段名获取句柄
            // 1.16.5+ 通常使用 "console" 或 "server" 字段
            Field handleField = null;

            // 首先尝试常见字段名
            String[] possibleFieldNames = {"console", "server", "handle"};
            for (String fieldName : possibleFieldNames) {
                try {
                    handleField = server.getClass().getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // 字段不存在，继续尝试下一个
                }
            }

            // 如果通过名称未找到，尝试通过类型查找
            if (handleField == null) {
                // 尝试查找 CraftServer 类型的字段
                Class<?> craftServerClass = null;
                try {
                    craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
                } catch (ClassNotFoundException e) {
                    // 尝试不同的包路径（如带版本号的）
                    try {
                        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                        craftServerClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer");
                    } catch (ClassNotFoundException ex) {
                        getLogger().severe("XiReflection: Could not find CraftServer class: " + ex.getMessage());
                        return null;
                    } catch (NullPointerException ex) {
                        getLogger().severe("XiReflection: Bukkit server not initialized: " + ex.getMessage());
                        return null;
                    }
                }

                // 查找 NMS Server 类型的字段
                Class<?> nmsServerClass = null;
                try {
                    nmsServerClass = Class.forName("net.minecraft.server.MinecraftServer");
                } catch (ClassNotFoundException e) {
                    // 尝试不同的包路径
                    try {
                        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                        nmsServerClass = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
                    } catch (ClassNotFoundException ex) {
                        getLogger().severe("XiReflection: Could not find MinecraftServer class: " + ex.getMessage());
                        return null;
                    } catch (NullPointerException ex) {
                        getLogger().severe("XiReflection: Bukkit server not initialized: " + ex.getMessage());
                        return null;
                    }
                }

                handleField = getFieldByType(craftServerClass, nmsServerClass);
            }

            if (handleField != null) {
                handleField.setAccessible(true);
                Object handle = handleField.get(server);
                getLogger().info("XiReflection: Successfully obtained NMS handle: " + handle.getClass().getName());
                return handle;
            } else {
                getLogger().severe("XiReflection: Could not find NMS handle field in Server instance");
            }
        } catch (Exception e) {
            getLogger().severe("XiReflection: Exception when getting NMS handle: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 设置字段值
     * 用于注入操作
     *
     * @param object    目标对象
     * @param field     字段
     * @param value     新值
     * @return 是否设置成功
     */
    public static boolean setFieldValue(Object object, Field field, Object value) {
        if (object == null || field == null) {
            getLogger().warning("XiReflection: Object or field is null");
            return false;
        }

        try {
            field.setAccessible(true);
            field.set(object, value);
            return true;
        } catch (Exception e) {
            getLogger().severe("XiReflection: Exception when setting field value: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取字段值
     * 用于提取原始 SessionService
     *
     * @param object    目标对象
     * @param field     字段
     * @return 字段值，如果获取失败返回 null
     */
    public static Object getFieldValue(Object object, Field field) {
        if (object == null || field == null) {
            getLogger().warning("XiReflection: Object or field is null");
            return null;
        }

        try {
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            getLogger().severe("XiReflection: Exception when getting field value: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取类的简单名称（不含包名）
     * 用于日志输出
     *
     * @param clazz 类
     * @return 简单类名
     */
    public static String getSimpleClassName(Class<?> clazz) {
        if (clazz == null) {
            return "null";
        }
        return clazz.getSimpleName();
    }
}
