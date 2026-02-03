package com.Leeinx.ximultilogin.reflection;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * XiReflection 反射工具类测试
 */
public class XiReflectionTest {

    /**
     * 测试内部类
     */
    private static class TestInnerClass {
    }

    /**
     * 测试类
     */
    private static class TestClass {
        private String stringField;
        private int intField;
        private TestInnerClass innerClassField;
    }

    /**
     * 测试 getFieldByType 方法
     */
    @Test
    public void testGetFieldByType() {

        // 测试查找 String 类型字段
        Field stringField = XiReflection.getFieldByType(TestClass.class, String.class);
        assertNotNull("Should find String field", stringField);
        assertEquals("Field name should be stringField", "stringField", stringField.getName());

        // 测试查找 int 类型字段
        Field intField = XiReflection.getFieldByType(TestClass.class, int.class);
        assertNotNull("Should find int field", intField);
        assertEquals("Field name should be intField", "intField", intField.getName());

        // 测试查找自定义类型字段
        Field innerClassField = XiReflection.getFieldByType(TestClass.class, TestInnerClass.class);
        assertNotNull("Should find inner class field", innerClassField);
        assertEquals("Field name should be innerClassField", "innerClassField", innerClassField.getName());

        // 测试查找不存在的字段
        Field nonExistentField = XiReflection.getFieldByType(TestClass.class, double.class);
        assertNull("Should not find non-existent field", nonExistentField);
    }
}
