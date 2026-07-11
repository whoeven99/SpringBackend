package com.bogda.api.support;

import com.bogda.common.controller.response.AidgeResponse;
import com.bogda.common.controller.response.BaseResponse;

/** PC / APG 产品 API 已下线，Controller 保留路由并统一返回错误。 */
public final class DisabledProductEndpoints {

    public static final String MESSAGE = "Product API retired (PC/APG); service unavailable";

    private DisabledProductEndpoints() {
    }

    public static <T> BaseResponse<T> error() {
        return new BaseResponse<T>().CreateErrorResponse(MESSAGE);
    }

    public static <T> AidgeResponse<T> aidgeError() {
        AidgeResponse<T> response = new AidgeResponse<>();
        response.setSuccess(false);
        response.setCode(503);
        response.setMessage(MESSAGE);
        return response;
    }
}
