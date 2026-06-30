# QujuBackend

Spring Boot 3 + MySQL 后端框架，按 `API与数据库设计最新.md` 搭建了可运行的分层骨架。

## 重要说明：注册、登录与邮箱激活

当前项目已经实现了“邮箱 + 密码注册、账号激活、登录、JWT 双 Token、刷新、退出”的后端闭环，但**尚未接入真实 SMTP 邮件发送服务**。也就是说，代码会生成激活令牌并保存到数据库，但目前不会真的向用户邮箱发送激活链接。

已实现：

- 个人用户注册：`POST /api/auth/register/personal`
- 商家用户注册：`POST /api/auth/register/merchant`
- 密码使用 BCrypt 加密保存。
- 注册时写入 `users` 和 `activation_tokens`，激活令牌 24 小时过期。
- 未激活用户状态为 `pending_activation`，登录时会被拦截。
- 激活接口：`GET /api/auth/activate/{token}` 或 `GET /api/auth/activate?token=...`
- 登录接口：`POST /api/auth/login`，成功后返回 `access_token` 和 `refresh_token`。
- 刷新接口：`POST /api/auth/refresh`，退出接口：`POST /api/auth/logout`。
- 商家注册会创建 `merchant_profiles`，初始审核状态为 `pending`，后台审核接口已做基础实现。

开发测试时的激活方式：

1. 调用注册接口。
2. 从响应 `data.activation_token` 中复制激活令牌。
3. 访问 `http://localhost:3000/api/auth/activate/{activation_token}` 完成激活。
4. 激活成功后再调用登录接口。

示例：

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:3000/api/auth/activate/这里替换为activation_token"
```

尚未实现的生产级能力：

- 未添加 `spring-boot-starter-mail`。
- 未配置 `spring.mail.*` SMTP 参数。
- 未实现 `MailService` / `JavaMailSender` 发送真实激活邮件。
- `POST /api/auth/resend-activation` 当前只会重新生成激活令牌，不会发送邮件。
- 商家营业执照当前按 `licenseImageUrl` 等 JSON 字段提交，尚未实现 multipart 文件上传注册。
- 生产环境不应在注册响应里返回 `activation_token`，应改为只发送邮件链接。

如果后续要补全真实邮箱激活，需要增加邮件依赖和 SMTP 配置，在注册和重发激活令牌后调用邮件服务发送类似 `http://localhost:3000/api/auth/activate/{token}` 的激活链接。

## 技术栈

- Java 21
- Spring Boot 3.5.16
- MyBatis-Plus 3.5.9
- MySQL 8+
- JWT、Spring Security、WebSocket、Validation

## 启动步骤

1. 确认本机 MySQL 服务已启动，并修改 `src/main/resources/application.yml` 中的 MySQL 用户名和密码。

2. 启动后端：

```powershell
.\mvnw.cmd spring-boot:run
```

启动时会自动创建 `quju_platform` 数据库并执行 `src/main/resources/db/schema.sql` 建表。

如需单独编译：

```powershell
.\mvnw.cmd -DskipTests package
```

也可以直接运行已构建 jar：

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/quju-platform-0.0.1-SNAPSHOT.jar
```

服务地址：`http://localhost:3000/api`

## 需求实现对照

当前项目优先保证后端可以启动、MySQL 自动建库建表、核心接口能跑通。安全、权限、分页、推荐排序、第三方服务等生产级能力目前以简化实现为主。

### 基础设施

已实现：

- Spring Boot 3.5.16 + Java 21 + MyBatis-Plus 3.5.9 项目骨架。
- MySQL 数据源配置，启动时自动创建 `quju_platform` 数据库并执行 `src/main/resources/db/schema.sql`。
- 统一响应结构 `ApiResponse`、业务异常 `BusinessException`、全局异常处理。
- JWT 生成与解析工具，Spring Security 无状态过滤器。
- MyBatis-Plus 分页插件、Mapper XML、基础实体/Mapper/Service/Controller 分层。
- 本地文件上传目录配置 `quju.files.upload-dir`。
- 已实现强制 JWT 鉴权，URL 白名单 + 其余 authenticated()

