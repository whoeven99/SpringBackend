package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;

import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;

public class ModelUtils {

    //将前端传的宽泛的模块解析成具体的翻译模块，并输出
    public static List<String> translateModel(List<String> list){
        List<TranslateResourceDTO> translateList = new ArrayList<>();
        for (String model: list
             ) {
            List<TranslateResourceDTO> translateResourceList = TOKEN_MAP.get(model);
            translateList.addAll(translateResourceList);
        }
        List<String> translateModelList = new ArrayList<>();
        for (TranslateResourceDTO resourceDTO: translateList){
            translateModelList.add(resourceDTO.getResourceType());
        }
        return translateModelList;
    }

}
