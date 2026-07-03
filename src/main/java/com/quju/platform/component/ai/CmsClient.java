package com.quju.platform.component.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 双层内容安全审核引擎
 *
 * Layer 1 — DFA 敏感词过滤器（快速，O(n)，无外部依赖）
 *   高分(≥8)直接驳回，低分(<2)直接放行，中间分转入 LLM
 *
 * Layer 2 — LLM 深度审核（调用 SiliconFlow DeepSeek-V3.2）
 *   语义理解判断，未配置或失败时返回 uncertain，由上层转人工审核
 *
 * 判定策略：
 *   DFA ≥ 8          → "violation"（即时驳回）
 *   DFA < 2          → "pass"（即时放行）
 *   2 ≤ DFA ≤ 7      → 调 LLM 深度审核
 *     LLM 成功        → 返回 LLM 结果
 *     LLM 失败        → DFA ≥ 5 → "violation" / DFA ≥ 2 → "uncertain"
 */
@Component
public class CmsClient {

    private static final Logger log = LoggerFactory.getLogger(CmsClient.class);

    private final LlmClient llmClient;

    /** DFA 根节点 */
    private final DfaNode root = new DfaNode();

    /** 正则规则列表 */
    private final List<PatternRule> patternRules = new ArrayList<>();

    public CmsClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostConstruct
    public void init() {
        loadDefaultDictionary();
        loadDefaultPatterns();
        log.info("DFA 敏感词词典加载完成，节点数: {}", countNodes(root));
    }

    // ========== 对外接口 ==========

    /**
     * 双层内容安全审核。
     * 未配置 AI 服务时不允许本地规则自动发布，统一转人工审核。
     *
     * @return "pass" | "violation" | "uncertain"
     */
    public String reviewContent(String title, String description, List<String> tags) {
        if (!llmClient.isConfigured()) {
            log.warn("AI 服务未配置，活动内容审核转人工");
            return "uncertain";
        }

        String titleNorm = normalize(title);
        String descNorm = normalize(description);

        // ===== Layer 1: DFA 快速过滤 =====
        int dfaScore = 0;
        dfaScore += scanDfa(titleNorm) * 2;    // 标题权重 2x
        dfaScore += scanDfa(descNorm);
        if (tags != null) {
            for (String tag : tags) {
                dfaScore += scanDfa(normalize(tag));
            }
        }
        // 正则规则
        for (PatternRule rule : patternRules) {
            if (rule.pattern.matcher(titleNorm).find() || rule.pattern.matcher(descNorm).find()) {
                dfaScore += rule.score;
            }
        }

        log.debug("DFA 评分: {}", dfaScore);

        // 明确违规 → 直接驳回（无需 LLM）
        if (dfaScore >= 8) {
            log.info("DFA 命中高危词，直接驳回，评分: {}", dfaScore);
            return "violation";
        }

        // 明显安全 → 直接放行（无需 LLM）
        if (dfaScore < 2) {
            log.debug("DFA 低分，直接放行");
            return "pass";
        }

        // ===== Layer 2: LLM 深度审核 =====
        log.info("DFA 评分: {}，转入 LLM 深度审核", dfaScore);
        try {
            String llmResult = llmClient.deepReview(title, description, tags);
            if ("pass".equals(llmResult) || "violation".equals(llmResult) || "uncertain".equals(llmResult)) {
                return llmResult;
            }
        } catch (Exception e) {
            log.error("LLM 审核异常，回退 DFA 决策", e);
        }

        return "uncertain";
    }

    // ========== DFA 扫描 ==========

    private int scanDfa(String text) {
        if (text == null || text.isEmpty()) return 0;
        int score = 0;
        int len = text.length();
        Set<String> matched = new HashSet<>();
        for (int i = 0; i < len; i++) {
            DfaNode node = root;
            for (int j = i; j < len; j++) {
                char c = text.charAt(j);
                DfaNode child = node.children.get(c);
                if (child == null) break;
                node = child;
                if (node.isEnd) {
                    String key = i + ":" + node.level;
                    if (matched.add(key)) {
                        score += node.level.score;
                    }
                }
            }
        }
        return score;
    }

