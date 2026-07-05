package com.quju.platform.component.mq;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 积分事件 —— 当小队成员触发积分行为时发布此事件。
 */
@Getter
public class PointEvent extends ApplicationEvent {

    private final String teamId;
    private final String userId;
    private final int points;
    private final String reason;

    public PointEvent(Object source, String teamId, String userId, int points, String reason) {
        super(source);
        this.teamId = teamId;
        this.userId = userId;
        this.points = points;
        this.reason = reason;
    }
}
