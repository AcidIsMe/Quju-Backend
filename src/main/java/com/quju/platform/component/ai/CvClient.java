package com.quju.platform.component.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class CvClient {

    private static final List<String> CATEGORIES = List.of("group_photo", "venue", "process", "supplies", "result");

    public List<String> classify(int imageCount) {
        return java.util.stream.IntStream.range(0, imageCount)
                .mapToObj(index -> CATEGORIES.get(ThreadLocalRandom.current().nextInt(CATEGORIES.size())))
                .toList();
    }
}
