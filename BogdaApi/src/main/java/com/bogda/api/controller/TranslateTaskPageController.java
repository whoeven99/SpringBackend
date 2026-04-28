package com.bogda.api.controller;

import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.StringUtils;
import com.bogda.repository.container.TranslateTaskV3DO;
import com.bogda.repository.repo.translate.TranslateTaskV3BlobRepo;
import com.bogda.repository.repo.translate.TranslateTaskV3CosmosRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/v3")
public class TranslateTaskPageController {
    private static final List<Integer> ALL_STATUSES = List.of(0, 1, 2, 3, 4);

    @Autowired
    private TranslateTaskV3CosmosRepo translateTaskV3CosmosRepo;
    @Autowired
    private TranslateTaskV3BlobRepo translateTaskV3BlobRepo;

    @GetMapping("/tasks")
    public String taskList(@RequestParam(required = false) Integer status, Model model) {
        List<TranslateTaskV3DO> tasks = new ArrayList<>();
        if (status != null) {
            tasks.addAll(translateTaskV3CosmosRepo.listByStatus(status));
        } else {
            for (Integer each : ALL_STATUSES) {
                tasks.addAll(translateTaskV3CosmosRepo.listByStatus(each));
            }
        }
        tasks.sort(Comparator.comparing(TranslateTaskV3DO::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("tasks", tasks);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statusOptions", buildStatusOptions());
        model.addAttribute("total", tasks.size());
        return "translate-v3-task-list";
    }

    @GetMapping("/task")
    public String taskDetail(@RequestParam String id,
                             @RequestParam String shopName,
                             @RequestParam(required = false) Integer status,
                             Model model) {
        TranslateTaskV3DO task = translateTaskV3CosmosRepo.getById(id, shopName);
        if (task != null) {
            try {
                String taskId = task.getId();
                Map<String, Object> totalAiScoreReport = readJsonMap(blobPath(taskId, "qa/ai-score.json"));
                model.addAttribute("totalAiScoreReport", totalAiScoreReport);
                model.addAttribute("totalAiModuleScores", extractModuleScores(totalAiScoreReport));
                model.addAttribute("moduleAiReports", buildModuleAiReports(task));
            } catch (Exception e) {
                model.addAttribute("totalAiScoreReport", null);
                model.addAttribute("totalAiModuleScores", new ArrayList<>());
                model.addAttribute("moduleAiReports", new ArrayList<>());
                model.addAttribute("detailError", "读取质量报告失败: " + e.getMessage());
            }
        }
        model.addAttribute("task", task);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statusOptions", buildStatusOptions());
        return "translate-v3-task-detail";
    }

    @GetMapping("/task/module-ai-report")
    public String moduleAiReport(@RequestParam String id,
                                 @RequestParam String shopName,
                                 @RequestParam String module,
                                 @RequestParam(required = false) Integer status,
                                 Model model) {
        TranslateTaskV3DO task = translateTaskV3CosmosRepo.getById(id, shopName);
        model.addAttribute("task", task);
        model.addAttribute("module", module);
        model.addAttribute("selectedStatus", status);

        if (task == null) {
            return "translate-v3-module-ai-report";
        }

        String moduleBlobPath = blobPath(id, "chunks/" + module + "/ai-score.json");
        Map<String, Object> moduleAiReport = readJsonMap(moduleBlobPath);
        Map<String, Object> result = moduleAiReport == null
                ? new LinkedHashMap<>()
                : toStringObjectMap(moduleAiReport.get("result"));

        model.addAttribute("moduleBlobPath", moduleBlobPath);
        model.addAttribute("moduleAiReport", moduleAiReport);
        model.addAttribute("moduleResult", result);
        model.addAttribute("moduleDimensions", toStringObjectMap(result.get("dimensions")));
        model.addAttribute("moduleIssues", toObjectList(result.get("issues")));
        model.addAttribute("moduleRawJson", moduleAiReport == null ? null : JsonUtils.objectToJson(moduleAiReport));
        return "translate-v3-module-ai-report";
    }

    private List<Map<String, Object>> buildModuleAiReports(TranslateTaskV3DO task) {
        List<Map<String, Object>> moduleReports = new ArrayList<>();
        if (task == null || StringUtils.isEmpty(task.getId()) || StringUtils.isEmpty(task.getModuleList())) {
            return moduleReports;
        }

        List<String> modules = JsonUtils.jsonToObject(task.getModuleList(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
        });
        if (modules == null || modules.isEmpty()) {
            return moduleReports;
        }

        for (String module : modules) {
            String path = blobPath(task.getId(), "chunks/" + module + "/ai-score.json");
            Map<String, Object> report = readJsonMap(path);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module", module);
            item.put("blobPath", path);

            if (report == null || report.isEmpty()) {
                item.put("status", "NOT_FOUND");
                item.put("score", null);
                item.put("sampleCount", 0);
                item.put("summary", null);
                moduleReports.add(item);
                continue;
            }

            item.put("generatedAt", report.get("generatedAt"));
            Object resultObj = report.get("result");
            Map<String, Object> result = toStringObjectMap(resultObj);
            item.put("status", result.get("status"));
            item.put("score", result.get("score"));
            item.put("sampleCount", result.get("sampleCount"));
            item.put("summary", result.get("summary"));
            item.put("tokenUsed", result.get("tokenUsed"));
            moduleReports.add(item);
        }
        return moduleReports;
    }

    private List<Map<String, Object>> extractModuleScores(Map<String, Object> totalAiScoreReport) {
        if (totalAiScoreReport == null || totalAiScoreReport.isEmpty()) {
            return new ArrayList<>();
        }
        Object moduleScores = totalAiScoreReport.get("moduleScores");
        if (moduleScores instanceof List<?>) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : (List<?>) moduleScores) {
                Map<String, Object> map = toStringObjectMap(item);
                if (!map.isEmpty()) {
                    result.add(map);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private Map<String, Object> readJsonMap(String blobPath) {
        String raw;
        try {
            raw = translateTaskV3BlobRepo.readText(blobPath);
        } catch (Exception ignored) {
            return null;
        }
        if (StringUtils.isEmpty(raw) || "null".equals(raw)) {
            return null;
        }
        try {
            return JsonUtils.jsonToObject(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private String blobPath(String taskId, String tail) {
        return "tasks/" + taskId + "/" + tail;
    }

    private Map<String, Object> toStringObjectMap(Object input) {
        if (!(input instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private List<Object> toObjectList(Object input) {
        if (!(input instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    private Map<Integer, String> buildStatusOptions() {
        Map<Integer, String> options = new LinkedHashMap<>();
        options.put(0, "0 - INIT_PENDING");
        options.put(1, "1 - TRANSLATING");
        options.put(2, "2 - DONE");
        options.put(3, "3 - STOPPED_TOKEN_LIMIT");
        options.put(4, "4 - STOPPED_PRIMARY_LOCALE");
        return options;
    }
}
