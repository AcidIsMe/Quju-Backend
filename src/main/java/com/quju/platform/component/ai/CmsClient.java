package com.quju.platform.component.ai;

import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class CmsClient {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * AI 内容安全审核，10 秒超时降级为人工审核
     * @return "pass" | "violation" | "uncertain"
     */
    public String reviewContent(String title, String description, java.util.List<String> tags) {
        Future<String> future = EXECUTOR.submit(() -> {
            String fullText = (title == null ? "" : title) + " " +
                    (description == null ? "" : description);
            if (fullText.contains("违规") || fullText.contains("违法") || fullText.contains("色情") || fullText.contains("赌博")) {
                return "violation";
            }
            if (fullText.contains("广告") || fullText.contains("营销")) {
                return "uncertain";
            }
            return "pass";
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 超时降级为人工审核
            future.cancel(true);
            return "uncertain";
        } catch (Exception e) {
            return "uncertain";
        }
    }
}
