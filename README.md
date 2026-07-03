# Quju-Backend — 趣聚平台后端

Spring Boot 3 + MySQL 后端服务，为「趣聚」活动社交平台提供完整 API 支持。

---

## 技术栈

- Java 21
- Spring Boot 3.5.16
- MyBatis-Plus 3.5.9
- MySQL 8+
- Spring Security + JWT（双 Token 鉴权）
- WebSocket（即时通讯，自研 `ImWebSocketServer`）
- Google ZXing（二维码生成，真实 PNG → Base64 data URI）
- SiliconFlow DeepSeek-V3.2（AI 内容审核 + AI 活动策划，真实 API 调用）
- DFA 敏感词过滤器（本地第一道过滤，O(n) 时间复杂度）
- H2 Database（dev 测试模式）

---

## 启动步骤

### 1. 配置数据库

修改 `src/main/resources/application.yml` 中的 MySQL 连接信息：

```yaml
spring:
  datasource:
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:lsy040628}
```

### 2. 启动服务

```powershell
.\mvnw.cmd spring-boot:run
```

启动时自动创建 `quju_platform` 数据库并执行 `schema.sql` 建表（21 张表）。

### 3. 访问地址

```
http://localhost:3002/api
```

### 4. H2 测试模式

使用 `application-h2.yml` 配置可启动内存数据库模式，无需 MySQL：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2
```

---

## 注册、登录与邮箱激活

当前已实现完整的**邮箱 + 密码注册、账号激活、JWT 双 Token 登录/刷新/退出**闭环，但**尚未接入真实 SMTP 邮件发送服务**。

### 注册流程

1. **个人注册** `POST /api/auth/register/personal` — 返回 `activation_token`
2. **商家注册** `POST /api/auth/register/merchant` — 同时创建 `merchant_profiles`，审核状态为 `pending`
3. 密码使用 **BCrypt** 加密存储
4. 未激活用户状态为 `pending_activation`，登录时被拦截

### 登录安全

- **频率限制**：连续 5 次输入错误密码 → 锁定账户 15 分钟（滑动窗口）
- **状态检查**：`pending_activation` / `banned` 状态登录被拦截并返回明确错误码

### 开发环境激活方式

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:3002/api/auth/activate/{activation_token}"
```

### 后续待接入

- `spring-boot-starter-mail` + SMTP 配置
- 真实邮件发送激活链接（当前仅在响应中返回 token，供开发测试使用）

---

## AI 服务（已真实接入）

### 内容安全审核（双层引擎）

```
活动提交审核
  │
  ├─ 未配置 AI Key → uncertain → 转人工审核
  │
  ├─ Layer 1: DFA 敏感词快速过滤（<1ms，本地预审）
  │    ├─ DFA ≥ 8 分  → violation ← 即时驳回
  │    ├─ DFA < 2 分  → pass      ← 即时放行
  │    └─ DFA 2~7 分  → 转入 Layer 2
  │
  └─ Layer 2: LLM 深度审核（SiliconFlow DeepSeek-V3.2）
       ├─ 成功 → 返回 LLM 判定结果（pass/violation/uncertain）
       └─ 失败（超时/网络异常/解析失败）→ uncertain → 转人工审核
```

- **DFA**：60+ 敏感词三级评分（HIGH 5分 / MEDIUM 3分 / LOW 1分），标题权重 2x
- **LLM**：`temperature=0.1` + `response_format=json_object` 保证稳定结构化输出
- **兜底**：AI Key 未配置或 LLM 调用失败时，活动进入 `pending_manual_review`
- **配置**：通过环境变量 `SILICONFLOW_API_KEY` 提供真实 API Key，不写入仓库

### AI 活动策划

- **端点**：`POST /api/ai/generate-activity`
- **实现**：调用 SiliconFlow DeepSeek-V3.2 生成结构化活动方案（标题、描述、标签、类型、时长、人数建议）
- **降级**：API 不可用时返回基于模板的默认方案

---

## 所有 API 端点一览

