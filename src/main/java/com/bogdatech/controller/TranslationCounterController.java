package com.bogdatech.controller;

import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
public class TranslationCounterController {
    @Autowired
    private JdbcRepository jdbcRepository;

    @PostMapping("/translationCounter/insertCharsByShopName")
    public BaseResponse insertCharsByShopName(@RequestBody TranslationCounterRequest request) {
        int result = jdbcRepository.insertCharsByShopName(request);
        //int result = 1;
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    @PostMapping("/translationCounter/getCharsByShopName")
    public BaseResponse getCharsByShopName(@RequestBody TranslationCounterRequest request) {
        List<TranslationCounterRequest> translatesDOS = jdbcRepository.readCharsByShopName(request);
        if (translatesDOS.size() > 0){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @PostMapping("/translationCounter/updateCharsByShopName")
    public BaseResponse updateCharsByShopName(@RequestBody TranslationCounterRequest request) {
        int result = jdbcRepository.updateCharsByShopName(request);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }
}
