package com.bogdatech.logic;

import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.KlaviyoDataDO;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.model.controller.request.ProfileToListRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.bogdatech.constants.KlaviyoConstants.I_I_E;
import static com.bogdatech.constants.KlaviyoConstants.PROFILE;

@Component
@Transactional
public class UserService {

    @Autowired
    private IUsersService usersService;

    @Autowired
    private KlaviyoService klaviyoService;

    public BaseResponse<Object> addUser(UsersDO usersDO) {
        int i = usersService.addUser(usersDO);
        if (i > 0) {
            //创建Klaviyo用户并加入到初次安装的list中
            String profileId = klaviyoService.createProfile(usersDO);
            klaviyoService.addProfileToDatabase(new KlaviyoDataDO(usersDO.getShopName(), usersDO.getShopName(), PROFILE, profileId));
            // 获取初次注册List的id
            String listId = klaviyoService.getListId(I_I_E);
            if (profileId != null && listId != null) {
                klaviyoService.addProfileToKlaviyoList(new ProfileToListRequest(profileId, listId));
            }
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
        }

    }

    public UsersDO getUser(UsersDO request) {
        return usersService.getUserByName(request.getShopName());
    }
}
