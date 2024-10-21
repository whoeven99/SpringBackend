package com.bogdatech.exception;

import lombok.Data;

@Data
public class ClientException extends RuntimeException{
    private String errorMessage;
    private String success;
    public ClientException(String success,String errorMessage) {
        this.errorMessage = errorMessage;
        this.success = success;
    }
}