### 认证 Auth

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/auth/register/personal` | 个人注册 |
| POST | `/api/auth/register/merchant` | 商家注册（含执照上传） |
| POST | `/api/auth/login` | 登录（返回 access + refresh token） |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 退出登录 |
| GET | `/api/auth/activate/{token}` | 邮箱激活 |
| POST | `/api/auth/resend-activation` | 重发激活邮件 |

### 用户 Users

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/users/me` | 获取当前用户信息 |
| PATCH | `/api/users/me` | 更新当前用户资料（昵称、头像、性别、生日、bio、兴趣标签） |
| GET | `/api/users/{id}` | 查看用户公开信息（含好友/关注状态、活动数统计） |
| GET | `/api/users/check-nickname` | 检查昵称是否可用（公开端点） |
| GET | `/api/users/me/created-activities` | 我创建的活动（游标分页） |
| GET | `/api/users/me/joined-activities` | 我报名的活动（游标分页，含活动详情聚合） |

### 活动 Activities

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/activities` | 创建活动（支持 draft 草稿/直接提交审核） |
| PUT | `/api/activities/{id}` | 更新活动（仅 draft/rejected 状态可编辑） |
| GET | `/api/activities/{id}` | 活动详情（含创建者信息、报名状态、展示状态；匿名可访问） |
| POST | `/api/activities/{id}/clone` | 克隆活动为草稿 |
| DELETE | `/api/activities/{id}` | 删除草稿 |
| GET | `/api/activities/{id}/participants` | 报名用户列表 |
| POST | `/api/activities/{id}/submit` | 提交审核（自动触发 AI 审核或分流人工） |

### 活动发现 Discover

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/discover/latest` | 最新活动（游标分页） |
| GET | `/api/discover/recommended` | 推荐活动（热门优先） |
| GET | `/api/discover/search` | 关键词搜索（相关度排序：标题 > 标签 > 描述；无结果兜底推荐） |
| GET | `/api/discover/filter` | 高级筛选（类型 OR / 城市 / 费用 / 时间 / 距离） |
| GET | `/api/discover/nearby` | 附近活动（Haversine 半径查询，需位置权限） |
| GET | `/api/discover/map` | 地图模式点位（优先边界框查询，降级半径查询） |

### 报名与候补 Registrations & Waitlist

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/activities/{id}/register` | 报名活动（SELECT FOR UPDATE 行锁防超卖） |
| POST | `/api/activities/{id}/cancel-registration` | 取消报名（截止时间校验 + 自动候补递补） |
| POST | `/api/activities/{id}/join-waitlist` | 加入等待队列 |
| DELETE | `/api/activities/{id}/leave-waitlist` | 退出等待队列 |
| GET | `/api/activities/{id}/waitlist-position` | 查询队列位置 |

> 取消报名时自动从候补队列递补下一位，递补后 30 分钟确认超时顺延下一位。（`@Scheduled(fixedRate=60s)` 每分钟检查）

### 签到 Check-in

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/activities/{id}/check-in` | 扫码签到（含 500m 位置校验、重复检测、报名校验） |
| GET | `/api/activities/{id}/check-in/list` | 签到名单（含统计：签到率） |
| POST | `/api/activities/{id}/check-in/qrcode` | 生成签到二维码（ZXing 真实生成 PNG → Base64 data URI） |

### 评价 Reviews

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/activities/{id}/reviews` | 提交评价（已签到 + 7 天内 + 活动已结束） |
| GET | `/api/activities/{id}/reviews` | 评价列表（游标分页，平铺用户昵称/头像） |

### 活动总结 Summaries

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/activities/{id}/summary` | 发布图文总结（仅创建者，至少 1 张图片） |
| GET | `/api/activities/{id}/summary` | 获取总结详情 |
| POST | `/api/activities/{id}/summary/classify-images` | AI 图片分类（DeepSeek-V3.2） |
| PUT | `/api/activities/{id}/summary/images/{imageId}/category` | 手动调整图片分类 |

### 活动模板 Templates

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/templates` | 模板列表（支持 category 筛选） |
| POST | `/api/templates/{id}/use` | 使用模板创建草稿（自动填充字段） |

### AI 模块

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/ai/generate-activity` | AI 活动策划（DeepSeek-V3.2 结构化生成） |
| POST | `/api/ai/classify-images` | AI 图片分类 |

