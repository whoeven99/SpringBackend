package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.*;
import com.bogdatech.logic.redis.ConfigRedisRepo;
import com.bogdatech.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogdatech.logic.translate.TranslateProgressService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.response.ProgressResponse;
import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.repo.InitialTaskV2Repo;
import com.bogdatech.repository.repo.TranslateTaskV2Repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class MonitorController {

    @Autowired
    private ICharsOrdersService charsOrdersService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private ConfigRedisRepo configRedisRepo;
    @Autowired
    private InitialTaskV2Repo initialTaskV2Repo;
    @Autowired
    private TranslateTaskV2Repo translateTaskV2Repo;
    @Autowired
    private TranslateProgressService translateProgressService;
    @Autowired
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    @GetMapping("/getTable")
    public Map<String, Object> getTable(@RequestParam String shopName) {
        List<CharsOrdersDO> list = charsOrdersService.getCharsOrdersDoByShopName(shopName);
        Map<String, Object> map = new HashMap<>();
        map.put("CharsOrder", list);

        // 获取Translates表数据
        List<TranslatesDO> translatesDOS = iTranslatesService.listTranslatesDOByShopName(shopName);
        map.put("Translates", translatesDOS);

        // 获取task表准备翻译和正在翻译的数据
        List<TranslateTasksDO> translateTasksDOS = translateTasksService.listTranslateStatus2And0TasksByShopName(shopName);
        map.put("TranslateTasks", translateTasksDOS);

        // 获取用户额度表数据
        TranslationCounterDO translationCounterDO = translationCounterService.getTranslationCounterByShopName(shopName);
        map.put("TranslationCounter", new ArrayList<TranslationCounterDO>() {{
            add(translationCounterDO);
        }});

        // 获取initial表的数据
        List<InitialTranslateTasksDO> initialTranslateTasksDOS = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getShopName, shopName)
                        .eq(InitialTranslateTasksDO::isDeleted, false));
        map.put("InitialTranslateTasks", initialTranslateTasksDOS);

        return map;
    }

    // For Monitor
    @GetMapping("/getProgressByShopName")
    public List<ProgressResponse.Progress> getProgressByShopName(@RequestParam String shopName) {
        List<InitialTranslateTasksDO> initialTranslateTasksDOS = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getShopName, shopName)
                        .eq(InitialTranslateTasksDO::isDeleted, false)
                        .orderByAsc(InitialTranslateTasksDO::getCreatedAt));
        if (initialTranslateTasksDOS.isEmpty()) {
            return new ArrayList<>();
        }

        return translateProgressService.getAllProgressData(shopName, initialTranslateTasksDOS.get(0).getSource()).getResponse().getList();
    }

    @GetMapping("/bogdaconfig")
    public Map<String, String> config() {
        return configRedisRepo.getAllConfigs();
    }

    @PutMapping("/bogdaconfig")
    public Map<String, String> config(@RequestParam String key, @RequestParam String value) {
        configRedisRepo.setConfig(key, value);
        return configRedisRepo.getAllConfigs();
    }

    @DeleteMapping("/bogdaconfig")
    public Map<String, String> config(@RequestParam String key) {
        configRedisRepo.delConfig(key);
        return configRedisRepo.getAllConfigs();
    }

    @GetMapping("/monitorv2")
    public Map<String, Object> monitorv2() {
        // 获取过去24小时以内所有的任务
        List<InitialTaskV2DO> initialList = initialTaskV2Repo.selectByLast24Hours();

        Map<String, Object> responseMap = new HashMap<>();
        for (InitialTaskV2DO initialTaskV2DO : initialList) {
            Map<String, String> taskMap =  translateTaskMonitorV2RedisService.getAllByTaskId(initialTaskV2DO.getId());
            responseMap.put("" + initialTaskV2DO.getId(), taskMap);
        }

        return responseMap;
    }

}
