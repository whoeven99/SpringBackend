package com.bogdatech.model.controller.request;

import lombok.Data;

import java.util.Map;

@Data
public class SignRequest {
    String api;
    Map<String, String> params;
}
