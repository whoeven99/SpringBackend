package com.bogdatech.controller;

import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.impl.TranslationCounterServiceImpl;
import com.bogdatech.entity.DO.CharsOrdersDO;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.logic.redis.ConfigRedisRepo;
import com.bogdatech.logic.redis.RedisStoppedRepository;
import com.bogdatech.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogdatech.model.controller.response.ProgressResponse;
import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.repo.InitialTaskV2Repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MonitorController {
    @Autowired
    private ICharsOrdersService charsOrdersService;
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
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;
    @Autowired
    private RedisStoppedRepository redisStoppedRepository;
    @Autowired
    private TranslationCounterServiceImpl translationCounterServiceRepo;

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
        return map;
    }

    // For Monitor
    @GetMapping("/getProgressByShopName")
    public List<ProgressResponse.Progress> getProgressByShopName(@RequestParam String shopName) {
        return new ArrayList<>();
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

    @PostMapping("/todoBConfig")
    public Map<String, String> todoBConfig(@RequestBody Map<String, String> map) {
        String shopName = map.get("shopName");
        Integer addChars = map.get("addChars") != null ? Integer.parseInt(map.get("addChars")) : 0;

        TranslationCounterDO counterDO = translationCounterServiceRepo.readCharsByShopName(shopName);
        Integer chars = counterDO.getChars();

        translationCounterServiceRepo.updateCharsByShopNameWithoutCheck(shopName, addChars);

        counterDO = translationCounterServiceRepo.readCharsByShopName(shopName);
        Integer newChars = counterDO.getChars();

        Map<String, String> map2 = new HashMap<>();
        map2.put("oldChars", chars.toString());
        map2.put("addChars", addChars.toString());
        map2.put("newChars", newChars.toString());
        return map2;
    }

    @GetMapping("/monitorv2")
    public Map<String, Object> monitorv2(@RequestParam(required = false) String type, @RequestParam(required = false) Boolean includeFinish) {
        List<InitialTaskV2DO> initialList;
        if ("auto".equals(type)) {
            initialList = initialTaskV2Repo.selectByLastDaysAndType("auto", 1);
        } else if ("manual".equals(type)) {
            initialList = initialTaskV2Repo.selectByLastDaysAndType("manual", 3);
        } else {
            initialList = initialTaskV2Repo.selectByLastDaysAndType("auto", 1);
            initialList.addAll(initialTaskV2Repo.selectByLastDaysAndType("manual", 3));
        }
        initialList = initialList.stream()
                .filter(initialTaskV2DO -> (includeFinish != null && includeFinish) || !initialTaskV2DO.getStatus().equals(4))
                .toList();

        Map<String, Object> responseMap = new HashMap<>();
        for (InitialTaskV2DO initialTaskV2DO : initialList) {
            Map<String, String> taskMap = translateTaskMonitorV2RedisService.getAllByTaskId(initialTaskV2DO.getId());
            taskMap.put("task_type", initialTaskV2DO.getTaskType());
            taskMap.put("status", initialTaskV2DO.getStatus().toString());
            if (initialTaskV2DO.getStatus().equals(5)) {
                boolean isTokenLimit = redisStoppedRepository.isStoppedByTokenLimit(initialTaskV2DO.getShopName());
                if (isTokenLimit) {
                    taskMap.put("status", "6");
                }
            }
            taskMap.put("send_email", initialTaskV2DO.isSendEmail() ? "1" : "0");
            responseMap.put("" + initialTaskV2DO.getId(), taskMap);
        }

        return responseMap;
    }

}
