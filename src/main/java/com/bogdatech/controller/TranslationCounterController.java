package com.bogdatech.controller;

import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslationCounterController {
    @Autowired
    private JdbcRepository jdbcRepository;

    @PostMapping("/translationCounter/insertCharsByShopName")
    public BaseResponse insertCharsByShopName(@RequestBody TranslationCounterRequest request) {
        return jdbcRepository.insertCharsByShopName(request);
    }

    @PostMapping("/translationCounter/getCharsByShopName")
    public BaseResponse getCharsByShopName(@RequestBody TranslationCounterRequest request) {
        return jdbcRepository.readCharsByShopName(request);
    }

    @PostMapping("/translationCounter/updateCharsByShopName")
    public BaseResponse updateCharsByShopName(@RequestBody TranslationCounterRequest request) {
        return jdbcRepository.updateCharsByShopName(request);
    }
}
