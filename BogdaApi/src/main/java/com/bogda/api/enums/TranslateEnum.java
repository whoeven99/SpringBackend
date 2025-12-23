package com.bogda.api.enums;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum TranslateEnum {
    NOT_TRANSLATED(0, "Not Translated"),
    SUCCESSFULLY_TRANSLATED(1, "Successfully Translated"),
    TRANSLATING(2, "Translating"),
    PARTIAL_TRANSLATION(3, "Partial translation"),
    //TODO: 先写这些后面，再添加
    ;

    public int status;
    public String statusMsg;

    TranslateEnum(int status, String statusMsg) {
        this.status = status;
        this.statusMsg = statusMsg;
    }
}
