package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;

import java.util.List;
import java.util.Objects;

import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;

public class ModelUtils {
    //将前端传的宽泛的模块解析成具体的翻译模块，并输出
    public static List<String> translateModel(List<String> list){
        return list.stream()
                .map(TOKEN_MAP::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(TranslateResourceDTO::getResourceType)
                .toList();
    }
}
