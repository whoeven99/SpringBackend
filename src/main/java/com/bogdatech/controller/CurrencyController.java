package com.bogdatech.controller;

import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrencyController {

    @Autowired
    private JdbcRepository jdbcRepository;

    @PostMapping("/currency/insertCurrency")
    public BaseResponse<Object> addCurrency(@RequestBody CurrencyRequest request) {
        return jdbcRepository.insertCurrency(request);
    }

    @PostMapping("/currency/updateCurrency")
    public BaseResponse<Object> updateCurrency(@RequestBody CurrencyRequest request) {
        return jdbcRepository.updateCurrency(request);
    }

    @PostMapping("/currency/deleteCurrency")
    public BaseResponse<Object> deleteCurrency(@RequestBody CurrencyRequest request) {
        return jdbcRepository.deleteCurrency(request);
    }

    @PostMapping("/currency/getCurrencyByShopName")
    public BaseResponse<Object> getCurrencyByShopName(@RequestBody CurrencyRequest request) {
        return jdbcRepository.getCurrencyByShopName(request);
    }

    @PostMapping("/currency/test")
    public BaseResponse<Object> test(@RequestBody CurrencyRequest request) {
        return jdbcRepository.test(request);
    }
}
