package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.entity.UserPrivateDO;
import com.bogdatech.model.controller.request.UserPrivateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;

@Component
public class UserPrivateService {

    private final IUserPrivateService userPrivateService;

    @Autowired
    public UserPrivateService(IUserPrivateService userPrivateService) {
        this.userPrivateService = userPrivateService;
    }

    public BaseResponse<Object> saveOrUpdateUserData(UserPrivateRequest userPrivateRequest) {
        //如果user的googleKey或openaiKey 有值，判断对应的value有没有值，没有的话报错
        if (userPrivateRequest.getGoogleKey() != null && userPrivateRequest.getGoogleSecret() == null) {
            return new BaseResponse<>().CreateErrorResponse("googleSecret 不能为空");
        }
        if (userPrivateRequest.getOpenaiKey() != null && userPrivateRequest.getOpenaiSecret() == null) {
            return new BaseResponse<>().CreateErrorResponse("openaiSecret 不能为空");
        }
        //将userPrivateRequest转为userPrivateDO
        UserPrivateDO userPrivateDO = new UserPrivateDO(null, userPrivateRequest.getShopName(), userPrivateRequest.getAmount(), null, userPrivateRequest.getOpenaiKey(), userPrivateRequest.getGoogleKey());

        //存到数据库中
        //先判断userPrivateDO是否已经存在，如果存在则更新，如果不存在则插入
        //存到Azure的keyVault中
        try {
            System.out.println("查询数据库: " + userPrivateDO.getShopName());
            UserPrivateDO user = userPrivateService.selectOneByShopName(userPrivateDO.getShopName());
            if (user != null) {
                //更新
                //openaiKey和googleKey为空则不更新
                System.out.println("用户存在，执行更新");
                userPrivateService.update(userPrivateDO, new QueryWrapper<UserPrivateDO>().eq(SHOP_NAME, userPrivateDO.getShopName()));
                //TODO: 更新Azure的keyVault

            } else {
                //存入
                System.out.println("用户不存在，执行插入");
                userPrivateService.save(userPrivateDO);
                //存入Azure的keyVault

            }
        } catch (Exception e) {
            System.out.println("保存用户数据失败：" + e.getMessage());
            return new BaseResponse<>().CreateErrorResponse("保存用户数据失败");
        }

        return new BaseResponse<>().CreateSuccessResponse("保存用户数据成功");
    }

    public BaseResponse<Object> getUserData(UserPrivateRequest userPrivateRequest) {
        UserPrivateDO userPrivateDO;
        int retries = 3;
        int delay = 1000;
        while (retries > 0) {
            try {
                userPrivateDO = userPrivateService.selectOneByShopName(userPrivateRequest.getShopName());
                if (userPrivateDO != null) {
                    return new BaseResponse<>().CreateSuccessResponse(userPrivateDO);
                }else {
                    return new BaseResponse<>().CreateErrorResponse("用户不存在");
                }
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    System.out.println("failed: " + e.getMessage());
                } else {
                    try {
                        Thread.sleep(delay);  // 延迟重试
                        delay *= 2;  // 增加延迟时间，指数回退
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return new BaseResponse<>().CreateErrorResponse("查询用户数据失败");
    }
}
