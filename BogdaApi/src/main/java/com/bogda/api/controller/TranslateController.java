package com.bogda.api.controller;

import com.bogda.common.entity.VO.ImageTranslateVO;
import com.bogda.common.controller.request.TargetListRequest;
import com.bogda.common.controller.request.TranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.Service.IUserTypeTokenService;
import com.bogda.service.logic.TranslateService;
import com.bogda.service.logic.UserTypeTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogda.common.enums.ErrorEnum.SQL_DELETE_ERROR;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    @Autowired
    private TranslateService translateService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private UserTypeTokenService userTypeTokensService;

    @PostMapping("/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translatesService.insertLanguageStatus(request);
    }

    @PostMapping("/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        TraceReporterHolder.report("TranslateController.deleteFromTranslates", "deleteFromTranslates 用户删除翻译语言： " + request);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
    }

    @PostMapping("/insertTargets")
    public void insertTargets(@RequestBody TargetListRequest request) {
        List<String> targetList = request.getTargetList();
        TranslateRequest translateRequest = targetListRequestToTranslateRequest(request);
        if (!targetList.isEmpty()) {
            translateRequest.setTarget(targetList.get(0));
            userTypeTokensService.getUserInitToken(translateRequest);
            for (String target : targetList) {
                TranslateRequest request1 = new TranslateRequest(
                        0,
                        request.getShopName(),
                        request.getAccessToken(),
                        request.getSource(),
                        target,
                        null);
                translatesService.insertLanguageStatus(request1);
                int idByShopNameAndTarget = translateService.getIdByShopNameAndTargetAndSource(
                        request1.getShopName(), request1.getTarget(), request1.getSource());
                userTypeTokenService.insertTypeInfo(request1, idByShopNameAndTarget);
            }
        } else {
            translateRequest.setTarget("asdf");
            userTypeTokensService.getUserInitToken(translateRequest);
        }
    }

    @PutMapping("/imageTranslate")
    public BaseResponse<Object> imageTranslate(
            @RequestParam String shopName,
            @RequestBody ImageTranslateVO imageTranslateVO) {
        String targetPic = translateService.imageTranslate(
                imageTranslateVO.getSourceCode(),
                imageTranslateVO.getTargetCode(),
                imageTranslateVO.getImageUrl(),
                shopName,
                imageTranslateVO.getAccessToken());

        if (targetPic != null) {
            return new BaseResponse<>().CreateSuccessResponse(targetPic);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    private TranslateRequest targetListRequestToTranslateRequest(TargetListRequest targetListRequest) {
        TranslateRequest translateRequest = new TranslateRequest();
        translateRequest.setAccessToken(targetListRequest.getAccessToken());
        translateRequest.setShopName(targetListRequest.getShopName());
        translateRequest.setSource(targetListRequest.getSource());
        return translateRequest;
    }
}
