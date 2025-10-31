package com.bogdatech.logic.translate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.ProgressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bogdatech.logic.RabbitMqTranslateService.MANUAL;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class TranslateProgressService {
    @Autowired
    private TranslateService translateService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;

    public BaseResponse<ProgressResponse> getAllProgressData(String shopName, String source) {
        List<InitialTranslateTasksDO> initialTranslateTasksDOS = initialTranslateTasksMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::getSource, source).eq(InitialTranslateTasksDO::getTaskType, MANUAL).eq(InitialTranslateTasksDO::isDeleted, false).orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        // 获取所有的TranslatesDO
        ProgressResponse response = new ProgressResponse();
        List<ProgressResponse.Progress> list = new ArrayList<>();
        response.setList(list);
        if (initialTranslateTasksDOS.isEmpty()) {
            return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
        }

        // 先获取所有的， 然后转化为map
        List<TranslatesDO> translatesDOList = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getSource, source).orderByAsc(TranslatesDO::getUpdateAt));
        Map<String, TranslatesDO> translatesMap = translatesDOList.stream()
                .collect(Collectors.toMap(
                        TranslatesDO::getTarget,  // key：target 字段
                        Function.identity()      // value：整个对象本身
                ));

        for (InitialTranslateTasksDO initialTranslateTasksDO : initialTranslateTasksDOS) {
            TranslatesDO translatesDO = translatesMap.get(initialTranslateTasksDO.getTarget());
            // 不返回状态为0的数据
            if (translatesDO.getStatus() == 0) {
                continue;
            }

            ProgressResponse.Progress progress = new ProgressResponse.Progress();
            progress.setStatus(translatesDO.getStatus());
            progress.setTarget(translatesDO.getTarget());

            Map<String, String> map = translationParametersRedisService.hgetAll(generateProgressTranslationKey(shopName, source, translatesDO.getTarget()));
            progress.setResourceType(map.get(TranslationParametersRedisService.TRANSLATING_MODULE));
            progress.setValue(map.get(TranslationParametersRedisService.TRANSLATING_STRING));
            progress.setTranslateStatus("4".equals(map.get(TranslationParametersRedisService.TRANSLATION_STATUS)) ? "translation_process_saved" : "3".equals(map.get(TranslationParametersRedisService.TRANSLATION_STATUS)) ? "translation_process_saving_shopify"
                    : "2".equals(map.get(TranslationParametersRedisService.TRANSLATION_STATUS)) ? "translation_process_translating"
                    : "translation_process_init");

            Map<String, Integer> progressData = translateService.getProgressData(shopName, translatesDO.getTarget(), source);
            appInsights.trackTrace("getAllProgressData " + shopName + " target : " + translatesDO.getTarget() + " " + source + " " + progressData);
            progress.setProgressData(progressData);

            if ("3".equals(map.get(TranslationParametersRedisService.TRANSLATION_STATUS))) {
                Map<String, Integer> writingData = translationParametersRedisService.getWritingData(shopName, translatesDO.getTarget());
                appInsights.trackTrace("getWritingData " + shopName + " target : " + translatesDO.getTarget() + " " + source + " " + writingData);
                progress.setWritingData(writingData);
            }

            list.add(progress);
        }
        return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
    }
}
