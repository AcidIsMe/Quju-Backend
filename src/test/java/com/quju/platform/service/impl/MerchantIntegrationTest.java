package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.auth.MerchantApplyReq;
import com.quju.platform.entity.MerchantProfileEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.MerchantProfileMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.AuthService;
import com.quju.platform.service.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("商家模块集成测试")
class MerchantIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private MerchantService merchantService;
    @Autowired private MerchantProfileMapper merchantProfileMapper;
    @Autowired private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        merchantProfileMapper.delete(null);
        userMapper.delete(null);
    }

    private String registerAndActivateMerchant(String email, String nickname, String merchantName, List<String> domains) {
        MerchantApplyReq req = new MerchantApplyReq();
        req.setEmail(email);
        req.setPassword("Merchant123");
        req.setNickname(nickname);
        req.setMerchantName(merchantName);
        req.setActivityDomains(domains);
        Map<String, Object> result = authService.registerMerchant(req);
        String activationToken = (String) result.get("activation_token");
        authService.activate(activationToken);
        return (String) result.get("email");
    }

    @Nested
    @DisplayName("商家注册流程")
    class RegisterTests {
        @Test
        @DisplayName("商家注册成功后状态为pending")
        void shouldRegisterMerchantWithPendingStatus() {
            MerchantApplyReq req = new MerchantApplyReq();
            req.setEmail("merchant1@test.com");
            req.setPassword("Merchant123");
            req.setNickname("TestMerchant1");
            req.setMerchantName("测试商家1号");
            req.setActivityDomains(List.of("户外", "运动"));
            Map<String, Object> result = authService.registerMerchant(req);
            assertNotNull(result.get("activation_token"));
            assertEquals("pending", result.get("audit_status"));

            MerchantProfileEntity profile = merchantProfileMapper.selectList(null).get(0);
            assertEquals("pending", profile.getAuditStatus());
            assertEquals("测试商家1号", profile.getMerchantName());
            assertEquals(List.of("户外", "运动"), profile.getActivityDomains());
        }

        @Test
        @DisplayName("商家注册后用户role为merchant")
        void shouldSetUserRoleToMerchant() {
            MerchantApplyReq req = new MerchantApplyReq();
            req.setEmail("merchant2@test.com");
            req.setPassword("Merchant123");
            req.setNickname("TestMerchant2");
            req.setMerchantName("测试商家2号");
            authService.registerMerchant(req);
            UserEntity user = userMapper.selectList(null).get(0);
            assertEquals("merchant", user.getRole());
        }
    }

    @Nested
    @DisplayName("商家审核流程")
    class AuditTests {
        @Test
        @DisplayName("审核通过后状态变为approved并激活用户")
        void shouldApproveMerchantAndActivateUser() {
            registerAndActivateMerchant("audit-pass@test.com", "AuditPass", "审核通过商家", List.of("美食"));
            MerchantProfileEntity profile = merchantProfileMapper.selectList(null).get(0);
            merchantService.approve(profile.getId(), "admin-001");
            profile = merchantProfileMapper.selectById(profile.getId());
            assertEquals("approved", profile.getAuditStatus());
            assertEquals("admin-001", profile.getAuditedBy());
            assertNotNull(profile.getAuditedAt());

            UserEntity user = userMapper.selectById(profile.getUserId());
            assertEquals("active", user.getStatus());
        }

        @Test
        @DisplayName("审核驳回后状态变为rejected并记录原因")
        void shouldRejectMerchantWithReason() {
            registerAndActivateMerchant("audit-reject@test.com", "AuditReject", "审核驳回商家", List.of("音乐"));
            MerchantProfileEntity profile = merchantProfileMapper.selectList(null).get(0);
            merchantService.reject(profile.getId(), "资料不完整", "admin-001");
            profile = merchantProfileMapper.selectById(profile.getId());
            assertEquals("rejected", profile.getAuditStatus());
            assertEquals("资料不完整", profile.getAuditReason());
            assertEquals("admin-001", profile.getAuditedBy());
            assertNotNull(profile.getAuditedAt());
        }

        @Test
        @DisplayName("重复审核应抛异常")
        void shouldRejectDuplicateAudit() {
            registerAndActivateMerchant("dup-audit@test.com", "DupAudit", "重复审核商家", List.of("旅行"));
            MerchantProfileEntity profile = merchantProfileMapper.selectList(null).get(0);
            merchantService.approve(profile.getId(), "admin-001");
            assertThrows(BusinessException.class,
                    () -> merchantService.approve(profile.getId(), "admin-002"));
            assertThrows(BusinessException.class,
                    () -> merchantService.reject(profile.getId(), "重复驳回", "admin-002"));
        }

        @Test
        @DisplayName("驳回原因必填校验")
        void shouldRequireRejectReason() {
            registerAndActivateMerchant("no-reason@test.com", "NoReason", "无原因驳回", List.of("读书"));
            MerchantProfileEntity profile = merchantProfileMapper.selectList(null).get(0);
            assertThrows(BusinessException.class,
                    () -> merchantService.reject(profile.getId(), "", "admin-001"));
            assertThrows(BusinessException.class,
                    () -> merchantService.reject(profile.getId(), null, "admin-001"));
        }
    }

    @Nested
    @DisplayName("公开商家列表")
    class ListTests {
        @Test
        @DisplayName("只返回已审核通过的商家")
        void shouldOnlyReturnApprovedMerchants() {
            registerAndActivateMerchant("approved@test.com", "ApprovedM", "已通过商家", List.of("户外"));
            registerAndActivateMerchant("pending@test.com", "PendingM", "待审核商家", List.of("美食"));
            MerchantProfileEntity pending = merchantProfileMapper.selectList(
                    Wrappers.<MerchantProfileEntity>lambdaQuery()
                            .eq(MerchantProfileEntity::getMerchantNickname, "PendingM")).get(0);
            // 通过第一个
            MerchantProfileEntity approved = merchantProfileMapper.selectList(
                    Wrappers.<MerchantProfileEntity>lambdaQuery()
                            .eq(MerchantProfileEntity::getMerchantNickname, "ApprovedM")).get(0);
            merchantService.approve(approved.getId(), "admin-001");
            // pending 不通过

            Map<String, Object> result = merchantService.listApproved(null, null, 1, 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
            assertEquals(1, data.size());
            assertEquals("已通过商家", data.get(0).get("merchant_name"));
        }

        @Test
        @DisplayName("关键词搜索商家名称和昵称")
        void shouldSearchByKeyword() {
            registerAndActivateMerchant("cafe@test.com", "CoffeeShop", "咖啡驿站", List.of("美食"));
            registerAndActivateMerchant("gym@test.com", "FitGym", "健身工坊", List.of("运动"));
            // 通过两个
            merchantProfileMapper.selectList(null).forEach(p -> merchantService.approve(p.getId(), "admin-001"));

            // 搜索"咖啡"
            Map<String, Object> result = merchantService.listApproved("咖啡", null, 1, 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
            assertEquals(1, data.size());
            assertEquals("咖啡驿站", data.get(0).get("merchant_name"));

            // 搜索"Fit"（昵称）
            result = merchantService.listApproved("Fit", null, 1, 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data2 = (List<Map<String, Object>>) result.get("data");
            assertEquals(1, data2.size());
            assertEquals("健身工坊", data2.get(0).get("merchant_name"));
        }

        @Test
        @DisplayName("按活动领域筛选")
        void shouldFilterByActivityDomain() {
            registerAndActivateMerchant("hiking@test.com", "HikeClub", "徒步俱乐部", List.of("户外", "旅行"));
            registerAndActivateMerchant("board@test.com", "BoardGame", "桌游吧", List.of("桌游", "社交"));
            merchantProfileMapper.selectList(null).forEach(p -> merchantService.approve(p.getId(), "admin-001"));

            Map<String, Object> result = merchantService.listApproved(null, "户外", 1, 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
            assertEquals(1, data.size());
            assertEquals("徒步俱乐部", data.get(0).get("merchant_name"));

            result = merchantService.listApproved(null, "桌游", 1, 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data2 = (List<Map<String, Object>>) result.get("data");
            assertEquals(1, data2.size());
            assertEquals("桌游吧", data2.get(0).get("merchant_name"));
        }

        @Test
        @DisplayName("分页功能正常")
        void shouldPaginateResults() {
            for (int i = 0; i < 5; i++) {
                registerAndActivateMerchant("m" + i + "@test.com", "Merchant" + i, "商家" + i, List.of("通用"));
            }
            merchantProfileMapper.selectList(null).forEach(p -> merchantService.approve(p.getId(), "admin-001"));

            Map<String, Object> result = merchantService.listApproved(null, null, 1, 3);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
            assertEquals(3, data.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> pagination = (Map<String, Object>) result.get("pagination");
            assertEquals(5L, pagination.get("total"));
            assertEquals(1, pagination.get("page"));
            assertEquals(3, pagination.get("size"));

            result = merchantService.listApproved(null, null, 2, 3);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data2 = (List<Map<String, Object>>) result.get("data");
            assertEquals(2, data2.size());
        }

        @Test
        @DisplayName("空列表查询不报错")
        void shouldReturnEmptyListWhenNoMerchants() {
            Map<String, Object> result = merchantService.listApproved(null, null, 1, 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
            assertTrue(data.isEmpty());
            @SuppressWarnings("unchecked")
            Map<String, Object> pagination = (Map<String, Object>) result.get("pagination");
            assertEquals(0L, pagination.get("total"));
        }
    }
}
