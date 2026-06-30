package com.quju.platform.service.impl;

import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.TeamJoinRequestMapper;
import com.quju.platform.mapper.TeamMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.SquadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("小队服务集成测试 (US29-33)")
class SquadIntegrationTest {

    @Autowired private SquadService squadService;
    @Autowired private TeamMapper teamMapper;
    @Autowired private TeamMemberMapper teamMemberMapper;
    @Autowired private TeamJoinRequestMapper teamJoinRequestMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private UserEntity leader;
    private UserEntity member;
    private UserEntity outsider;

    @BeforeEach
    void setUp() {
        teamJoinRequestMapper.delete(null);
        teamMemberMapper.delete(null);
        teamMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 队长
        leader = new UserEntity();
        leader.setEmail("leader@example.com");
        leader.setPasswordHash(passwordEncoder.encode("Pass1234"));
        leader.setNickname("Leader");
        leader.setRole("personal");
        leader.setStatus("active");
        leader.setCreditScore(100);
        leader.setInterestTags(List.of());
        userMapper.insert(leader);

        // 普通成员
        member = new UserEntity();
        member.setEmail("member@example.com");
        member.setPasswordHash(passwordEncoder.encode("Pass1234"));
        member.setNickname("Member");
        member.setRole("personal");
        member.setStatus("active");
        member.setCreditScore(100);
        member.setInterestTags(List.of());
        userMapper.insert(member);

        // 外部用户
        outsider = new UserEntity();
        outsider.setEmail("outsider@example.com");
        outsider.setPasswordHash(passwordEncoder.encode("Pass1234"));
        outsider.setNickname("Outsider");
        outsider.setRole("personal");
        outsider.setStatus("active");
        outsider.setCreditScore(100);
        outsider.setInterestTags(List.of());
        userMapper.insert(outsider);
    }

    private void authenticateAs(String uid) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_personal"))));
    }

    private SquadCreateReq createValidReq() {
        SquadCreateReq req = new SquadCreateReq();
        req.setName("测试小队");
        req.setDescription("测试小队描述");
        req.setInterestTags(List.of("户外", "徒步"));
        req.setJoinType("public");
        req.setMaxMembers(50);
        return req;
    }

    @Nested @DisplayName("创建小队")
    class CreateTests {

        @Test @DisplayName("成功创建小队")
        void shouldCreateSquad() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());
            assertNotNull(team.getId());
            assertEquals("测试小队", team.getName());
            assertEquals(leader.getId(), team.getLeaderId());
        }

        @Test @DisplayName("创建后队长自动成为成员")
        void shouldAddLeaderAsMember() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());
            List<Map<String, Object>> members = squadService.members(team.getId());
            assertTrue(members.stream().anyMatch(m -> m.get("userId").equals(leader.getId())));
        }
    }

    @Nested @DisplayName("加入和退出")
    class JoinLeaveTests {

        @Test @DisplayName("公开小队可直接加入")
        void shouldJoinPublicSquad() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());

            authenticateAs(member.getId());
            Map<String, Object> result = squadService.join(team.getId(), member.getId());
            assertEquals("joined", result.get("status"));
        }

        @Test @DisplayName("退出小队成功")
        void shouldLeaveSquad() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());

            authenticateAs(member.getId());
            squadService.join(team.getId(), member.getId());

            authenticateAs(member.getId());
            squadService.leave(team.getId(), member.getId());

            List<Map<String, Object>> members = squadService.members(team.getId());
            assertTrue(members.stream().noneMatch(m -> m.get("userId").equals(member.getId())));
        }

        @Test @DisplayName("审核制小队需发送申请")
        void shouldSendJoinRequestForReviewSquad() {
            authenticateAs(leader.getId());
            SquadCreateReq req = createValidReq();
            req.setJoinType("review");
            TeamEntity team = squadService.create(req, leader.getId());

            authenticateAs(member.getId());
            Map<String, Object> result = squadService.join(team.getId(), member.getId());
            assertEquals("pending", result.get("status"));
        }
    }

    @Nested @DisplayName("小队管理")
    class ManagementTests {

        @Test @DisplayName("队长可更新小队信息")
        void shouldUpdateSquad() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());

            SquadCreateReq updateReq = createValidReq();
            updateReq.setName("更新后的小队");
            TeamEntity updated = squadService.update(team.getId(), leader.getId(), updateReq);
            assertEquals("更新后的小队", updated.getName());
        }

        @Test @DisplayName("非队长不能更新小队")
        void shouldRejectUpdateByNonLeader() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());

            authenticateAs(member.getId());
            assertThrows(BusinessException.class,
                    () -> squadService.update(team.getId(), member.getId(), createValidReq()));
        }

        @Test @DisplayName("队长可修改成员角色")
        void shouldChangeMemberRole() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());

            authenticateAs(member.getId());
            squadService.join(team.getId(), member.getId());

            authenticateAs(leader.getId());
            squadService.changeRole(team.getId(), leader.getId(), member.getId(), "admin");
        }

        @Test @DisplayName("队长可移出成员")
        void shouldRemoveMember() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());

            authenticateAs(member.getId());
            squadService.join(team.getId(), member.getId());

            authenticateAs(leader.getId());
            squadService.removeMember(team.getId(), leader.getId(), member.getId());

            List<Map<String, Object>> members = squadService.members(team.getId());
            assertTrue(members.stream().noneMatch(m -> m.get("userId").equals(member.getId())));
        }

        @Test @DisplayName("申请审核制：通过加入申请")
        void shouldApproveJoinRequest() {
            authenticateAs(leader.getId());
            SquadCreateReq req = createValidReq();
            req.setJoinType("review");
            TeamEntity team = squadService.create(req, leader.getId());

            authenticateAs(member.getId());
            squadService.join(team.getId(), member.getId());

            authenticateAs(leader.getId());
            var requests = squadService.joinRequests(team.getId(), leader.getId());
            assertFalse(requests.isEmpty());

            squadService.approveRequest(team.getId(), leader.getId(), requests.get(0).getId());

            List<Map<String, Object>> members = squadService.members(team.getId());
            assertTrue(members.stream().anyMatch(m -> m.get("userId").equals(member.getId())));
        }

        @Test @DisplayName("申请审核制：拒绝加入申请")
        void shouldRejectJoinRequest() {
            authenticateAs(leader.getId());
            SquadCreateReq req = createValidReq();
            req.setJoinType("review");
            TeamEntity team = squadService.create(req, leader.getId());

            authenticateAs(member.getId());
            squadService.join(team.getId(), member.getId());

            authenticateAs(leader.getId());
            var requests = squadService.joinRequests(team.getId(), leader.getId());
            assertFalse(requests.isEmpty());

            squadService.rejectRequest(team.getId(), leader.getId(), requests.get(0).getId());
        }

        @Test @DisplayName("解散小队")
        void shouldDissolveSquad() {
            authenticateAs(leader.getId());
            TeamEntity team = squadService.create(createValidReq(), leader.getId());
            squadService.dissolve(team.getId(), leader.getId());

            TeamEntity dissolved = teamMapper.selectById(team.getId());
            assertNotNull(dissolved);
            assertEquals("dissolved", dissolved.getStatus());
        }
    }
}
