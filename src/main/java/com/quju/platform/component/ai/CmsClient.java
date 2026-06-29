package com.quju.platform.component.ai;

import org.springframework.stereotype.Component;

@Component
public class CmsClient {

    public boolean passText(String text) {
        return text == null || !text.contains("违规");
    }
}