### 好友 Friends

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/friends/requests` | 发送好友申请 |
| GET | `/api/friends` | 好友列表（支持 group_tag 筛选） |
| GET | `/api/friends/requests/received` | 收到的好友申请 |
| GET | `/api/friends/requests/sent` | 发出的好友申请 |
| POST | `/api/friends/requests/{id}/accept` | 接受好友申请 |
| POST | `/api/friends/requests/{id}/reject` | 拒绝好友申请 |
| DELETE | `/api/friends/{userId}` | 删除好友（双向解除） |
| PATCH | `/api/friends/{userId}` | 修改备注名/分组标签 |

### 关注 Follows

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/follows/{userId}` | 关注用户（互关自动成为好友） |
| DELETE | `/api/follows/{userId}` | 取消关注（互关时解除好友） |
| GET | `/api/users/{id}/followers` | 粉丝列表 |
| GET | `/api/users/{id}/following` | 关注列表 |

### 小队 Teams

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/teams` | 创建小队（设置名称、标签、加入方式、人数上限） |
| GET | `/api/teams` | 小队发现列表（关键词搜索） |
| GET | `/api/teams/{id}` | 小队详情 |
| PUT | `/api/teams/{id}` | 修改小队信息（仅队长/管理员） |
| POST | `/api/teams/{id}/join` | 加入小队（公开直接 / 审核制申请） |
| POST | `/api/teams/{id}/leave` | 退出小队 |
| POST | `/api/teams/{id}/dissolve` | 解散小队（仅队长） |
| GET | `/api/teams/{id}/members` | 成员列表 |
| PATCH | `/api/teams/{id}/members/{userId}/role` | 修改成员角色（队长/管理员/成员） |
| DELETE | `/api/teams/{id}/members/{userId}` | 移出成员 |
| GET | `/api/teams/{id}/join-requests` | 加入申请列表 |
| POST | `/api/teams/{id}/join-requests/{requestId}/approve` | 通过申请 |
| POST | `/api/teams/{id}/join-requests/{requestId}/reject` | 拒绝申请 |
| GET | `/api/teams/{id}/leaderboard` | 积分榜（按积分降序 + 排名） |
| POST | `/api/teams/{id}/points` | 调整成员积分 |

### 即时通讯 IM

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/im/messages` | 发送消息 |
| POST | `/api/im/messages/{id}/recall` | 撤回消息（2 分钟内） |

> WebSocket 连接：`ws://{host}/api/ws/im?token={jwt}`（自研 `ImWebSocketServer`）

### 通知 Notifications

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/notifications` | 通知列表（支持类型/已读筛选，游标分页） |
| GET | `/api/notifications/unread-count` | 未读通知数 |
| PATCH | `/api/notifications/{id}/read` | 标记已读 |
| POST | `/api/notifications/read-all` | 全部标记已读 |

### 商家 Merchant

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/merchants/me` | 获取商家资料 |
| PATCH | `/api/merchants/me` | 更新商家资料（名称、昵称、活动领域） |

### 文件上传 Files

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/files/upload` | 上传文件（支持 type + teamId，存储到本地磁盘） |
| GET | `/api/files/team-album/{teamId}` | 小队相册列表 |
| DELETE | `/api/files/team-album/{teamId}` | 删除相册照片（仅队长/管理员） |

### 管理后台 Admin（需 admin 角色）

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/admin/dashboard` | 仪表盘统计 |
| GET | `/api/admin/users` | 用户查询（邮箱/昵称/角色/状态） |
| GET | `/api/admin/users/{id}` | 用户详情 |
| POST | `/api/admin/users/{id}/ban` | 封禁用户（原因必填） |
| POST | `/api/admin/users/{id}/unban` | 解封用户 |
| GET | `/api/admin/merchants/pending` | 待审核商家列表 |
| POST | `/api/admin/merchants/{id}/approve` | 通过商家审核（自动激活用户 + 通知） |
| POST | `/api/admin/merchants/{id}/reject` | 驳回商家审核（原因必填 + 通知） |
| GET | `/api/admin/activities` | 活动管理列表 |
| GET | `/api/admin/activities/{id}` | 活动详情 |
| POST | `/api/admin/activities/{id}/review` | 活动审核（通过/驳回/退回修改 + 状态守卫 + 通知） |
| POST | `/api/admin/activities/{id}/take-down` | 下架活动（原因必填 + 状态守卫 + 通知） |
| POST | `/api/admin/activities/{id}/restore` | 恢复活动（状态守卫 + 通知） |
| GET | `/api/admin/teams` | 小队列表（含队长信息、活动数） |
| GET | `/api/admin/teams/{id}` | 小队详情（含队长/成员/活动数） |
| GET | `/api/admin/teams/{id}/members` | 小队成员列表 |
| POST | `/api/admin/teams/{id}/disable` | 停用小队（原因必填 + 通知） |
| POST | `/api/admin/teams/{id}/restore` | 恢复小队（存在性校验 + 通知） |

