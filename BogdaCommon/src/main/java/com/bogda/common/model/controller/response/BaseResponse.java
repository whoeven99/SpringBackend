package com.bogda.common.model.controller.response;

import com.bogda.common.enums.ErrorEnum;
import lombok.Data;

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

    public BaseResponse<T> CreateErrorResponse(T response, String errorMsg) {
        this.success = false;
        this.response = response;
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

    public static <T> BaseResponse<T> FailedResponse(String errorMsg) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setSuccess(false);
        response.setErrorCode(10001);
        response.setErrorMsg(errorMsg);
        return response;
    }

    public static <T> BaseResponse<T> SuccessResponse(T res) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setSuccess(true);
        response.setResponse(res);
        return response;
    }
}
