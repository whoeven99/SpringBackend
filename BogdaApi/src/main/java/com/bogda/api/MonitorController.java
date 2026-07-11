package com.bogda.api;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogda.common.utils.LiquidHtmlTranslatorUtils.*;

@RestController
public class MonitorController {

    private static final String SERVER_START_TIME = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    @GetMapping("")
    public Map<String, String> getVersion() {
        Map<String, String> info = new HashMap<>();
        info.put("startTime", SERVER_START_TIME);
        info.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        info.put("status", "UP");
        return info;
    }

    @PostMapping("htmlToJson")
    public Map<Integer, String> htmlToJson(@RequestBody Map<String, Object> map) {
        String value = map.get("html").toString();

        value = isHtmlEntity(value);

        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(value).find();
        Document doc = parseHtml(value, "en", hasHtmlTag);

        List<TextNode> nodes = new ArrayList<>();
        for (Element element : doc.getAllElements()) {
            nodes.addAll(element.textNodes());
        }

        Map<Integer, String> ans = new HashMap<>();
        int index = 0;
        for (TextNode node : nodes) {
            String text = node.text().trim();
            if (!text.isEmpty()) {
                ans.put(index, text);
                index++;
            }
        }
        return ans;
    }
}
