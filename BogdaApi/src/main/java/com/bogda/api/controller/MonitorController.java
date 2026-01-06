package com.bogda.api.controller;

import com.bogda.api.Service.ICharsOrdersService;
import com.bogda.api.Service.ITranslatesService;
import com.bogda.api.Service.ITranslationCounterService;
import com.bogda.api.Service.impl.TranslationCounterServiceImpl;
import com.bogda.api.entity.DO.CharsOrdersDO;
import com.bogda.api.entity.DO.TranslatesDO;
import com.bogda.api.entity.DO.TranslationCounterDO;
import com.bogda.api.logic.redis.ConfigRedisRepo;
import com.bogda.api.logic.redis.RedisStoppedRepository;
import com.bogda.api.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.api.logic.translate.TranslateV2Service;
import com.bogda.api.logic.translate.stragety.HtmlTranslateStrategyService;
import com.bogda.api.model.controller.response.ProgressResponse;
import com.bogda.api.repository.entity.InitialTaskV2DO;
import com.bogda.api.repository.repo.InitialTaskV2Repo;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.bogda.common.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogda.common.utils.LiquidHtmlTranslatorUtils.parseHtml;

@RestController
public class MonitorController {
    @Autowired
    private ICharsOrdersService charsOrdersService;
    @Autowired
    private ITranslatesService iTranslatesService;
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

    @Autowired
    private TranslateV2Service translateV2Service;

    @PostMapping("/promptTest")
    public Map<String, Object> promptTest(@RequestBody Map<String, Object> map) {
        return translateV2Service.testTranslate(map);
    }

    @PostMapping("htmlToJson")
    public Map<Integer, String> htmlToJson(@RequestBody Map<String, Object> map) {
        String value = map.get("html").toString();

        value = isHtmlEntity(value); //判断是否含有HTML实体,然后解码

        boolean hasHtmlTag = HtmlTranslateStrategyService.HTML_TAG_PATTERN.matcher(value).find();
        Document doc = parseHtml(value, "en", hasHtmlTag);

        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }

        Map<Integer, String> ans = new HashMap<>();
        // 生成json
        int index = 0;
        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty()) {
                ans.put(index, text);
                index++;
            }
        }
        return ans;
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
            if ("0".equals(taskMap.get("totalCount"))) {
                continue;
            }
            taskMap.put("task_type", initialTaskV2DO.getTaskType());
            taskMap.put("status", initialTaskV2DO.getStatus().toString());
            if (initialTaskV2DO.getStatus().equals(5) || initialTaskV2DO.getStatus().equals(4)) {
                taskMap.remove("lastUpdatedTime");
            }
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
