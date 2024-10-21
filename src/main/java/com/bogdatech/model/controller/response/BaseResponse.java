package com.bogdatech.model.controller.response;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseResponse {

    private String success;
    private String errMessage;
    private Object response;

    public BaseResponse(String success, String errMessage) {
    }

    public BaseResponse CreateSuccessResponse(Object response) {
        this.success = "success";
        this.errMessage = "";
        this.response = response;
        return this;
    }
    public BaseResponse CreateErrorResponse(String errorMessage, Object response) {
        this.success = "error";
        this.errMessage = errorMessage;
        this.response = response;
        return this;
    }
}
