package com.bogda.api.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.nodes.TextNode;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterHtmlTransactionRequest {
    private String value;           // 待翻译的原始文本
    private String translatedValue; // 翻译后的文本
    private TextNode nodeReference; // 原始文本节点的引用，用于回填
}
