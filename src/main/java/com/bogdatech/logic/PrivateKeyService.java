package com.bogdatech.logic;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.entity.UserPrivateDO;
import com.bogdatech.integration.PrivateIntegration;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.bogdatech.constants.TranslateConstants.HAS_TRANSLATED;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;

@Component
public class PrivateKeyService {
    private final PrivateIntegration privateIntegration;
    private final UserPrivateService userPrivateService;
    private final IUserPrivateService iUserPrivateService;
    private final ITranslatesService iTranslatesService;
    @Autowired
    public PrivateKeyService(PrivateIntegration privateIntegration, UserPrivateService userPrivateService, IUserPrivateService iUserPrivateService, ITranslatesService iTranslatesService) {
        this.privateIntegration = privateIntegration;
        this.userPrivateService = userPrivateService;
        this.iUserPrivateService = iUserPrivateService;
        this.iTranslatesService = iTranslatesService;
    }

    //测试google调用
    public void test(String text, String source, String apiKey, String target) {
        String s = privateIntegration.translateByGoogle(text, source, apiKey, target);
        System.out.println("s = " + s);
    }

    //私有key翻译前的判断
    public BaseResponse<Object> judgePrivateKey(ClickTranslateRequest clickTranslateRequest) {
//        将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);

        //判断字符是否超限
        UserPrivateDO userPrivateDO = iUserPrivateService.selectOneByShopName(request.getShopName());
        Integer remainingChars = userPrivateDO.getAmount();
        Integer usedChars = userPrivateDO.getUsedAmount();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = iTranslatesService.readStatusInTranslatesByShopName(request);
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateSuccessResponse(HAS_TRANSLATED);
            }
        }

        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        iTranslatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        //私有key翻译
//        translateService.startTranslation(request, remainingChars, counter, usedChars);
        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }


}
