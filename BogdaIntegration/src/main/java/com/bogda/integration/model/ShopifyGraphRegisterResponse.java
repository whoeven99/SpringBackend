package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyGraphRegisterResponse {
    private TranslationsRegister translationsRegister;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TranslationsRegister {
        private List<UserError> userErrors;
        private List<Translation> translations;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class UserError {
            private String message;
            private List<String> field;
            private String code;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Translation {
            private String key;
            private String value;
            private String locale;
        }
    }
}