---

## 用户故事完成度

### 迭代 1 — P0 核心闭环（14 个用户故事，55 故事点）

| 故事 | 实现位置 | 状态 | 说明 |
|------|---------|------|------|
| **US01 用户注册** | `AuthServiceImpl` | ✅ 完整 | 邮箱+密码注册、激活 token |
| **US02 登录鉴权** | `AuthServiceImpl` + `SecurityConfig` | ✅ 完整 | JWT 双 token、频率限制、状态检查 |
| **US03 用户资料管理** | `UserController` | ✅ 完整 | 全字段更新、公开信息统计、昵称查重 |
| **US07 活动发布（发起人）** | `ActivityServiceImpl` | ✅ 完整 | 创建/编辑/删除/克隆 |
| **US08 活动状态管理** | `ActivityStateMachine` | ✅ 完整 | draft→pending_ai/pending_manual→published/rejected→taken_down |
| **US09 活动参与管理（报名）** | `RegistrationServiceImpl` | ✅ 完整 | FOR UPDATE 行锁、截止时间校验 |
| **US12 AI 内容安全审核** | `CmsClient` + `LlmClient` | ✅ 完整 | DFA 快速过滤 + DeepSeek-V3.2 深度审核 |
| **US13 人工审核活动** | `OpsAdminController` + `ActivityStateMachine` | ✅ 完整 | 通过/驳回/退回修改 + 状态守卫 + 通知 |
| **US14 发现活动** | `DiscoveryServiceImpl` | ✅ 完整 | 最新/推荐/搜索/筛选/附近/地图 |
| **US15 活动关键词搜索** | `ActivityMapper.xml` + `DiscoveryServiceImpl` | ✅ 完整 | 相关度排序、无结果兜底热门 |
| **US17 地图模式** | `DiscoveryController` + `ActivityMapper.xml` | ✅ 完整 | 边界框查询、轻量字段、缩放动态加载 |
| **US18 活动详情页** | `ActivityServiceImpl` | ✅ 完整 | 创建者/报名状态/展示状态聚合 |
| **US19 活动报名与校验** | `RegistrationServiceImpl` | ✅ 完整 | 名额校验、报名/取消、FOR UPDATE 行锁 |
| **US20 取消报名** | `RegistrationServiceImpl` | ✅ 完整 | 截止时间校验、人满无候补退名额 |
| **US40 活动管理（后台）** | `OpsAdminController` + `ActivityStateMachine` | ✅ 完整 | 下架/恢复状态守卫 + 原因必填 + 通知 |

### 迭代 2 — 功能完善（27 个用户故事）

