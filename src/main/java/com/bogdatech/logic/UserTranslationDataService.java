package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.IUserTranslationDataService;
import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.model.controller.request.CloudInsertRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.bogdatech.logic.ShopifyService.saveToShopify;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

@Service
public class UserTranslationDataService {
    private final IUserTranslationDataService userTranslationDataService;

    @Autowired
    public UserTranslationDataService(IUserTranslationDataService userTranslationDataService) {
        this.userTranslationDataService = userTranslationDataService;
    }

    /**
     * 将翻译后的文本以String的类型存储到数据库中
     * */
    public Boolean insertTranslationData(String translationData, String shopName){
        return userTranslationDataService.insertTranslationData(translationData, shopName);
    }

    /**
     * 读取用户翻译数据，暂定8个不同的用户（shopName）
     * */
    public List<UserTranslationDataDO> selectTranslationDataList(){
        return userTranslationDataService.selectTranslationDataList();
    }

    /**
     * 根据task_id删除对应数据
     * */
    public boolean deleteDataByTaskId(String taskId){
        return userTranslationDataService.removeById(taskId);
    }

    public boolean updateStatusTo2(String taskId, int status) {
        return userTranslationDataService.update(new LambdaUpdateWrapper<UserTranslationDataDO>().eq(UserTranslationDataDO::getTaskId, taskId).set(UserTranslationDataDO::getStatus, status));
    }

    /**
     * 异步去做存shopify的处理
     * */
    @Async
    public void translationDataToSave(UserTranslationDataDO data){
        //将状态改为2
        updateStatusTo2(data.getTaskId(), 2);
        String payload = data.getPayload();
        //将payload解析
        CloudInsertRequest cloudInsertRequest = null;
        try {
            cloudInsertRequest = jsonToObject(payload, CloudInsertRequest.class);
        } catch (Exception e) {
            appInsights.trackTrace("errors : " + e.getMessage());
            appInsights.trackException(e);
            return;
        }
        if (cloudInsertRequest == null){
            return ;
        }
        saveToShopify(cloudInsertRequest);
        //删除对应任务id
        deleteDataByTaskId(data.getTaskId());
    }
}
