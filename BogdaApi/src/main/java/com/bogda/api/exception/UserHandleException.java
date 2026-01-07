package com.bogda.api.exception;

import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.utils.AppInsightsUtils;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class UserHandleException {
    //全局异常处理器

    @ExceptionHandler(Exception.class)
    public BaseResponse resolveException(Exception ex){
        //判断拦截的异常是我们自定义的异常
        if (ex instanceof ClientException){
            System.out.println("-----------ClientException异常错误信息---------");
            System.out.println("AppException failed by " + ((ClientException) ex).getErrorMessage());
            AppInsightsUtils.trackTrace("AppException failed by " + ((ClientException) ex).getErrorMessage());
            return new BaseResponse().CreateErrorResponse(((ClientException) ex).getErrorMessage());
        }

        //如果拦截的异常不是我们自定义的异常
        System.out.println("-----------异常错误信息---------");
        System.out.println("Exception failed by " + ex.getMessage());
        AppInsightsUtils.trackTrace("Exception failed by " + ex.getMessage());
        return new BaseResponse().CreateErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse handleMethodArgumentNotValidException  (MethodArgumentNotValidException ex){
        List<ObjectError> errors = ex.getBindingResult().getAllErrors();
        String message = errors.stream().map(ObjectError::getDefaultMessage).collect(Collectors.joining(","));
        System.out.println("-----------校验数据错误信息---------");
        System.out.println("Valid参数错误信息: " + message);
        AppInsightsUtils.trackTrace("Valid参数错误信息: " + message);
        return new BaseResponse().CreateErrorResponse(message);
    }
}
