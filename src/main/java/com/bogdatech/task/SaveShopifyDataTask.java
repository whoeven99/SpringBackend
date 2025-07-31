package com.bogdatech.task;

import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.logic.UserTranslationDataService;
import com.bogdatech.model.controller.request.CloudInsertRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.bogdatech.logic.ShopifyService.saveToShopify;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

@Service
public class SaveShopifyDataTask {
    private final UserTranslationDataService userTranslationDataService;

    @Autowired
    public SaveShopifyDataTask(UserTranslationDataService userTranslationDataService) {
        this.userTranslationDataService = userTranslationDataService;
    }

    /**
     * 每1s调用一次，存储获取到的数据
     * 获取成功后将数据状态改为2
     * */
    @Scheduled(fixedDelay = 1000)
    public void getDataToSaveInShopify(){
        List<UserTranslationDataDO> userTranslationDataDOS = userTranslationDataService.selectTranslationDataList();
        for (UserTranslationDataDO data: userTranslationDataDOS
             ) {
            //将状态改为2
            userTranslationDataService.updateStatusTo2(data.getTaskId(), 2);
            String payload = data.getPayload();
            //将payload解析
            CloudInsertRequest cloudInsertRequest = null;
            try {
                cloudInsertRequest = jsonToObject(payload, CloudInsertRequest.class);
            } catch (Exception e) {
                appInsights.trackTrace("errors : " + e.getMessage());
                appInsights.trackException(e);
                continue;
            }
            if (cloudInsertRequest == null){
                continue;
            }
            saveToShopify(cloudInsertRequest);
            //删除对应任务id
            userTranslationDataService.deleteDataByTaskId(data.getTaskId());
        }
    }
}
