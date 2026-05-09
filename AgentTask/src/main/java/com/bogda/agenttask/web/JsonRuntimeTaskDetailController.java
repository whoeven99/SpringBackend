package com.bogda.agenttask.web;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.service.logic.translate.TranslateV3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 从 BogdaApi {@code TranslateController} 迁出：便于仅部署 AgentTask（如 Render）时查询 json-runtime 任务详情。
 */
@RestController
@RequestMapping("/translate")
public class JsonRuntimeTaskDetailController {

    @Autowired
    private TranslateV3Service translateV3Service;

    /**
     * 查看 JSON runtime 任务：Cosmos 文档、Redis 进度、checkpoint 中 Blob 情况；可选预览。
     */
    @GetMapping("/v3/jsonRuntimeTaskDetail")
    public BaseResponse<Object> jsonRuntimeTaskDetail(@RequestParam String taskId,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) String redisPrefix,
            @RequestParam(required = false, defaultValue = "false") boolean includeBlobPreview,
            @RequestParam(required = false, defaultValue = "8192") int maxPreviewBytes) {
        return translateV3Service.getJsonRuntimeTaskDetail(taskId, shopName, redisPrefix, includeBlobPreview,
                maxPreviewBytes);
    }
}
