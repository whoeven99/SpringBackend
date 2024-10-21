package com.bogdatech.common.enums;

import lombok.Getter;

@Getter
public enum ErrorEnum {
    SERVER_ERROR(10001, "服务器错误"),
    ;

    public int errCode;
    public String errMsg;

    ErrorEnum(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }
}
