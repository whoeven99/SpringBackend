package com.bogda.task.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class EnableScheduledTaskTest {

    @Test
    void metaAnnotation_shouldHaveRuntimeRetention_andMethodTarget() {
        Retention retention = EnableScheduledTask.class.getAnnotation(Retention.class);
        assertNotNull(retention, "EnableScheduledTask 应该有 @Retention 注解");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(), "Retention 应为 RUNTIME");

        Target target = EnableScheduledTask.class.getAnnotation(Target.class);
        assertNotNull(target, "EnableScheduledTask 应该有 @Target 注解");
        ElementType[] types = target.value();
        boolean hasMethod = false;
        for (ElementType t : types) {
            if (t == ElementType.METHOD) {
                hasMethod = true;
                break;
            }
        }
        assertTrue(hasMethod, "@Target 必须包含 ElementType.METHOD");
    }

    static class DummyClass {
        @EnableScheduledTask
        public void annotatedMethod() {
        }

        public void notAnnotatedMethod() {
        }
    }

    @Test
    void annotatedMethod_shouldBeDiscoverableAtRuntime() throws NoSuchMethodException {
        Method annotated = DummyClass.class.getMethod("annotatedMethod");
        assertTrue(annotated.isAnnotationPresent(EnableScheduledTask.class), "被 @EnableScheduledTask 标注的方法应在运行时可见");

        Method notAnnotated = DummyClass.class.getMethod("notAnnotatedMethod");
        assertFalse(notAnnotated.isAnnotationPresent(EnableScheduledTask.class), "未标注的方法不应包含该注解");
    }
}

