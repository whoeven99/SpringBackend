package com.bogdatech.enums;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum InitialTaskStatusEnum {
    INIT(0, "刚创建，未初始化"),
    TASKS_CREATING(2, "创建子task中"),
    TASKS_CREATED(1, "子task创建完成"),
    TRANSLATED_WRITING_SHOPIFY(3, "翻译完，写入中"),
    TRANSLATED_WRITTEN(4, "翻译完，写入完成"),
    ;

    public int status;
    public String description;

    InitialTaskStatusEnum(int status, String description) {
        this.status = status;
        this.description = description;
    }
}
