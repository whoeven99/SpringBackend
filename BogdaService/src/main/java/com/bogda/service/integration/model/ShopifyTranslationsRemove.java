package com.bogda.service.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyTranslationsRemove {
    private String resourceId;
    private String[] locales;
    private String[] translationKeys;

    public void add(String locale, String translationKey) {
        // 如果 locales 已存在该 locale，直接返回（不追加）
        if (this.locales != null && contains(this.locales, locale)) {
            return;
        }

        // 初始化 locales
        if (this.locales == null) {
            this.locales = new String[]{locale};
        } else {
            this.locales = append(this.locales, locale);
        }

        // 初始化 translationKeys
        if (this.translationKeys == null) {
            this.translationKeys = new String[]{translationKey};
        } else {
            this.translationKeys = append(this.translationKeys, translationKey);
        }
    }

    private String[] append(String[] array, String value) {
        String[] newArray = Arrays.copyOf(array, array.length + 1);
        newArray[array.length] = value;
        return newArray;
    }

    private boolean contains(String[] array, String value) {
        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
