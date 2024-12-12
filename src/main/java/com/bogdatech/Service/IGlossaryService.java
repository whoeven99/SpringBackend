package com.bogdatech.Service;

import com.bogdatech.entity.GlossaryDO;

public interface IGlossaryService {
    Boolean insertGlossaryInfo(GlossaryDO glossaryDO);

    boolean deleteGlossaryById(GlossaryDO glossaryDO);

    GlossaryDO[] getGlossaryByShopName(String shopName);

    boolean updateGlossaryInfoById(GlossaryDO glossaryDO);
}
