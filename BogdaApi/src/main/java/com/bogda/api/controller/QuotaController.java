package com.bogda.api.controller;

import com.bogda.common.entity.VO.TokenQuotaVO;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.service.Service.impl.TranslationCounterServiceImpl;
import com.bogda.common.entity.DO.TranslationCounterDO;
import com.bogda.service.logic.token.UserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 外部额度查询与扣减 API。
 * <p>
 * 查询额度：直接查 DB。
 * 扣除额度：先扣 Redis，再同步到 DB，返回最新剩余额度（可为负数）。
 */
@RestController
@RequestMapping("/quota")
public class QuotaController {

    @Autowired
    private TranslationCounterServiceImpl translationCounterService;

    @Autowired
    private UserTokenService userTokenService;

    /**
     * 查询用户额度（直接查询DB）。
     *
     * @param shopName 店铺名
     * @return TokenQuotaVO 包含 maxToken / usedToken / remaining
     */
    @GetMapping("/query")
    public BaseResponse<TokenQuotaVO> queryQuota(@RequestParam String shopName) {
        // 从DB获取最大额度
        Integer maxToken = translationCounterService.getMaxCharsByShopName(shopName);
        // 从DB获取已使用额度
        TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
        Integer usedToken = (counterDO != null) ? counterDO.getUsedChars() : 0;

        int max = (maxToken != null) ? maxToken : 0;
        int remaining = max - usedToken;

        TokenQuotaVO vo = new TokenQuotaVO(shopName, max, usedToken, remaining);
        return BaseResponse.SuccessResponse(vo);
    }

    /**
     * 扣除用户额度。
     * 先扣 Redis（实时），再同步到 DB，返回最新剩余额度。
     * remaining 可能为负数（允许超额消费）。
     *
     * @param shopName 店铺名
     * @param tokens   本次消耗的 token 数
     * @return TokenQuotaVO 包含扣减后的 maxToken / usedToken / remaining
     */
    @PostMapping("/deduct")
    public BaseResponse<TokenQuotaVO> deductQuota(@RequestParam String shopName,
                                                   @RequestParam Integer tokens) {
        if (tokens == null || tokens <= 0) {
            return BaseResponse.FailedResponse("tokens must be positive");
        }

        TokenQuotaVO result = userTokenService.deductAndGetRemaining(shopName, tokens);
        return BaseResponse.SuccessResponse(result);
    }
}
