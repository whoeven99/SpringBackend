package com.bogdatech.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateResourceDTO {
    // 创建一个静态的 ArrayList 来存储 TranslateResourceDTO 对象
    public static final List<TranslateResourceDTO> translationResources = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO("ARTICLE", "2", "",""),
            new TranslateResourceDTO("BLOG", "2", "",""),
            new TranslateResourceDTO("COLLECTION", "2", "", "")
            //TODO 还有其他类型需要添加
    ));

    private String resourceType;
    private String first;
    private String target;
    private String after;
}
