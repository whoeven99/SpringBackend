package com.bogdatech.enums;

import lombok.Getter;

@Getter
public enum ErrorEnum {
    SERVER_ERROR(10001, "服务器错误"),
    /*
    * josn解析错误
    */
    JSON_PARSE_ERROR(10002, "json解析错误"),
    ;

    public int errCode;
    public String errMsg;

    ErrorEnum(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }
}
