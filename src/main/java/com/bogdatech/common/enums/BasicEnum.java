package com.bogdatech.common.enums;

public enum BasicEnum {
    /**
     *成功全是success
     */
    TRUE("success","成功"),
    /**
     *服务器错误
     */
    SERVER_ERROR("error","请联系管理员处理"),
    /**
     *获取汇率失败
     */
    GET_RATE_ERROR("error","获取汇率失败")
    ;

    public String success;
    public String errorMessage;

    BasicEnum(String success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getSuccess() {
        return success;
    }
    public String getErrorMessage(){return errorMessage;}
}