简化或占位：

- 多数需要“当前用户”的接口支持 `Authorization`，也支持开发期 `X-User-Id` 或默认 `dev-user`。
- Redis、RabbitMQ、SMTP 邮件、真实 AI/CV/GIS SDK 均未接入，相关组件是本地占位实现。

### 数据库

已实现：

- 已建表：`users`、`activation_tokens`、`refresh_tokens`、`login_attempts`、`merchant_profiles`、`activities`、`activity_templates`、`registrations`、`waitlist`、`reviews`、`activity_summaries`、`summary_images`、`friendships`、`follows`、`teams`、`team_members`、`team_join_requests`、`team_blacklist`、`user_bans`、`im_messages`。
- `activities` 已包含新版字段：`min_age`、`fee_type`、`fee_amount`、`city`。
- 初始化模板数据：`tpl_hiking`、`tpl_board_game`。
- 附近搜索使用 MySQL 经纬度字段和 Haversine 公式实现。

未实现或与文档不同：

- 文档中的 PostGIS/GIST/GIN/trigram 索引属于 PostgreSQL 方案，当前项目使用 MySQL，因此未实现 PostGIS 几何字段和空间索引。
- `notifications` 通知表和通知模块尚未实现。
- 部分文档中的条件唯一索引、JSON 高级索引在 MySQL 版本里做了简化。

### 认证模块 Auth

已实现接口：

- `POST /api/auth/register/personal`：个人注册，写入用户和激活 token。
- `POST /api/auth/register/merchant`：商家注册，写入用户和商家资料。
- `POST /api/auth/login`：登录，校验 BCrypt 密码，返回 access token 和 refresh token。
- `POST /api/auth/refresh`：刷新 token，并吊销旧 refresh token。
- `POST /api/auth/logout`：吊销 refresh token。
- `GET /api/auth/activate/{token}` 与 `GET /api/auth/activate?token=`：激活账号。
- `POST /api/auth/resend-activation`：重新生成激活 token。

简化或未实现：

- 未发送真实激活邮件，当前只生成激活 token。
- 商家注册当前使用 JSON DTO，不是文档中的 multipart/form-data 文件上传流程。

### 用户模块 Users

已实现接口：

- `GET /api/users/me`：获取当前用户。
- `PATCH /api/users/me`：更新当前用户部分资料。
- `GET /api/users/{id}`：查看用户信息。
- `GET /api/users/check-nickname`：检查昵称是否可用。

简化或未实现：

- 未实现敏感词过滤、好友状态、关注状态。

### 活动模块 Activities

已实现接口：

- `POST /api/activities`：创建活动。
- `PUT /api/activities/{id}`：更新活动。
- `GET /api/activities/{id}`：活动详情。
- `POST /api/activities/{id}/clone`：克隆活动为草稿。
- `DELETE /api/activities/{id}`：删除草稿活动。
- `GET /api/activities/{id}/participants`：报名记录列表。
- `POST /api/activities/{id}/submit`：提交审核状态流转。

已实现规则：

- 活动字段包含标题、描述、标签、类型、封面、时间、报名截止、人数、信誉分、最低年龄、费用类型、费用金额、城市、经纬度、小队活动字段。
- 创建活动时支持 `status=draft` 保存草稿；非草稿进入审核。
- `max_participants > 50` 进入 `pending_manual_review`，否则进入 `pending_ai_review`。
- 校验 `start_time < end_time`、`fee_type`、`fee_amount`。

简化或未实现：

- 未实现严格的人工审核权限校验、内容安全审核。
- 活动状态机只覆盖基础状态流转。

### 活动发现 Discover

已实现接口：

