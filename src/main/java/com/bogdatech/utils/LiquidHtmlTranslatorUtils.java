package com.bogdatech.utils;


import com.bogdatech.entity.DTO.FullAttributeSnapshotDTO;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class LiquidHtmlTranslatorUtils {

    // 不翻译的URL模式
    public static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+|www\\.[^\\s<>\"]+");
    // 自定义变量模式：%{ order.name } 等
    public static final Pattern CUSTOM_VAR_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}|\\{\\w+\\}|%\\{[^}]+\\}|\\{%(.*?)%\\}|\\[[^\\]]+\\]");
    // 纯符号模式：匹配单独的 -、×、+、= 等符号（不含字母数字）
    public static final Pattern SYMBOL_PATTERN = Pattern.compile("^[\\+=×*/|!@#$%^&()_]+$", Pattern.MULTILINE);
    // 判断是否有 <html> 标签的模式
    public static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*html\\s*", Pattern.CASE_INSENSITIVE);
    // 从配置文件读取不翻译的标签，默认为 "style,img,script"
    public final static Set<String> NO_TRANSLATE_TAGS = Set.of("script", "style", "meta", "svg", "canvas", "link");
    private static final String STYLE_TEXT = "data-id";

    /**
     * 清理文本格式：去除多余的换行符和空格
     *
     * @param text 输入文本
     * @return 清理后的文本
     */
    public static String cleanTextFormat(String text) {
        // 去除首尾的换行符和多余空格，保留内部有效内容
//        return text.trim().replaceAll("[\\r\\n]+", "").replaceAll("\\s+", " ");
        return text.replaceAll("[\\r\\n]+", "");
    }

    // 辅助类用于保存匹配范围
    public static class MatchRange {
        public int start;
        public int end;
        public String content;

        public MatchRange(int start, int end, String content) {
            this.start = start;
            this.end = end;
            this.content = content;
        }
    }

    //判断是否含有HTML实体
    public static String isHtmlEntity(String text) {
        int i = 0;
        while (!text.equals(StringEscapeUtils.unescapeHtml4(text))) {
            // 如果有 HTML 实体，则解码
            text = StringEscapeUtils.unescapeHtml4(text);
            i++;
            if (i > 3) {
                return text;
            }
        }

        // 最终结果（无 HTML 实体后直接输出）
        return text;
    }

    /**
     * 对大模型翻译后的数据进行处理并返回
     */
    public static String processTranslationResult(String translatedText, Map<String, FullAttributeSnapshotDTO> attrMap, boolean hasHtmlTag) {
        Document translatedDoc;
        if (hasHtmlTag) {
            translatedDoc = Jsoup.parse(translatedText);
        } else {
            translatedDoc = Jsoup.parseBodyFragment(translatedText);
        }
        // 第五步：将样式还原到翻译后的 HTML 中
        restoreFullAttributes(translatedDoc, attrMap);
        return translatedDoc.body().html();
    }

    public static int countLeadingSpaces(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public static int countTrailingSpaces(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }


    /**
     * 给所有含保留属性的元素打上唯一 data-id，并保存这些属性
     *
     * @param doc HTML 文档
     * @return 属性快照映射表
     */
    public static Map<String, FullAttributeSnapshotDTO> tagElementsAndSaveFullAttributes(Document doc) {
        Map<String, FullAttributeSnapshotDTO> attrMap = new LinkedHashMap<>();
        AtomicInteger idGen = new AtomicInteger(0);

        for (Element el : doc.getAllElements()) {
            // 排除我们自己用于标记的属性
            List<Attribute> attrList = el.attributes().asList().stream()
                    .filter(attr -> !attr.getKey().equals(STYLE_TEXT))
                    .toList();

            // 拼接为完整属性字符串
            if (!attrList.isEmpty()) {
                String id = "attr-" + idGen.getAndIncrement();
                el.attr(STYLE_TEXT, id); // 添加唯一标识

                String fullAttrs = attrList.stream()
                        .map(attr -> attr.getKey() + "=\"" + attr.getValue() + "\"")
                        .collect(Collectors.joining(" "));

                FullAttributeSnapshotDTO dto = new FullAttributeSnapshotDTO();
                dto.fullAttributes = fullAttrs;
                attrMap.put(id, dto);
            }
        }

        return attrMap;
    }


    /**
     * 移除所有保存的属性（包括 style, class, data-* 等），只保留 data-id
     */
    public static void removeAllAttributesExceptMarker(Document doc) {
        for (Element el : doc.getAllElements()) {
            List<String> attrKeys = el.attributes().asList().stream()
                    .map(Attribute::getKey)
                    .filter(key -> !key.equals(STYLE_TEXT))
                    .toList();

            attrKeys.forEach(el::removeAttr);
        }
    }

    /**
     * 还原之前保存的属性信息
     */
    public static void restoreFullAttributes(Document doc, Map<String, FullAttributeSnapshotDTO> attrMap) {
        for (Element el : doc.select("[" + STYLE_TEXT + "]")) {
            String id = el.attr(STYLE_TEXT);
            FullAttributeSnapshotDTO snapshot = attrMap.get(id);

            if (snapshot != null) {
                // 安全处理 null
                String fullAttrs = snapshot.fullAttributes != null ? snapshot.fullAttributes.trim() : "";
                String innerHtml = el.html() != null ? el.html() : "";

                // 构造 fakeOuterHtml
                String fakeOuterHtml = "<" + el.tagName() + " " + fullAttrs + ">" + innerHtml + "</" + el.tagName() + ">";

                // 解析并安全获取第一个子节点
                Element body = Jsoup.parseBodyFragment(fakeOuterHtml).body();
                if (body.childrenSize() > 0) {
                    Element restored = body.child(0);

                    // 替换所有属性
                    el.clearAttributes();
                    restored.attributes().forEach(attr -> el.attr(attr.getKey(), attr.getValue()));
                } else {
                    // 记录异常情况，方便排查
                    appInsights.trackTrace(
                            "[restoreFullAttributes] 无法解析出有效子节点, id=" + id +
                                    ", tag=" + el.tagName() +
                                    ", fakeOuterHtml=" + fakeOuterHtml
                    );
                }
            }

            // 可选：删除还原标记
            el.removeAttr(STYLE_TEXT);
        }
    }

    /**
     * 解析html，根据html标签，选择不同的解析方式， 将prettyPrint设置为false
     */
    public static Document parseHtml(String html, String target, boolean hasHtmlTag) {
        Document doc;
        if (hasHtmlTag) {
            doc = Jsoup.parse(html);
            // 获取 <html> 元素并修改 lang 属性
            Element htmlTag = doc.selectFirst("html");
            if (htmlTag != null) {
                htmlTag.attr("lang", target);
            }
        } else {
            doc = Jsoup.parseBodyFragment(html);
        }
        doc.outputSettings().prettyPrint(false);
        return doc;
    }
}
