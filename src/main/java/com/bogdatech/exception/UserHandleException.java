package com.bogdatech.exception;

import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

import static com.bogdatech.enums.ErrorEnum.SERVER_ERROR;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;


@RestControllerAdvice
public class UserHandleException {
    //全局异常处理器

    @ExceptionHandler(Exception.class)
    public BaseResponse resolveException(Exception ex){
        //判断拦截的异常是我们自定义的异常
        if (ex instanceof ClientException){
            System.out.println("-----------ClientException异常错误信息---------");
//            log.error("AppException failed by  {}",((ClientException) ex).getErrorMessage());
            System.out.println("AppException failed by " + ((ClientException) ex).getErrorMessage());
            appInsights.trackTrace("AppException failed by " + ((ClientException) ex).getErrorMessage());
            return new BaseResponse().CreateErrorResponse(((ClientException) ex).getErrorMessage());
        }

        //如果拦截的异常不是我们自定义的异常
        System.out.println("-----------异常错误信息---------");
//        log.error("Exception failed by {}",ex);
        System.out.println("Exception failed by " + ex.getMessage());
        appInsights.trackTrace("Exception failed by " + ex.getMessage());
        return new BaseResponse().CreateErrorResponse(SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse handleMethodArgumentNotValidException  (MethodArgumentNotValidException ex){
        List<ObjectError> errors = ex.getBindingResult().getAllErrors();
        String message = errors.stream().map(ObjectError::getDefaultMessage).collect(Collectors.joining(","));
        System.out.println("-----------校验数据错误信息---------");
//        log.error("Valid参数错误信息: {}", message);
        System.out.println("Valid参数错误信息: " + message);
        appInsights.trackTrace("Valid参数错误信息: " + message);
        return new BaseResponse().CreateErrorResponse(message);
    }
}
