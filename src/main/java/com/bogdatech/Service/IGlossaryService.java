package com.bogdatech.Service;

import com.bogdatech.entity.DO.GlossaryDO;

public interface IGlossaryService {
    Boolean insertGlossaryInfo(GlossaryDO glossaryDO);

    boolean deleteGlossaryById(GlossaryDO glossaryDO);

    GlossaryDO[] getGlossaryByShopName(String shopName);

    boolean updateGlossaryInfoById(GlossaryDO glossaryDO);

    GlossaryDO getSingleGlossaryByShopNameAndSource(String shopName, String sourceText, String rangeCode);
}
