package com.bogdatech.model.controller.response;

public class BaseResponse {
    private String status;
    private String errMessage;

    public BaseResponse CreateSuccessResponse() {
        this.status = "success";
        this.errMessage = "";
        return this;
    }
}
