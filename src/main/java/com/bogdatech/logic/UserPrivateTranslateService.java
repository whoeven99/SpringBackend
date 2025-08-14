package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.IUserPrivateTranslateService;
import com.bogdatech.entity.DO.UserPrivateTranslateDO;
import com.bogdatech.integration.ChatGptByOpenaiIntegration;
import com.bogdatech.integration.PrivateIntegration;
import com.bogdatech.utils.CharacterCountUtils;
import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.bogdatech.utils.UserPrivateUtils.getApiKey;
import static com.bogdatech.utils.UserPrivateUtils.maskString;

@Service
public class UserPrivateTranslateService {
    private final IUserPrivateTranslateService iUserPrivateTranslateService;
    private final SecretClient secretClient;
    private final ChatGptByOpenaiIntegration chatGptByOpenaiIntegration;
    private final PrivateIntegration privateIntegration;

    @Autowired
    public UserPrivateTranslateService(IUserPrivateTranslateService iUserPrivateTranslateService, SecretClient secretClient, ChatGptByOpenaiIntegration chatGptByOpenaiIntegration, PrivateIntegration privateIntegration) {
        this.iUserPrivateTranslateService = iUserPrivateTranslateService;
        this.secretClient = secretClient;
        this.chatGptByOpenaiIntegration = chatGptByOpenaiIntegration;
        this.privateIntegration = privateIntegration;
    }

    public Boolean configPrivateModel(String shopName, UserPrivateTranslateDO data) {
        data.setShopName(shopName);
        //判断这个值在DB中是否存在。存在一系列操作后，插入；不存在，更新相关数据
        UserPrivateTranslateDO dbData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, data.getApiName()));

        if (dbData != null) {
            //仅更新 api_model，prompt_word，token_limit ，is_selected
            return iUserPrivateTranslateService.update(new LambdaUpdateWrapper<UserPrivateTranslateDO>()
                    .eq(UserPrivateTranslateDO::getShopName, shopName)
                    .eq(UserPrivateTranslateDO::getApiName, data.getApiName())
                    .set(UserPrivateTranslateDO::getApiModel, data.getApiModel())
                    .set(UserPrivateTranslateDO::getPromptWord, data.getPromptWord())
                    .set(UserPrivateTranslateDO::getTokenLimit, data.getTokenLimit())
                    .set(UserPrivateTranslateDO::getIsSelected, data.getIsSelected()));
        }
        String userKey = getApiKey(shopName, data.getApiName());
        //将数据存到Azure服务器里面
        KeyVaultSecret keyVaultSecret = secretClient.setSecret(userKey, data.getApiKey());

        //将数据存到数据库中
        data.setApiKey(userKey);
        boolean save = iUserPrivateTranslateService.save(data);
        return keyVaultSecret != null && save;
    }



    public UserPrivateTranslateDO getUserPrivateData(String shopName, Integer apiName) {
        String userKey = getApiKey(shopName, apiName);
        KeyVaultSecret keyVaultSecret = secretClient.getSecret(userKey);
        String keyVaultSecretValue = keyVaultSecret.getValue();
        // 从数据中获取相关数据
        UserPrivateTranslateDO one = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiName, apiName));
        // 处理key后，存放到UserPrivateTranslateDO
        String key = maskString(keyVaultSecretValue);
        one.setApiKey(key);
        return one;
    }

    /**
     * 暂时先不做 等后面再说
     * */
    public void testPrivateModel(String shopName, Integer apiName, String data) {

    }

    /**
     * 根据apiName，返回对应的值
     * */
    public String getGenerateText(String shopName, Integer apiName, String text, String apiKey, String target, String model, CharacterCountUtils counter, Long limitChars, OpenAIClient client) {
        //暂时先写两个，Google和openAI
        return switch (apiName) {
            case 0 ->
                //google
                    privateIntegration.getGoogleTranslationWithRetry(text, apiKey, target, shopName, limitChars);
            case 1 ->
                //openAI
                    chatGptByOpenaiIntegration.chatWithGptOpenai(text, model, counter, limitChars, client, shopName);
            default -> text;
        };
    }
}
