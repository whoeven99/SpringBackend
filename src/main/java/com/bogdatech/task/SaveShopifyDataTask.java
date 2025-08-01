package com.bogdatech.task;

import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.logic.UserTranslationDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableAsync
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
            userTranslationDataService.translationDataToSave(data);
        }
    }
}
