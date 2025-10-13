package com.bogdatech.task;

import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.logic.UserTranslationDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.bogdatech.logic.TranslateService.executorService;

@Service
@EnableScheduling
public class SaveShopifyDataTask {
    @Autowired
    private UserTranslationDataService userTranslationDataService;

    /**
     * 每1s调用一次，存储获取到的数据
     * 获取成功后将数据状态改为2
     */
    @Scheduled(fixedDelay = 1000)
    public void getDataToSaveInShopify() {
        List<UserTranslationDataDO> userTranslationDataDOS = userTranslationDataService.selectTranslationDataList();
        for (UserTranslationDataDO data : userTranslationDataDOS
             ) {
            userTranslationDataService.updateStatusTo2(data.getTaskId(), 2);
        }

        for (UserTranslationDataDO data : userTranslationDataDOS
        ) {
            userTranslationDataService.updateStatusTo2(data.getTaskId(), 2);
        }

        for (UserTranslationDataDO data : userTranslationDataDOS
        ) {
            executorService.submit(() -> {
                userTranslationDataService.translationDataToSave(data);
            });
        }
    }
}