- `GET /api/discover/latest`：最新活动。
- `GET /api/discover/recommended`：推荐活动。
- `GET /api/discover/search`：关键词搜索。
- `GET /api/discover/filter`：筛选搜索。
- `GET /api/discover/nearby`：附近活动。
- `GET /api/discover/map`：当前复用附近活动查询。

已实现能力：

- `latest` 默认返回 `published` 活动，按创建时间倒序。
- `search/filter` 支持 `q`、`type`、`activity_types`、`city`、`fee_type`、`start_after/start_before`。
- `nearby` 支持 `lat`、`lng`、`radius`/`max_distance`。

简化或未实现：

- 关键词搜索当前主要匹配 title/description，未实现标签命中优先级排序。

### 报名与候补 Registrations

已实现接口：

- `POST /api/activities/{id}/register`：报名活动。
- `POST /api/activities/{id}/cancel-registration`：取消报名。
- `POST /api/activities/{id}/join-waitlist`：加入候补队列。
- `DELETE /api/activities/{id}/leave-waitlist`：退出候补队列。

已实现规则：

- 校验活动存在、活动状态为 `published`、报名未截止、未重复报名。
- 校验信誉分、最低年龄、名额上限。
- 报名成功写入 registration 并增加 `current_participants`。

简化或未实现：

- 未使用 `SELECT ... FOR UPDATE` 行锁，当前性能和并发安全不是重点。
- 候补队列仅记录 FIFO 位置，未实现名额释放后的自动递补、TTL 通知、MQ 延迟队列。
- 候补加入未严格校验“活动已满员且用户未在队列中”。

### 签到 Check-in

已实现接口：

- `POST /api/activities/{id}/check-in`：扫码签到。
- `GET /api/activities/{id}/check-in/list`：签到列表。
- `POST /api/activities/{id}/check-in/qrcode`：生成签到二维码。

已实现规则：

- 校验活动存在、签到码匹配、用户已报名、不可重复签到。
- 开启位置校验时计算距离，超过 500m 返回错误。
- 签到成功将报名状态更新为 `checked_in`。

简化或未实现：

- 二维码是本地生成的 data URI，占位签名，不是真正的动态安全签名。
- 未实现发起人/管理员权限控制。
- 签到列表未聚合用户昵称头像，也未返回完整 stats。

### 评价 Reviews

已实现接口：

- `POST /api/activities/{id}/reviews`：提交评价。
- `GET /api/activities/{id}/reviews`：评价列表。

简化或未实现：

- 未聚合评价用户信息。

### 模板 Templates

已实现接口：

- `GET /api/templates`：模板列表，支持 `category`。
- `POST /api/templates/{id}/use`：使用模板创建草稿活动。

简化或未实现：

- 当前只初始化了少量系统模板。
- 模板实例化只填充基础字段，未覆盖所有活动字段。

### AI 模块

已实现接口：

- `POST /api/ai/generate-activity`：根据 topic 返回活动策划结构。
- `POST /api/ai/classify-images`：按上传图片数量返回分类结果。

简化或未实现：

- 未接入真实 LLM/CV 服务，当前为本地规则/占位返回。
- 未实现异步生成、内容安全审核、失败重试。

### 活动总结 Summaries

已实现接口：

- `POST /api/activities/{id}/summary`：创建活动总结。
- `GET /api/activities/{id}/summary`：获取活动总结。

简化或未实现：

- 当前只保存 summary 主表内容，没有保存 `images` 数组到 `summary_images`。
- 未校验至少 1 张图片、活动发起人权限。
- 未接入 CV 自动分类结果回写。

### 好友与关注 Friends/Follows

已实现接口：

- `POST /api/friends/requests`：发送好友申请。
- `POST /api/friends/requests/{id}/accept`：接受申请。
- `POST /api/friends/requests/{id}/reject`：拒绝申请。
- `GET /api/friends`：好友列表。
- `DELETE /api/friends/{targetUserId}`：删除好友。
- `POST /api/follows/{targetUserId}`：关注用户。
- `DELETE /api/follows/{targetUserId}`：取消关注。

