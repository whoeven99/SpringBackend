package com.bogdatech.logic;

import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.model.controller.request.UserRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserService {

    @Autowired
    private JdbcRepository jdbcRepository;

    public BaseResponse<Object> addUser(UserRequest request) {
        int i = jdbcRepository.addUser(request);
        if (i > 0) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
        }

    }

    public void getUser() {

    }
}
