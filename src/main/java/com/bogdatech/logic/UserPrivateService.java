package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.entity.DO.UserPrivateDO;
import com.bogdatech.model.controller.request.UserPrivateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.bogdatech.constants.UserPrivateConstants.GOOGLE;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.StringUtils.replaceDot;

@Component
public class UserPrivateService {
    @Autowired
    private IUserPrivateService userPrivateService;
    @Autowired
    private SecretClient secretClient;

    public BaseResponse<Object> saveOrUpdateUserData(UserPrivateRequest userPrivateRequest) {
        String model = userPrivateRequest.getModel();
        if (model == null) {
            return new BaseResponse<>().CreateErrorResponse("model 不能为空");
        }
        //如果user的googleKey或openaiKey 有值，判断对应的value有没有值，没有的话报错
        if (userPrivateRequest.getSecret() == null) {
            return new BaseResponse<>().CreateErrorResponse("Secret 不能为空");
        }

        UserPrivateDO userPrivateDO;
        //根据传入的model，将userPrivateRequest转为对应的userPrivateDO
        switch (model) {
            case "google":
                userPrivateDO = new UserPrivateDO(null, userPrivateRequest.getShopName(), userPrivateRequest.getAmount(), null, null, userPrivateRequest.getSecret());
                break;
            case "openai":
                userPrivateDO = new UserPrivateDO(null, userPrivateRequest.getShopName(), userPrivateRequest.getAmount(), null, userPrivateRequest.getSecret(), null);
                break;
            default:
                return new BaseResponse<>().CreateErrorResponse(model + " 暂时不支持！");
        }
        //存到数据库中
        //先判断userPrivateDO是否已经存在，如果存在则更新，如果不存在则插入
        //存到Azure的keyVault中
        try {
            UserPrivateDO user = userPrivateService.selectOneByShopName(userPrivateDO.getShopName());
            if (user != null) {
                //更新
                //openaiKey和googleKey为空则不更新
                userPrivateService.updatePrivateUserByShopName(userPrivateDO, userPrivateDO.getShopName());

                //更新Azure的keyVault

            } else {
                //存入
                userPrivateService.save(userPrivateDO);
                //存入Azure的keyVault

            }
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("saveOrUpdateUserData " + userPrivateRequest.getShopName() + " 保存用户数据失败：" + e.getMessage());
            return new BaseResponse<>().CreateErrorResponse("保存用户数据失败");
        }

        return new BaseResponse<>().CreateSuccessResponse("保存用户数据成功");
    }

    //获取用户数据
    public BaseResponse<Object> getUserData(UserPrivateRequest userPrivateRequest) {
        UserPrivateDO userPrivateDO;
        int retries = 3;
        int delay = 1000;
        while (retries > 0) {
            try {
                userPrivateDO = userPrivateService.selectOneByShopName(userPrivateRequest.getShopName());

                //如果数据中没有key 就 输出空
                if (userPrivateDO == null) {
                    return new BaseResponse<>().CreateSuccessResponse(new UserPrivateDO());
                }
                if (userPrivateDO.getGoogleKey() == null) {
                    userPrivateDO.setId(null);
                    return new BaseResponse<>().CreateSuccessResponse(userPrivateDO);
                }
                KeyVaultSecret keyVaultSecret = secretClient.getSecret(userPrivateDO.getGoogleKey());
                //对用户的key做处理， 只传前4位和后4位，中间用x代替
                String key = keyVaultSecret.getValue().substring(0, 4) + "**********" + keyVaultSecret.getValue().substring(keyVaultSecret.getValue().length() - 4);
                userPrivateDO.setGoogleKey(key);
                userPrivateDO.setId(null);
                return new BaseResponse<>().CreateSuccessResponse(userPrivateDO);
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    appInsights.trackTrace("getUserData " + userPrivateRequest.getShopName() + " failed: " + e.getMessage());
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

    //更新用户已使用字符数
    public void updateUsedCharsByShopName(String shopName, Integer usedChars) {
        //更新用户
        UserPrivateDO userPrivateDO = new UserPrivateDO();
        userPrivateDO.setUsedAmount(usedChars);
        userPrivateService.updatePrivateUserByShopName(userPrivateDO, shopName);
    }

    //删除用户数据
    public Boolean deleteUserData(String shopName) {
        //只删除 amount 和 key数据
        UserPrivateDO user = userPrivateService.selectOneByShopName(shopName);
        if (user == null) {
            appInsights.trackTrace("deleteUserData " + shopName + " 用户不存在");
            return false;
        }
        //删除用户在keyVault里面的数据
        try {
            shopName = replaceDot(shopName);
            secretClient.getDeletedSecret(shopName + "-" + GOOGLE);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("deleteUserData " + shopName + " 删除用户在keyVault里面的数据失败：" + e.getMessage());
        }

        //将数据库中的数据的amount和key清空
        return userPrivateService.updateAmountAndGoogleKey(user.getShopName());
    }
}
