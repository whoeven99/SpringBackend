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
    // 创建一个静态的 ArrayList 来存储 TranslationResource 对象
    public static final List<TranslateResourceDTO> translationResources = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO("ARTICLE", "2", "en", "zh"),
            new TranslateResourceDTO("BLOG", "2", "en", "ja"),
            new TranslateResourceDTO("COLLECTION", "2", "en", "zh")

    ));

    private String resourceType;
    private String first;
    private String source;
    private String target;
}
