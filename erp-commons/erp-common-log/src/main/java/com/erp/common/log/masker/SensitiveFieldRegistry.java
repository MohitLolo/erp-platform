package com.erp.common.log.masker;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 敏感字段名注册表（硬编码黑名单）。
 *
 * <p>设计文档 §4.3：默认即"安全"，业务无需逐个标注。所有匹配会先做小写归一化，
 * 因此 {@code Password / PASSWD / pwd} 等大小写变体都能命中。</p>
 *
 * <p>命中规则：字段名小写后等于黑名单任一元素，或<i>包含</i>黑名单任一元素作为子串
 * （命中 {@code userPassword}、{@code idCardNo} 等驼峰形式）。</p>
 */
public final class SensitiveFieldRegistry {

    private static final Set<String> DEFAULT_BLACKLIST;

    static {
        Set<String> set = new HashSet<>(Arrays.asList(
                "password", "passwd", "pwd",
                "secret", "token", "accesstoken", "refreshtoken",
                "apikey", "appsecret", "privatekey",
                "idcard", "idcardno",
                "bankcard", "bankcardno", "cardno",
                "cvv", "cvv2",
                "creditcard"
        ));
        DEFAULT_BLACKLIST = Collections.unmodifiableSet(set);
    }

    private SensitiveFieldRegistry() {
    }

    /**
     * 判定字段名是否敏感。大小写不敏感，支持驼峰子串匹配（如 {@code userPassword} 命中 {@code password}）。
     */
    public static boolean isSensitive(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        if (DEFAULT_BLACKLIST.contains(lower)) {
            return true;
        }
        for (String keyword : DEFAULT_BLACKLIST) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 暴露只读视图，便于测试与 micrometer 报告。 */
    public static Set<String> snapshot() {
        return DEFAULT_BLACKLIST;
    }
}
