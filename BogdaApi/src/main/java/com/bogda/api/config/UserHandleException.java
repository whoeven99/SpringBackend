package com.bogda.api.config;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.exception.ClientException;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class UserHandleException {
    @ExceptionHandler(Exception.class)
    public BaseResponse resolveException(Exception ex){
        //判断拦截的异常是我们自定义的异常
        if (ex instanceof ClientException){
            System.out.println("ClientException failed by " + ((ClientException) ex).getErrorMessage());
            AppInsightsUtils.trackTrace("AppException failed by " + ((ClientException) ex).getErrorMessage());
            return new BaseResponse().CreateErrorResponse(((ClientException) ex).getErrorMessage());
        }

        //如果拦截的异常不是我们自定义的异常
        System.out.println("Exception failed by " + ex.getMessage());
        // 打印并收集完整堆栈信息
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        System.out.println(stack);
        AppInsightsUtils.trackTrace("Exception failed by " + ex.getMessage() + "\n" + stack);
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
