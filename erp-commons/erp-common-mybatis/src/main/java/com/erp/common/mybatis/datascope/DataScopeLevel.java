package com.erp.common.mybatis.datascope;

import java.util.Arrays;

/**
 * 数据权限等级枚举。
 */
public enum DataScopeLevel {
    ALL(1, "全部数据"),
    DEPT(2, "本部门数据"),
    DEPT_AND_CHILD(3, "本部门及下级部门数据"),
    SELF(4, "仅本人数据"),
    CUSTOM_DEPT(5, "自定义部门数据");

    private final int code;
    private final String description;

    DataScopeLevel(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static DataScopeLevel fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(level -> level.code == code)
                .findFirst()
                .orElse(null);
    }
}
