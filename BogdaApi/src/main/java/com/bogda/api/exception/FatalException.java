package com.bogda.api.exception;

import lombok.Data;

@Data
public class FatalException extends RuntimeException{
    private String errorMessage;

    public FatalException(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
