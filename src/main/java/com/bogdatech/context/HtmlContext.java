package com.bogdatech.context;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class HtmlContext extends TranslateContext{
    private String content;

    private Map<Integer, String> originalTextMap = new HashMap<>();
    private Document doc;
    boolean hasHtmlTag;
    private Map<Integer, TextNode> nodeMap = new HashMap<>();

    private Map<Integer, String> translatedTextMap;
    private String replaceBackContent;
}