| 故事 | 实现位置 | 状态 | 说明 |
|------|---------|------|------|
| **US04 商家注册** | `AuthServiceImpl.registerMerchant()` | ✅ 完整 | 含执照上传/格式校验 |
| **US05 商家资料管理** | `MerchantController` | ✅ 完整 | 资料编辑 + 昵称唯一性 |
| **US06 商家身份审核** | `MerchantServiceImpl` + `OpsAdminController` | ✅ 完整 | 审核通知 + 重复拦截 + 原因必填 |
| **US10 活动模板与克隆** | `TemplateServiceImpl` + `ActivityServiceImpl.cloneActivity()` | ✅ 完整 | 六大分类模板、克隆独立 |
| **US11 活动草稿保存与编辑** | `ActivityServiceImpl` | ✅ 完整 | draft 状态支持、恢复编辑、提交后清除 |
| **US16 高级筛选** | `DiscoveryController.filter` | ✅ 完整 | 组合 AND + 类型 OR + 城市/费用/时间/距离 |
| **US21 等待队列** | `RegistrationServiceImpl` + `WaitlistTimeoutScheduler` | ✅ 完整 | 队列加入/退出/位置查询/超时顺延 |
| **US22 扫码签到** | `CheckInServiceImpl` | ✅ 完整 | 500m 距离校验 + 重复检测 + 未报名拦截 |
| **US23 签到管理** | `CheckInServiceImpl` | ✅ 完整 | 签到名单统计 + 真实 ZXing 二维码生成 |
| **US24 活动图文总结** | `SummaryServiceImpl` | ✅ 完整 | 图文发布 + AI 图片分类 + 通知参与者 |
| **US25 用户评价活动** | `ReviewServiceImpl` | ✅ 完整 | 已签到/7天内校验、游标分页 |
| **US26 添加好友** | `SocialGraphServiceImpl` | ✅ 完整 | 好友申请/接受/拒绝、黑名单拦截 |
| **US27 好友管理** | `SocialGraphServiceImpl` | ✅ 完整 | 备注名/分组标签/双向删除 |
| **US28 关注关系** | `SocialGraphServiceImpl` | ✅ 完整 | 单向关注/互关好友/取消关注解除 |
| **US29 兴趣小队建立** | `SquadServiceImpl` | ✅ 完整 | 公开/审核加入、名额上限、标签 |
| **US30 小队发现与加入** | `SquadServiceImpl` | ✅ 完整 | 搜索/公开直接/审核/满员/黑名单 |
| **US31 解散小队** | `SquadServiceImpl.dissolve()` | ✅ 完整 | 仅队长、全员退出、活动停止 |
| **US32 小队群聊** | `ImServiceImpl` + `ImWebSocketServer` | ⚠️ 基础 | 消息发送/撤回/WebSocket；群公告/@提醒/投票/文件待实现 |
| **US33 队内活动** | `ActivityServiceImpl.create()` | ✅ 完整 | 队长/管理员发布、成员权限校验 |
| **US34 小队相册** | `FileController` | ✅ 完整 | 上传/列表/队长管理员删除权限 |
| **US35 积分榜** | `SquadService` + `RegistrationServiceImpl` | ✅ 完整 | 报名自动 +10 分、积分榜查询、手动调整 API |
| **US36 小队权限管理** | `SquadServiceImpl` | ✅ 完整 | 任命/撤销管理员、角色矩阵踢人/改角色 |
| **US37 即时通讯** | `ImServiceImpl` + `ImWebSocketServer` | ⚠️ 基础 | 发送/撤回/已读/WebSocket；转发/表情/图片/位置待实现 |
| **US38 用户查询** | `OpsAdminController` | ✅ 完整 | 按邮箱/昵称/角色/状态查询、个人/商家筛选 |
| **US39 用户封禁与解封** | `OpsAdminController.ban/unban` | ✅ 完整 | 原因必填 + 解封数据不影响 |
| **US41 小队查询** | `OpsAdminController.teams/team/teamMembers` | ✅ 完整 | 含队长信息/活动数/成员列表 |
| **US42 小队停用与恢复** | `OpsAdminController.disableTeam/restoreTeam` | ✅ 完整 | 原因必填 + 停用/恢复通知 |

---

## 数据库

启动时自动建表（21 张表 + 模板种子数据）：

`users`、`activation_tokens`、`refresh_tokens`、`login_attempts`、`merchant_profiles`、`activities`、`activity_templates`、`registrations`、`waitlist`、`reviews`、`activity_summaries`、`summary_images`、`friendships`、`follows`、`teams`、`team_members`、`team_join_requests`、`team_blacklist`、`user_bans`、`notifications`、`im_messages`

附近搜索使用 MySQL 经纬度字段 + Haversine 公式（非 PostGIS）。

---

## 核心架构设计

### 游标分页

所有列表接口使用 **N+1 游标分页**替代传统 `LIMIT OFFSET`：

- **原理**：每次查询 `limit + 1` 条，通过多出的 1 条判断 `has_more`
- **游标格式**：`{排序字段值}|{ID}`（如 `2026-06-29T17:44:17.223|f33ca6e898dac703ef2db0b7da6cf483`）
- **优势**：深翻页性能不下降、新增数据不影响偏移一致性

