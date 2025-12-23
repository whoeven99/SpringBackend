package com.bogdatech.exception;

import lombok.Data;

@Data
public class ClientException extends RuntimeException{
    private String errorMessage;
    
    public ClientException(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
