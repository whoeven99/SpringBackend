package com.bogdatech.exception;

import com.bogdatech.model.controller.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

import static com.bogdatech.common.enums.BasicEnum.SERVER_ERROR;


@RestControllerAdvice
@Slf4j
public class UserHandleException {
    //全局异常处理器

    @ExceptionHandler(Exception.class)
    public BaseResponse resolveException(Exception ex){
        //判断拦截的异常是我们自定义的异常
        if (ex instanceof ClientException){
            ClientException clientException = (ClientException) ex;
            System.out.println("-----------AppException异常错误信息---------");
            log.error("AppException failed by {},  {}",clientException.getSuccess(),clientException.getMessage());
            return new BaseResponse(clientException.getSuccess(),clientException.getMessage());
        }

        //如果拦截的异常不是我们自定义的异常
        System.out.println("-----------异常错误信息---------");
        log.error("Exception failed by {}",ex);
        return new BaseResponse(SERVER_ERROR.getSuccess(),SERVER_ERROR.getErrorMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse handleMethodArgumentNotValidException  (MethodArgumentNotValidException ex){
        List<ObjectError> errors = ex.getBindingResult().getAllErrors();
        String message = errors.stream().map(ObjectError::getDefaultMessage).collect(Collectors.joining(","));
        System.out.println("-----------校验数据错误信息---------");
        log.error("Valid参数错误信息: {}", message);
        return new BaseResponse(SERVER_ERROR.getSuccess(),message);
    }
}
