package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.constants.TranslateConstants.MAX_LENGTH;
import static com.bogdatech.entity.DO.TranslateResourceDTO.PRODUCT_RESOURCES;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;

@Component
public class ListUtils {

    /**
     * 将List<String> 转化位 List<TranslateResourceDTO>
     * */
    public static List<TranslateResourceDTO> convert(List<String> list){
       List<TranslateResourceDTO> translateResourceDTOList = new ArrayList<>();
        for (String s : list
              ) {
            List<TranslateResourceDTO> translateResourceDTOList1 = TOKEN_MAP.get(s);
            translateResourceDTOList.addAll(translateResourceDTOList1);

        }
        return translateResourceDTOList;
    }

}
