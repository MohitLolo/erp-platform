package com.erp.common.log.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link LogParam} / {@link LogResult} / {@link LogIgnore} 的元注解契约。
 *
 * <p>使用反射读取注解，确认 RUNTIME 保留 + 正确的 Target，避免后续重构改坏契约。</p>
 */
class AnnotationContractTest {

    @SuppressWarnings("unused")
    static class Sample {
        @LogParam
        @LogResult
        public String methodLevel(@LogParam("uid") Long id, String name) {
            return name;
        }

        @LogIgnore
        public void ignored() {
        }
    }

    @Test
    void logParam_metaAnnotations() {
        Retention retention = LogParam.class.getAnnotation(Retention.class);
        Target target = LogParam.class.getAnnotation(Target.class);
        Documented documented = LogParam.class.getAnnotation(Documented.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertNotNull(documented);
        // 方法级 + 参数级
        ElementType[] targets = target.value();
        assertTrue(containsAll(targets, ElementType.METHOD, ElementType.PARAMETER),
                "@LogParam must target METHOD and PARAMETER");
    }

    @Test
    void logResult_metaAnnotations_methodOnly() {
        Retention retention = LogResult.class.getAnnotation(Retention.class);
        Target target = LogResult.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(1, target.value().length);
        assertEquals(ElementType.METHOD, target.value()[0]);
    }

    @Test
    void logIgnore_metaAnnotations_methodOnly() {
        Retention retention = LogIgnore.class.getAnnotation(Retention.class);
        Target target = LogIgnore.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(1, target.value().length);
        assertEquals(ElementType.METHOD, target.value()[0]);
    }

    @Test
    void annotations_readableViaReflection() throws Exception {
        Method m = Sample.class.getMethod("methodLevel", Long.class, String.class);
        assertNotNull(m.getAnnotation(LogParam.class), "method-level @LogParam");
        assertNotNull(m.getAnnotation(LogResult.class), "method-level @LogResult");

        Parameter[] params = m.getParameters();
        LogParam onParam = params[0].getAnnotation(LogParam.class);
        assertNotNull(onParam, "parameter-level @LogParam");
        assertEquals("uid", onParam.value(), "value() override on parameter-level");

        Method ignored = Sample.class.getMethod("ignored");
        assertNotNull(ignored.getAnnotation(LogIgnore.class));
    }

    private static boolean containsAll(ElementType[] arr, ElementType... wanted) {
        for (ElementType w : wanted) {
            boolean found = false;
            for (ElementType e : arr) {
                if (e == w) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }
}
