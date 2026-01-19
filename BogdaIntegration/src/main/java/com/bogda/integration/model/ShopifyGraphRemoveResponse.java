package com.bogda.service.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyGraphRemoveResponse {
    private TranslationsRemove translationsRemove;
    @Data
    public static class TranslationsRemove {
        private List<UserErrors> userErrors;
        private List<Translations> translations;

        @Data
        public static class UserErrors {
            private String message;
            private String field;
            private String code;
        }

        @Data
        public static class Translations {
            private String key;
            private String value;
        }

    }
}
