package com.bogdatech.logic;

import org.springframework.stereotype.Component;

@Component
public class PurchaseService {

    // 付费表单推荐购买字符数（根据商店总字符数推荐）
    public int recommendPurchaseAmount() {
        return 1000000;
    }
}
