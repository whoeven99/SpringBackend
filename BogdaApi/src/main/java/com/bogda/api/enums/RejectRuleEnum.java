package com.bogda.api.enums;


import lombok.Getter;

import java.util.regex.Pattern;

@Getter
public enum RejectRuleEnum {
    PLUS_EQUAL_PREFIX(
            Pattern.compile("^\\=+.*"),
            "以 += 开头"
    ),

    PURE_DIGIT_PUNC(
            Pattern.compile("^[\\d\\p{P}]+$"),
            "纯数字或标点"
    ),

    ID_15(
            Pattern.compile("^[A-Za-z0-9]{15}$"),
            "15位字母数字ID"
    ),

    UUID(
            Pattern.compile("^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$"),
            "UUID"
    ),

    GA(
            Pattern.compile("^GA\\d+(\\.\\d+)+$"),
            "GA 标识"
    ),

    GA_KEY(
            Pattern.compile("^_(gid|gat)$"),
            "GA key"
    ),

    BASE64(
            Pattern.compile("^[A-Za-z0-9+/]{40,}={0,2}$"),
            "Base64 数据"
    ),

    HASH_64(
            Pattern.compile("^[a-fA-F0-9]{64}$"),
            "64位十六进制 Hash"
    ),

    HASH_32(
            Pattern.compile("^[a-fA-F0-9]{32}$"),
            "32位十六进制 Hash"
    ),

    JWT(
            Pattern.compile("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"),
            "JWT Token"
    ),

//    SUSPICIOUS_ALNUM(
//            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])[A-Za-z0-9]{9,}$"),
//            "疑似随机字符串"
//    ),

    PHONE(
            Pattern.compile(
                    "\\+\\d{1,3}(?:\\s?(?:\\(\\d+\\)|\\d+))?\\s?\\d[\\d\\s-]{3,13}\\d"
                            + "|\\+86\\s?1\\d{10}"
                            + "|00\\d{1,3}\\s?1\\d{10}"
                            + "|\\+\\d{8,15}"
            ),
            "电话号码"
    ),

    EMAIL(
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
            "邮箱"
    ),

    WITH_DATA(Pattern.compile("^[A-Za-z0-9]+(-[A-Za-z0-9]+)+$"), "大小写 + 数字 + 多段 -"),

    HORIZONTAL_BAR_DATE(Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$"), "日期")
    ;


    private final Pattern pattern;
    private final String reason;

    RejectRuleEnum(Pattern pattern, String reason) {
        this.pattern = pattern;
        this.reason = reason;
    }

    public boolean matches(String value) {
        return pattern.matcher(value).matches();
    }

}