简化或未实现：

- 未实现收到/发出的好友申请列表。
- 未实现好友备注和分组修改接口 `PATCH /api/friends/{userId}`。
- 未实现黑名单阻断、互关自动升级好友、双向好友记录完整维护。
- 未实现粉丝列表和关注列表接口。

### 小队 Teams

已实现接口：

- `POST /api/teams`：创建小队。
- `GET /api/teams`：小队列表。
- `GET /api/teams/{id}`：小队详情。
- `POST /api/teams/{id}/join`：加入小队或提交申请。
- `POST /api/teams/{id}/dissolve`：解散小队。

简化或未实现：

- 未实现修改小队信息、退出小队、修改成员角色、移出成员。
- 未实现加入申请列表、通过/拒绝申请。
- 未实现队长/管理员权限校验、黑名单、共享文件、相册、实时积分排行榜。

### 通知 Notifications

未实现：

- `GET /api/notifications`
- `PATCH /api/notifications/{id}/read`
- `POST /api/notifications/read-all`
- 通知表、通知实体、通知创建事件均未实现。

### 管理后台 Admin

已实现接口：

- `GET /api/admin/users`：用户查询。
- `GET /api/admin/users/{id}`：用户详情。
- `POST /api/admin/users/{id}/ban`：封禁用户。
- `POST /api/admin/users/{id}/unban`：解封用户。
- `GET /api/admin/merchants/pending`：待审核商家。
- `POST /api/admin/merchants/{id}/approve`：通过商家。
- `POST /api/admin/merchants/{id}/reject`：驳回商家。
- `GET /api/admin/activities`：活动列表。
- `POST /api/admin/activities/{id}/review`：活动审核。
- `POST /api/admin/activities/{id}/take-down`：下架活动。
- `POST /api/admin/activities/{id}/restore`：恢复活动。
- `GET /api/admin/teams`：小队列表。
- `GET /api/admin/teams/{id}`：小队详情。
- `POST /api/admin/teams/{id}/disable`：停用小队。
- `POST /api/admin/teams/{id}/restore`：恢复小队。

简化或未实现：

- 用户封禁暂未处理 `expires_at` 自动解封。
- 活动审核未接入 AI 审核结果和复杂工作流。
- 小队停用未保存停用原因。

### 文件上传 Files

已实现接口：

- `POST /api/files/upload`：上传文件到本地 `uploads/{type}` 目录，返回本地 URL 和 key。

简化或未实现：

- 未按类型限制文件大小和文件格式。
- 未接入对象存储/CDN。
- 未实现静态资源访问映射的完整生产配置。

### 即时通讯 IM

已实现接口：

- `POST /api/im/messages`：发送消息并入库。
- `POST /api/im/messages/{id}/recall`：2 分钟内撤回消息。

已包含组件：

- WebSocket endpoint、消息编码器、会话管理器骨架。

简化或未实现：

- 未实现完整 WebSocket 鉴权、房间广播、ACK 回执、离线消息、跨节点路由。
- 撤回未校验发送者本人。

### 迭代优先级建议

优先补齐：

- 强制 JWT 鉴权和 `role=admin` 权限控制。
- 用户上下文统一从 JWT 获取，移除默认 `dev-user`。
- 活动编辑权限、报名并发锁、取消报名时间限制。
- 游标分页和统一分页响应。
- 通知模块。

后续增强：

- SMTP 激活邮件、登录失败锁定、文件格式校验。
- 推荐排序、标签搜索、地图边界框查询。
- 候补递补通知、RabbitMQ/Redis。
- 真实 LLM/CV/GIS 服务接入。

## 快速接口检查

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:3000/api/ai/generate-activity' `
  -ContentType 'application/json' `
  -Body '{"topic":"周末奥森徒步"}'
```
