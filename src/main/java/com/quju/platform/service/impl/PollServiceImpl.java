package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.*;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.*;
import com.quju.platform.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PollServiceImpl implements PollService {

    private final TeamPollMapper teamPollMapper;
    private final PollOptionMapper pollOptionMapper;
    private final PollVoteMapper pollVoteMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;

    @Override
    @Transactional
    public Map<String, Object> createPoll(String teamId, String userId, String title, List<String> options) {
        // 验证小队存在
        TeamEntity team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }

        // 验证是否为小队成员
        Long memberCount = teamMemberMapper.selectCount(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, userId));
        if (memberCount == 0) {
            throw new BusinessException(40300, "只有小队成员可以创建投票");
        }

        // 参数校验
        if (title == null || title.isBlank()) {
            throw new BusinessException(40000, "投票标题不能为空");
        }
        if (options == null || options.size() < 2) {
            throw new BusinessException(40000, "至少需要2个选项");
        }

        // 创建投票
        TeamPollEntity poll = new TeamPollEntity();
        poll.setTeamId(teamId);
        poll.setTitle(title);
        poll.setCreatedBy(userId);
        poll.setClosed(false);
        teamPollMapper.insert(poll);

        // 创建选项
        List<Map<String, Object>> optionList = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            PollOptionEntity option = new PollOptionEntity();
            option.setPollId(poll.getId());
            option.setOptionText(options.get(i));
            option.setSortOrder(i);
            pollOptionMapper.insert(option);

            Map<String, Object> optMap = new LinkedHashMap<>();
            optMap.put("id", option.getId());
            optMap.put("option_text", option.getOptionText());
            optMap.put("sort_order", option.getSortOrder());
            optMap.put("vote_count", 0);
            optMap.put("has_voted", false);
            optionList.add(optMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", poll.getId());
        result.put("team_id", poll.getTeamId());
        result.put("title", poll.getTitle());
        result.put("created_by", poll.getCreatedBy());
        result.put("closed", poll.getClosed());
        result.put("created_at", poll.getCreatedAt());
        result.put("closed_at", poll.getClosedAt());
        result.put("options", optionList);
        return result;
    }

    @Override
    @Transactional
    public void vote(String pollId, String optionId, String userId) {
        // 验证投票存在且未关闭
        TeamPollEntity poll = teamPollMapper.selectById(pollId);
        if (poll == null) {
            throw new BusinessException(40404, "投票不存在");
        }
        if (poll.getClosed() != null && poll.getClosed()) {
            throw new BusinessException(40001, "投票已关闭");
        }

        // 验证是否为小队成员
        Long memberCount = teamMemberMapper.selectCount(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, poll.getTeamId())
                .eq(TeamMemberEntity::getUserId, userId));
        if (memberCount == 0) {
            throw new BusinessException(40300, "只有小队成员可以投票");
        }

        // 验证选项属于该投票
        PollOptionEntity option = pollOptionMapper.selectById(optionId);
        if (option == null || !option.getPollId().equals(pollId)) {
            throw new BusinessException(40404, "选项不存在");
        }

        // 检查用户是否已投票
        Long voteCount = pollVoteMapper.selectCount(Wrappers.<PollVoteEntity>lambdaQuery()
                .eq(PollVoteEntity::getPollId, pollId)
                .eq(PollVoteEntity::getUserId, userId));
        if (voteCount > 0) {
            throw new BusinessException(40901, "您已经投过票了");
        }

        // 插入投票记录
        PollVoteEntity vote = new PollVoteEntity();
        vote.setPollId(pollId);
        vote.setOptionId(optionId);
        vote.setUserId(userId);
        pollVoteMapper.insert(vote);
    }

    @Override
    public List<Map<String, Object>> listPolls(String teamId) {
        // 验证小队存在
        TeamEntity team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }

        // 查询投票列表，按创建时间倒序
        List<TeamPollEntity> polls = teamPollMapper.selectList(Wrappers.<TeamPollEntity>lambdaQuery()
                .eq(TeamPollEntity::getTeamId, teamId)
                .orderByDesc(TeamPollEntity::getCreatedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamPollEntity poll : polls) {
            // 查询选项列表
            List<PollOptionEntity> options = pollOptionMapper.selectList(Wrappers.<PollOptionEntity>lambdaQuery()
                    .eq(PollOptionEntity::getPollId, poll.getId())
                    .orderByAsc(PollOptionEntity::getSortOrder));

            List<Map<String, Object>> optionList = new ArrayList<>();
            for (PollOptionEntity opt : options) {
                long count = pollVoteMapper.selectCount(Wrappers.<PollVoteEntity>lambdaQuery()
                        .eq(PollVoteEntity::getOptionId, opt.getId()));

                Map<String, Object> optMap = new LinkedHashMap<>();
                optMap.put("id", opt.getId());
                optMap.put("option_text", opt.getOptionText());
                optMap.put("sort_order", opt.getSortOrder());
                optMap.put("vote_count", count);
                optionList.add(optMap);
            }

            Map<String, Object> pollMap = new LinkedHashMap<>();
            pollMap.put("id", poll.getId());
            pollMap.put("team_id", poll.getTeamId());
            pollMap.put("title", poll.getTitle());
            pollMap.put("created_by", poll.getCreatedBy());
            pollMap.put("closed", poll.getClosed());
            pollMap.put("created_at", poll.getCreatedAt());
            pollMap.put("closed_at", poll.getClosedAt());
            pollMap.put("options", optionList);
            result.add(pollMap);
        }
        return result;
    }

    @Override
    @Transactional
    public void closePoll(String pollId, String userId) {
        // 验证投票存在
        TeamPollEntity poll = teamPollMapper.selectById(pollId);
        if (poll == null) {
            throw new BusinessException(40404, "投票不存在");
        }
        if (poll.getClosed() != null && poll.getClosed()) {
            throw new BusinessException(40001, "投票已关闭");
        }

        // 验证权限：创建者或队长/管理员
        boolean isCreator = userId.equals(poll.getCreatedBy());
        boolean isLeaderOrAdmin = false;
        if (!isCreator) {
            TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                    .eq(TeamMemberEntity::getTeamId, poll.getTeamId())
                    .eq(TeamMemberEntity::getUserId, userId));
            if (member != null && ("leader".equals(member.getRole()) || "admin".equals(member.getRole()))) {
                isLeaderOrAdmin = true;
            }
        }

        if (!isCreator && !isLeaderOrAdmin) {
            throw new BusinessException(40300, "只有投票创建者或队长/管理员可以关闭投票");
        }

        poll.setClosed(true);
        poll.setClosedAt(LocalDateTime.now());
        teamPollMapper.updateById(poll);
    }
}
