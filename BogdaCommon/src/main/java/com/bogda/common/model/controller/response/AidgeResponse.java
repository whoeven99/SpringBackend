package com.bogda.common.model.controller.response;

import lombok.Data;

@Data
public class AidgeResponse<T> {
    private int code = 200;
    private String message= "";
    private Boolean success;
    private String requestId= "";
    private T data;
    private String result;

    public AidgeResponse<T> CreateSuccessResponse(T response) {
        this.success = true;
        this.data = response;
        this.result= null;
        return this;
    }
}
