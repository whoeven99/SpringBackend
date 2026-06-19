package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenQuotaVO {
    private String shopName;
    /** 总可用额度（max_translations_month + chars），从DB查询 */
    private Integer maxToken;
    /** 已使用额度，扣除后从Redis读取 */
    private Integer usedToken;
    /** 剩余额度 = maxToken - usedToken，可能为负数 */
    private Integer remaining;
}
