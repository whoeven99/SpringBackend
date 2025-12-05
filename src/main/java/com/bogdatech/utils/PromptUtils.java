package com.bogdatech.utils;

import com.bogdatech.entity.DO.GlossaryDO;

import java.util.Map;

public class PromptUtils {
    public static String GlossaryJsonPrompt(String target, String glossaryMapping,
                                            Map<Integer, String> glossaryTextMap) {
        String prompt = """
                You are a professional e-commerce website translation expert.
                Your task is to translate the provided texts according to the specified requirements and
                return the complete translation result.
                
                The target language for translation is:
                <TargetLanguage>
                {{TARGET_LANGUAGE}}
                </TargetLanguage>

                First, carefully read the following list of texts to be translated:
                <SourceLanguageList>
                {{SOURCE_LANGUAGE_LIST}}
                </SourceLanguageList>

                When translating, please strictly apply the following term rules to the translation results:
                <TermRules>
                {{TERM_RULES}}
                </TermRules>

                Additionally, adhere to these key translation rules:
                1. Do not translate key-values; only translate the content of the values.
                2. Do not translate emojis (keep them as they are in the source text).
                3. Do not translate variable names (e.g., {{aaa}}, {{aa.bbb}}, {% ccc %}, {% capture email_title %} etc.).
                4. Ensure the translation is natural and contextually appropriate for an e-commerce website.

                Please return your translation in the following JSON - like format:
                                {"1": "translated text1", "2": "translated text2",...}
                                Start your translation now.
                """;
        return prompt.replace("{{TARGET_LANGUAGE}}", ApiCodeUtils.getLanguageName(target))
                .replace("{{SOURCE_LANGUAGE_LIST}}", JsonUtils.objectToJson(glossaryTextMap))
                .replace("{{TERM_RULES}}", glossaryMapping);
    }

    public static String JsonPrompt(String target, Map<Integer, String> originalTextMap) {
        String prompt = """
                You are a professional translator who can accurately translate text from various languages.
                Your task is to translate the given list of texts into the specified target language.
                First, carefully read the following list of texts to be translated:
                <SourceLanguageList>
                {{SOURCE_LANGUAGE_LIST}}
                </SourceLanguageList>
                When translating, please follow these rules:
                1. Do not translate key - values; only translate the content of the values.
                2. Do not translate emojis.
                3. If a text is a variable name(e.g., {{aaa}}, {{aa.bbb}}, {% ccc %}, {% capture email_title %} etc.).,
                do not translate it.
                
                The target language for translation is:
                <TargetLanguage>
                {{TARGET_LANGUAGE}}
                </TargetLanguage>
                Please return your translation in the following JSON - like format:
                {"1": "translated text1", "2": "translated text2",...}
                Start your translation now.
                """;
        return prompt.replace("{{TARGET_LANGUAGE}}", ApiCodeUtils.getLanguageName(target))
                .replace("{{SOURCE_LANGUAGE_LIST}}", JsonUtils.objectToJson(originalTextMap));
    }

    public static String GlossarySinglePrompt(String targetLanguage, String text,
                                              String glossaryMapping) {
        String prompt = """
                You are a professional translator who can accurately translate text from various languages.
                Your task is to translate the given text into the specified target language.
                
                When translating, please follow these rules:
                1. Do not translate emojis.
                2. If a text is a variable name(e.g., {{aaa}}, {{aa.bbb}}, {% ccc %}, {% capture email_title %} etc.).,
                do not translate it.
                
                When translating, please strictly apply the following term rules to the translation results:
                <TermRules>
                {{TERM_RULES}}
                </TermRules>
                
                The target language for translation is:
                <TargetLanguage>
                {{TARGET_LANGUAGE}}
                </TargetLanguage>
                
                Please translate the following text:
                <SourceText>
                {{SOURCE_TEXT}}
                </SourceText>
                
                Start your translation now.
                """;
        return prompt.replace("{{TARGET_LANGUAGE}}", ApiCodeUtils.getLanguageName(targetLanguage))
                .replace("{{SOURCE_TEXT}}", text)
                .replace("{{TERM_RULES}}", glossaryMapping);
    }

    public static String SinglePrompt(String targetLanguage, String text) {
        String prompt = """
                You are a professional translator who can accurately translate text from various languages.
                Your task is to translate the given text into the specified target language.
                
                When translating, please follow these rules:
                1. Do not translate emojis.
                2. If a text is a variable name(e.g., {{aaa}}, {{aa.bbb}}, {% ccc %}, {% capture email_title %} etc.).,
                do not translate it.
                
                The target language for translation is:
                {{TARGET_LANGUAGE}}
                
                Please translate the following text:
                {{SOURCE_TEXT}}
                
                Start your translation now.
                """;
        return prompt.replace("{{TARGET_LANGUAGE}}", ApiCodeUtils.getLanguageName(targetLanguage))
                .replace("{{SOURCE_TEXT}}", text);
    }
}
