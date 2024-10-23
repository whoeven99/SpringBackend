package com.bogdatech.enums;

import lombok.Getter;

@Getter
public enum ErrorEnum {
    SERVER_ERROR(10001, "服务器错误"),
    /*
    * josn解析错误
    */
    JSON_PARSE_ERROR(10002, "json解析错误"),
    /*
     * sql插入错误
     */
    SQL_INSERT_ERROR(10003, "sql插入错误"),
    /*
     * sql更新错误
     */
    SQL_UPDATE_ERROR(10004, "sql更新错误"),
    /*
     * sql删除错误
     */
    SQL_DELETE_ERROR(10005, "sql删除错误"),
    ;

    public int errCode;
    public String errMsg;

    ErrorEnum(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }
}
