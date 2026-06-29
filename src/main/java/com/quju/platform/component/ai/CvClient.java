package com.quju.platform.component.ai;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CvClient {

    public List<String> classify(int imageCount) {
        return java.util.stream.IntStream.range(0, imageCount)
                .mapToObj(index -> "process")
                .toList();
    }
}