    // ========== 词典加载 ==========

    private void loadDefaultDictionary() {
        // === 高危（5分） ===
        addWord("色情", Level.HIGH);
        addWord("黄色", Level.HIGH);
        addWord("裸聊", Level.HIGH);
        addWord("援交", Level.HIGH);
        addWord("约炮", Level.HIGH);
        addWord("买春", Level.HIGH);
        addWord("卖淫", Level.HIGH);
        addWord("包养", Level.HIGH);
        addWord("一夜情", Level.HIGH);

        addWord("赌博", Level.HIGH);
        addWord("赌场", Level.HIGH);
        addWord("赌球", Level.HIGH);
        addWord("六合彩", Level.HIGH);
        addWord("百家乐", Level.HIGH);
        addWord("老虎机", Level.HIGH);
        addWord("轮盘赌", Level.HIGH);
        addWord("梭哈", Level.HIGH);
        addWord("时时彩", Level.HIGH);
        addWord("棋牌室", Level.MEDIUM); // 可能是正常的

        addWord("毒品", Level.HIGH);
        addWord("冰毒", Level.HIGH);
        addWord("海洛因", Level.HIGH);
        addWord("大麻", Level.HIGH);
        addWord("摇头丸", Level.HIGH);
        addWord("K粉", Level.HIGH);

        addWord("枪支", Level.HIGH);
        addWord("弹药", Level.HIGH);
        addWord("枪械", Level.HIGH);
        addWord("爆炸物", Level.HIGH);
        addWord("恐怖袭击", Level.HIGH);
        addWord("砍人", Level.HIGH);

        // === 中危（3分） ===
        addWord("传销", Level.MEDIUM);
        addWord("诈骗", Level.MEDIUM);
        addWord("刷单", Level.MEDIUM);
        addWord("刷分", Level.MEDIUM);
        addWord("代练", Level.MEDIUM);
        addWord("代考", Level.MEDIUM);
        addWord("代购", Level.MEDIUM);
        addWord("假证", Level.MEDIUM);
        addWord("假钞", Level.MEDIUM);
        addWord("办证", Level.MEDIUM);
        addWord("发票", Level.MEDIUM);
        addWord("走私", Level.MEDIUM);
        addWord("窃听", Level.MEDIUM);
        addWord("针孔", Level.MEDIUM);
        addWord("作弊", Level.MEDIUM);
        addWord("违法", Level.MEDIUM);
        addWord("违规", Level.MEDIUM);

        // === 低危（1分） ===
        addWord("广告", Level.LOW);
        addWord("营销", Level.LOW);
        addWord("推广", Level.LOW);
        addWord("加微信", Level.LOW);
        addWord("加V", Level.LOW);
        addWord("私聊", Level.LOW);
    }

    private void loadDefaultPatterns() {
        patternRules.add(new PatternRule(Pattern.compile("1[3-9]\\d{9}"), 3));
        patternRules.add(new PatternRule(Pattern.compile("https?://[\\w./]+"), 2));
        patternRules.add(new PatternRule(Pattern.compile("微信号[\\w_-]+"), 2));
        patternRules.add(new PatternRule(Pattern.compile("(?<![\\d])[1-9]\\d{4,10}(?![\\d])"), 1));
    }

    private void addWord(String word, Level level) {
        if (word == null || word.isEmpty()) return;
        DfaNode node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new DfaNode());
        }
        node.isEnd = true;
        node.level = level;
    }

    // ========== 文本规范化 ==========

    private String normalize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                sb.append(' ');
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
            } else if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') {
                // skip
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========== 内部数据结构 ==========

    private static class DfaNode {
        final Map<Character, DfaNode> children = new HashMap<>();
        boolean isEnd = false;
        Level level = Level.LOW;
    }

    private enum Level {
        HIGH(5),
        MEDIUM(3),
        LOW(1);
        final int score;
        Level(int score) { this.score = score; }
    }

    private record PatternRule(Pattern pattern, int score) {}

    private int countNodes(DfaNode node) {
        int count = 1;
        for (DfaNode child : node.children.values()) {
            count += countNodes(child);
        }
        return count;
    }
}
