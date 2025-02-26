package com.bogdatech.model.controller.response;

import com.bogdatech.enums.ErrorEnum;
import lombok.*;

@Data
public class BaseResponse<T> {

    private Boolean success;
    private int errorCode = 0;
    private String errorMsg = "";
    private T response = null;

    public BaseResponse<T> CreateSuccessResponse(T response) {
        this.success = true;
        this.response = response;
        return this;
    }

    public BaseResponse CreateErrorResponse(String errorMsg) {
        this.success = false;
        this.errorCode = 10001;
        this.errorMsg = errorMsg;
        return this;
    }

    public BaseResponse<T> CreateErrorResponse(ErrorEnum errorEnum) {
        this.success = false;
        this.errorMsg = errorEnum.getErrMsg();
        this.errorCode = errorEnum.getErrCode();
        return this;
    }

    public BaseResponse<T> CreateErrorResponse(T response) {
        this.success = false;
        this.response = response;
        return this;
    }

}
