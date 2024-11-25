package com.bogdatech.logic;

import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserService {

    @Autowired
    private IUsersService usersService;

    public BaseResponse<Object> addUser(UsersDO request) {
        int i = usersService.addUser(request);
        if (i > 0) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
        }

    }

    public UsersDO getUser(UsersDO request) {
        UsersDO userByName = usersService.getUserByName(request.getShopName());
        return userByName;
    }
}
