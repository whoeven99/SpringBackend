package com.bogdatech.controller;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.TranslationCounterDO;
import com.bogdatech.logic.PrivateKeyService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.constants.TranslateConstants.HAS_TRANSLATED;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    private final PrivateKeyService privateKeyService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    @Autowired
    public PrivateKeyController(PrivateKeyService privateKeyService, ITranslationCounterService translationCounterService, ITranslatesService translatesService) {
        this.privateKeyService = privateKeyService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
    }


    //用于测试google是否可以调用
    //后面再加上对openai的调用
    @PostMapping("/test")
    public void test(String text, String source, String apiKey, String target) {
        privateKeyService.test(text, source, apiKey, target);
    }

    //用户通过私有key翻译
    @PostMapping("/translate")
    public BaseResponse<Object> translate(@RequestBody ClickTranslateRequest clickTranslateRequest) {
        //将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);

        //判断字符是否超限
//        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
//        Integer remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request);
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateSuccessResponse(HAS_TRANSLATED);
            }
        }
        //TODO： 单独的额度限制
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        //私有key翻译
//        translateService.startTranslation(request, remainingChars, counter, usedChars);
        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }
}