### JWT 双 Token 鉴权

| Token | 有效期 | 用途 |
|-------|--------|------|
| `access_token` | 2 小时 | 请求鉴权（Bearer 方式） |
| `refresh_token` | 7 天 | 无感刷新 access_token |

- **白名单**：`/auth/**`、`/discover/**`、`/ai/**`、`GET /activities/*` 等公开
- **角色**：`/admin/**` 需 `ROLE_admin`，其余接口需认证
- **匿名访问**：活动详情匿名可查看（`registration_status` 为 null）

### 内容审核流水线

```
活动提交 → DFA 快速过滤（<1ms）
             ├─ 明显违规 → 自动驳回
             ├─ 明显安全 → 自动发布
             └─ 存疑 → DeepSeek-V3.2 语义审核
                        ├─ pass → 发布
                        ├─ violation → 驳回
                        └─ uncertain/超时 → 转人工审核
```

### 活动状态机

```
draft ──→ pending_ai_review ──→ published ──→ taken_down
  │              │                    │             │
  │              ├──→ rejected        └──→ restored
  └──→ pending_manual_review ──→ published
                        │
                        └──→ rejected
```

- 人数 ≤ 50 → `pending_ai_review`（AI 自动审核）
- 人数 > 50 → `pending_manual_review`（管理员人工审核）

---

## 统一响应格式

```json
// 成功
{ "code": 0, "message": "success", "data": { ... }, "pagination": { "cursor": "...", "has_more": true, "limit": 20 } }

// 失败
{ "code": 40001, "message": "该邮箱已被注册", "data": null }
```

### 错误码

| 范围 | 说明 |
|------|------|
| 40001-40020 | 参数校验/重复/业务规则 |
| 40101-40104 | 认证失败 |
| 40300-40307 | 权限不足 |
| 40401-40406 | 资源不存在 |
| 40900-40911 | 冲突（报名/取消/状态等） |
| 42901 | 频率限制（登录锁定） |
| 50201 | 外部服务失败 |

---

## 测试覆盖

| 测试套件 | 数量 | 状态 |
|---------|:----:|:----:|
| ActivityIntegrationTest | 13 | ✅ |
| AuthIntegrationTest | 8 | ✅ |
| RegistrationIntegrationTest | 10 | ✅ |
| ReviewIntegrationTest | 5 | ✅ |
| CheckInIntegrationTest | 6 | ✅ |
| SummaryIntegrationTest | 8 | ✅ |
| SocialGraphIntegrationTest | 12 | ✅ |
| SquadIntegrationTest | 12 | ✅ |
| NotificationIntegrationTest | 6 | ✅ |
| WaitlistPositionIntegrationTest | 2 | ✅ |
| 单元测试（状态机/分页等） | 20+ | ✅ |
| **总计** | **103** | **✅ 全部通过** |

---

## 尚未实现 / 待生产化

| 项目 | 现状 | 计划 |
|------|------|------|
| **SMTP 邮件** | 激活 token 仅在响应中返回，未接入 `spring-boot-starter-mail` + SMTP | 生产前接入 |
| **对象存储** | 文件上传存储到本地磁盘（`upload/` 目录），未接入 OSS/S3/CDN | 生产前接入 |
| **消息队列** | 未接入 RabbitMQ/Redis，定时任务使用 `@Scheduled` 本地轮询 | 按需引入 |
| **高级 IM** | 群公告、@指定成员、@所有人、群投票、消息转发 | 计划后续迭代 |
| **举报系统** | 无举报模块 | 需单独设计 |
| **GIS/LBS SDK** | 距离计算使用 Haversine 公式（MySQL），未接入专业 LBS SDK | 精度需求时升级 |

> **关于"AI 服务"的说明**：AI 内容审核和 AI 活动策划已**真实接入 SiliconFlow DeepSeek-V3.2**，配置了真实 API Key，并采取 DFA 兜底降级策略，非占位实现。详见 [AI 服务](#ai-服务已真实接入) 章节。

---

*文档版本：v2.0（迭代 2 完成）*
*最后更新：2026-06-30*
