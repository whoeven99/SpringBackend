package com.bogda.service.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TranslationQualityEvaluation {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationQualityEvaluation.class);

    // 针对翻译结束的任务，进行翻译质量的评估
    // 定时演示（每分钟运行一次），实际使用可替换为合适的触发条件
    @Scheduled(fixedDelay = 60000)
    public void evaluateTranslationQuality() {
        // 演示用例
        String value = "Hello, this is a sample sentence with placeholder {0} and a name Alice.";
        String translatedValue = "你好，这是一个示例句子，包含占位符 {0} 和名字 Alice。";

        EvaluationResult result = evaluate(value, translatedValue);
        LOGGER.info("Translation evaluation result: {}", result);
    }

    // 对单个翻译对进行评估，返回包含分数与诊断理由的结果对象
    public EvaluationResult evaluate(String source, String translated) {
        List<String> reasons = new ArrayList<>();

        if (source == null || translated == null) {
            reasons.add("source 或 translated 为 null");
            return new EvaluationResult(0, "POOR", reasons);
        }

        String s = source.trim();
        String t = translated.trim();

        if (s.isEmpty() || t.isEmpty()) {
            reasons.add("源文本或译文为空");
            return new EvaluationResult(0, "POOR", reasons);
        }

        int sLen = s.length();
        int tLen = t.length();

        // 长度比
        double lengthRatio = (double) tLen / sLen;
        if (lengthRatio < 0.5 || lengthRatio > 2.0) {
            reasons.add(String.format("长度异常: 源=%d 译=%d 比率=%.2f", sLen, tLen, lengthRatio));
        }

        // 编辑距离（归一化相似度）
        int lev = levenshtein(s, t);
        int maxLen = Math.max(sLen, tLen);
        double similarity = 1.0 - (double) lev / maxLen; // maxLen 一定大于 0

        // 共享词比例（粗略用于检测未翻译或只替换少量词）
        double sharedWordRatio = sharedWordRatio(s, t);

        // 占位符保留检查
        List<String> placeholders = extractPlaceholders(s);
        Set<String> placeholdersInT = new HashSet<>(extractPlaceholders(t));
        boolean placeholdersMismatch = !placeholders.isEmpty() && !placeholdersInT.containsAll(placeholders);
        if (placeholdersMismatch) {
            reasons.add("占位符未被正确保留: " + placeholders);
        }

        // 相似但语种不同的判断比较困难，这里用高相似度+共享词多作为可能未翻译的信号
        if (similarity > 0.80) {
            reasons.add(String.format("高文本相似度(%.2f)，可能未翻译或直译保留大量源文本", similarity));
        }
        if (sharedWordRatio > 0.6) {
            reasons.add(String.format("共享词过多(%.2f)，可能未翻译或只替换少量词", sharedWordRatio));
        }

        // 计算综合分数：基线 50，依赖各因素加减分
        double score = 50.0;

        // 长度合理给予加分，否则扣分
        if (lengthRatio >= 0.8 && lengthRatio <= 1.25) score += 10;
        else score -= 15;

        // 编辑距离低（相似）则可能未翻译 -> 扣分
        if (similarity > 0.85) score -= 40;
        else if (similarity > 0.6) score -= 10;
        else score += 10; // 不相似通常是被翻译了

        // 共享词较多扣分
        if (sharedWordRatio > 0.6) score -= 30;
        else if (sharedWordRatio > 0.3) score -= 10;
        else score += 5;

        // 占位符未保留重扣
        if (placeholdersMismatch) score -= 30;
        else if (!placeholders.isEmpty()) score += 5;

        // 最终归一化到 0-100
        int finalScore = (int) Math.max(0, Math.min(100, Math.round(score)));

        String label;
        if (finalScore >= 75) label = "GOOD";
        else if (finalScore >= 50) label = "FAIR";
        else label = "POOR";

        // 简要添加诊断信息
        String diag = String.format(
                "最终得分=%d 分类=%s 长度比=%.2f 相似度=%.2f 共享词比=%.2f",
                finalScore, label, lengthRatio, similarity, sharedWordRatio);
        reasons.add(diag);

        return new EvaluationResult(finalScore, label, reasons);
    }

    private static List<String> extractPlaceholders(String s) {
        List<String> ret = new ArrayList<>();
        if (s == null) return ret;
        // 常见占位符样式：{0}, {name}, %s, %d, ${name}
        Pattern p = Pattern.compile("(\\{[^}]+\\}|%[sd]|%\\d+|\\$\\{[^}]+\\}");
        Matcher m = p.matcher(s);
        while (m.find()) {
            ret.add(m.group());
        }
        return ret;
    }

    private static double sharedWordRatio(String s, String t) {
        Set<String> sTokens = tokenizeUnique(s);
        Set<String> tTokens = tokenizeUnique(t);
        if (sTokens.isEmpty()) return 0.0;
        int shared = 0;
        for (String token : sTokens) {
            if (tTokens.contains(token)) shared++;
        }
        return (double) shared / sTokens.size();
    }

    private static Set<String> tokenizeUnique(String s) {
        if (s == null) return Collections.emptySet();
        String[] parts = s.toLowerCase().split("\\s+|[\\p{Punct}]+");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            if (p == null) continue;
            String w = p.trim();
            if (!w.isEmpty()) set.add(w);
        }
        return set;
    }

    // 经典 Levenshtein 编辑距离实现
    private static int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    public static class EvaluationResult {
        public final int score; // 0-100
        public final String label; // GOOD/FAIR/POOR
        public final List<String> reasons;

        public EvaluationResult(int score, String label, List<String> reasons) {
            this.score = score;
            this.label = label;
            this.reasons = reasons == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(reasons));
        }

        @Override
        public String toString() {
            String parts = "score=" + score + ", label='" + label + "'";
            return "EvaluationResult{" + parts + ", reasons=" + reasons + '}';
        }
    }
}
