package com.quju.platform.component.mq;

import org.springframework.stereotype.Component;

@Component
public class WaitlistTimeoutListener {
    public void onTimeout(String waitlistId) {
    }
}
