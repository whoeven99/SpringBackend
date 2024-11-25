package com.bogdatech.enums;

import lombok.Getter;

@Getter
public enum ErrorEnum {
    SERVER_ERROR(10001, "SERVER_ERROR"),
    /*
    * josn解析错误
    */
    JSON_PARSE_ERROR(10002, "JSON_PARSE_ERROR"),
    /*
     * sql插入错误
     */
    SQL_INSERT_ERROR(10003, "SQL_INSERT_ERROR"),
    /*
     * sql更新错误
     */
    SQL_UPDATE_ERROR(10004, "SQL_UPDATE_ERROR"),
    /*
     * sql删除错误
     */
    SQL_DELETE_ERROR(10005, "SQL_DELETE_ERROR"),
    /*
     * sql查询错误
     */
    SQL_SELECT_ERROR(10006, "SQL_SELECT_ERROR"),
    /*
     * 数据已存在
     */
    DATA_EXIST(10007, "DATA_EXIST"),
    /*
     * 翻译错误
     */
    TRANSLATE_ERROR(10008, "TRANSLATE_ERROR"),


    /*
     * 网络错误
     */
    NETWORK_ERROR(10009, "NETWORK_ERROR"),

    ;

    public int errCode;
    public String errMsg;

    ErrorEnum(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }
}
