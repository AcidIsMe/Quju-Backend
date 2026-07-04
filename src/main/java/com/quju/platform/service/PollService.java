package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface PollService {
    /** 创建投票（仅小队成员） */
    Map<String, Object> createPoll(String teamId, String userId, String title, List<String> options);

    /** 投票（仅小队成员，每人每个投票仅一票） */
    void vote(String pollId, String optionId, String userId);

    /** 投票列表（含选项和票数） */
    List<Map<String, Object>> listPolls(String teamId);

    /** 关闭投票（仅创建者或队长/管理员） */
    void closePoll(String pollId, String userId);
}
