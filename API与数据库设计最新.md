# API与数据库设计

# API 与数据库详细设计

---

## 第一部分：数据库设计

### 1\.1 ER 关系总览

```Plain Text
users ──1:1── merchant_profiles
users ──1:N── activities (creator)
users ──1:N── registrations
users ──1:N── waitlist
users ──1:N── reviews
users ──1:N── notifications
users ──1:N── refresh_tokens
users ──1:N── login_attempts
users ──1:1── activation_tokens

users ──N:M── users (friendships)
users ──N:M── users (follows)

activities ──1:N── registrations
activities ──1:N── waitlist
activities ──1:N── reviews
activities ──1:1── activity_summaries
activities ──N:1── activity_templates
activities ──N:1── teams

activity_summaries ──1:N── summary_images

teams ──1:N── team_members
teams ──1:N── team_join_requests
teams ──N:M── users (team_blacklist)

users ──1:N── user_bans
```

---

### 1\.2 表定义

#### 1\.2\.1 users（用户表）

**索引：**

- UNIQUE\(email\)

- UNIQUE\(nickname\)

- INDEX\(status\)

- INDEX\(role\)

---

#### 1\.2\.2 activation\_tokens（激活令牌）

**索引：** UNIQUE\(token\), INDEX\(user\_id\)

---

#### 1\.2\.3 refresh\_tokens（刷新令牌）

**索引：** UNIQUE\(token\), INDEX\(user\_id, revoked\_at\)

---

#### 1\.2\.4 login\_attempts（登录尝试记录）

**索引：** INDEX\(email, created\_at\)

---

#### 1\.2\.5 merchant\_profiles（商家资料）

**索引：** UNIQUE\(merchant\_nickname\), INDEX\(audit\_status\)

---

#### 1\.2\.6 activities（活动表）

**索引：**

- INDEX\(creator\_id\)

- INDEX\(status\)

- INDEX\(activity\_type\)

- INDEX\(city\)

- INDEX\(fee\_type\)

- INDEX\(start\_time\)

- INDEX\(created\_at DESC\) — 最新列表

- INDEX\(location\_geom\) USING GIST — 附近搜索

- INDEX\(team\_id\) WHERE is\_team\_activity = TRUE

- GIN trigram index on \(title, description\) \+ GIN\(tags\) — 中文关键词搜索

---

#### 1\.2\.7 activity\_templates（活动模板）

**索引：** INDEX\(category\)

---

#### 1\.2\.8 registrations（报名表）

**索引：**

- UNIQUE\(activity\_id, user\_id\)

- INDEX\(activity\_id, status\)

- INDEX\(user\_id\)

---

#### 1\.2\.9 waitlist（等待队列）

**索引：**

- UNIQUE\(activity\_id, user\_id\)

- INDEX\(activity\_id, status, position\)

---

#### 1\.2\.10 reviews（评价表）

**索引：** UNIQUE\(activity\_id, user\_id\), INDEX\(activity\_id, created\_at DESC\)

---

#### 1\.2\.11 friendships（好友关系表）

**索引：**

- UNIQUE\(user\_id, friend\_id\)

- INDEX\(friend\_id, status\)

- INDEX\(user\_id, status\)

---

#### 1\.2\.12 follows（关注表）

**索引：**

- PRIMARY KEY\(follower\_id, followed\_id\)

- INDEX\(followed\_id\)

- INDEX\(follower\_id\)

---

#### 1\.2\.13 teams（小队表）

**索引：** INDEX\(status\), INDEX\(name\)

---

#### 1\.2\.14 team\_members（小队成员表）

**索引：** UNIQUE\(team\_id, user\_id\), INDEX\(user\_id\)

---

#### 1\.2\.15 team\_join\_requests（小队加入申请）

**索引：** UNIQUE\(team\_id, user\_id\) WHERE status = 'pending'（部分唯一索引）

---

#### 1\.2\.16 team\_blacklist（小队黑名单）

**索引：** PRIMARY KEY\(team\_id, user\_id\)

---

#### 1\.2\.17 activity\_summaries（活动图文总结）

---

#### 1\.2\.18 summary\_images（总结图片）

**索引：** INDEX\(summary\_id, category\)

---

#### 1\.2\.19 user\_bans（用户封禁记录）

**索引：** INDEX\(user\_id, is\_active\)

---

#### 1\.2\.20 notifications（通知表）

**通知类型枚举：**

- `friend_request` — 好友申请

- `friend_accepted` — 好友申请通过

- `waitlist_promotion` — 等待队列递补

- `activity_published` — 活动审核通过

- `activity_rejected` — 活动审核驳回

- `merchant_approved` — 商家审核通过

- `merchant_rejected` — 商家审核驳回

- `team_join_request` — 小队加入申请

- `team_approved` — 小队申请通过

- `system` — 系统通知

**索引：** INDEX\(user\_id, is\_read, created\_at DESC\)

---

#### 1\.2\.21 group\_chat\_read\_markers（群聊已读标记）

**索引：** UNIQUE\(group\_id, user\_id\), INDEX\(user\_id\)

---### 1\.3 活动关键词搜索配置

迭代 1 优先保证中文关键词可用，采用标题/简介包含匹配 \+ 标签数组匹配：

```SQL
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_activities_title_trgm
  ON activities USING GIN (title gin_trgm_ops);

CREATE INDEX idx_activities_description_trgm
  ON activities USING GIN (description gin_trgm_ops);

CREATE INDEX idx_activities_tags_gin
  ON activities USING GIN (tags);
```

> 说明：原 `to_tsvector('simple', ...)` 对中文短词检索不稳定，不适合作为本项目 MVP 的唯一搜索方案。后续如需更高相关度排序，可接入中文分词插件或独立搜索服务。
> 
> 

### 1\.4 PostGIS 空间索引

```SQL
-- 为附近搜索创建 GIST 索引
CREATE INDEX idx_activities_location_geom ON activities USING GIST(location_geom);

-- 附近活动查询示例（5km 内）
SELECT *
FROM activities
WHERE ST_DWithin(
  location_geom,
  ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
  5000
)
AND status = 'published';
```

---

---

## 第二部分：API 接口设计

### 2\.1 通用规范

#### 基础 URL

- 开发环境：`http://localhost:3000/api`

- 生产环境：`https://api.quju.example.com/api`

#### 认证方式

- Header: `Authorization: Bearer <access_token>`

- access\_token 有效期：2h

- refresh\_token 有效期：7d

#### 统一响应格式

成功：

```JSON
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "pagination": { ... }   // 仅分页接口
}
```

失败：

```JSON
{
  "code": 40001,
  "message": "该邮箱已被注册",
  "data": null
}
```

#### 错误码规范

#### 分页规范

游标分页：

```Plain Text
GET /api/activities?cursor=2024-01-01T00:00:00Z&limit=20
```

响应：

```JSON
{
  "data": [ ... ],
  "pagination": {
    "next_cursor": "2024-01-02T12:00:00Z",
    "has_more": true,
    "limit": 20
  }
}
```

---

### 2\.2 认证模块 \(Auth\)

#### POST `/api/auth/register/personal`

个人用户注册

**Request:**

```JSON
{
  "email": "user@example.com",
  "password": "Abc12345",
  "nickname": "张三"
}
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "message": "注册成功，请前往邮箱激活账号",
  "data": { "email": "user@example.com" }
}
```

**校验规则：**

- email: 合法邮箱格式

- password: 至少 8 位，包含字母和数字

- nickname: 2\-50 字

**错误码：** 40001=邮箱已注册, 40002=密码不合规, 40003=昵称已被占用

---

#### POST `/api/auth/register/merchant`

商家用户注册（multipart/form\-data）

**Request:**

```Plain Text
email: merchant@example.com
password: Abc12345
nickname: 某某商家
merchant_name: 某某餐饮管理有限公司
activity_domains: ["美食","聚会"]
license_image: <file>
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "message": "资料已提交，请等待审核",
  "data": { "email": "merchant@example.com", "audit_status": "pending" }
}
```

**文件限制：** jpg/png/pdf, 最大 10MB

---

#### POST `/api/auth/login`

用户/管理员登录

**Request:**

```JSON
{
  "email": "user@example.com",
  "password": "Abc12345"
}
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "access_token": "eyJhbG...",
    "refresh_token": "dGhpcyBp...",
    "expires_in": 7200,
    "user": {
      "id": "uuid",
      "nickname": "张三",
      "avatar_url": "https://...",
      "role": "personal",
      "status": "active"
    }
  }
}
```

**错误码：** 40101=邮箱或密码错误, 40102=账户未激活, 40103=账户已封禁, 42901=密码错误次数过多请15分钟后重试

**登录锁逻辑：** 同一邮箱 5 分钟内连续 5 次失败 → 锁定 15 分钟

---

#### POST `/api/auth/logout`

退出登录

**Header:** Authorization: Bearer `<access_token>`

**Response \(200\):**

```JSON
{ "code": 0, "message": "已退出登录" }
```

（服务端标记 refresh\_token 为 revoked）

---

#### POST `/api/auth/refresh`

刷新 Token

**Request:**

```JSON
{ "refresh_token": "dGhpcyBp..." }
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "access_token": "eyJhbG...",
    "refresh_token": "bmV3IHRv...",
    "expires_in": 7200
  }
}
```

---

#### GET `/api/auth/activate/:token`

激活邮箱

**Path:** `/api/auth/activate/:token`

**Response \(200\):**

```JSON
{ "code": 0, "message": "账号激活成功，请登录" }
```

**错误码：** 40005=激活链接无效或已过期, 40006=账号已激活

---

#### POST `/api/auth/resend-activation`

重发激活邮件

**Request:**

```JSON
{ "email": "user@example.com" }
```

**Response \(200\):**

```JSON
{ "code": 0, "message": "激活邮件已重新发送" }
```

---

### 2\.3 用户模块 \(Users\)

#### GET `/api/users/me`

获取当前用户信息

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "nickname": "张三",
    "avatar_url": "https://...",
    "gender": "male",
    "birthday": "1995-06-15",
    "bio": "热爱户外",
    "interest_tags": ["户外","徒步","摄影"],
    "role": "personal",
    "credit_score": 100,
    "created_at": "2024-01-01T00:00:00Z"
  }
}
```

---

#### PATCH `/api/users/me`

更新当前用户资料

**Request:**

```JSON
{
  "nickname": "张三丰",
  "avatar_url": "https://...",
  "gender": "male",
  "birthday": "1995-06-15",
  "bio": "热爱户外运动",
  "interest_tags": ["户外","徒步","摄影","骑行"]
}
```

**校验规则：**

- nickname: 全平台唯一

- bio: 最多 200 字

- 敏感词过滤

---

#### GET `/api/users/:id`

查看用户公开信息

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "uuid",
    "nickname": "张三",
    "avatar_url": "https://...",
    "gender": "male",
    "bio": "热爱户外",
    "interest_tags": ["户外","徒步"],
    "role": "personal",
    "credit_score": 100,
    "created_at": "2024-01-01T00:00:00Z",
    "friendship_status": "accepted",   // null | pending | accepted | blocked
    "follow_status": "following",      // null | following | followed | mutual
    "stats": {
      "activity_count": 5,
      "follower_count": 42,
      "following_count": 30
    }
  }
}
```

---

#### GET `/api/users/check-nickname`

检查昵称是否可用

**Query:** `?nickname=张三`

**Response \(200\):**

```JSON
{ "code": 0, "data": { "available": false } }
```

---

#### GET `/api/users/me/created-activities`

我创建的活动

**Query:** `?cursor=&limit=20&status=published`

分页响应。

---

#### GET `/api/users/me/joined-activities`

我报名的活动

**Query:** `?cursor=&limit=20&status=registered`

分页响应。

---

### 2\.4 活动创建模块 \(Activities\)

#### POST `/api/activities`

创建活动

**Request:**

```JSON
{
  "title": "周末奥森徒步",
  "description": "一起在北京奥林匹克森林公园徒步...",
  "tags": ["户外","徒步","周末"],
  "activity_type": "户外徒步",
  "cover_image_url": "https://...",
  "start_time": "2024-06-15T08:00:00+08:00",
  "end_time": "2024-06-15T12:00:00+08:00",
  "registration_deadline": "2024-06-14T20:00:00+08:00",
  "max_participants": 30,
  "min_credit_score": 60,
  "min_age": 18,
  "fee_type": "free",
  "fee_amount": 0,
  "city": "北京",
  "location_name": "北京奥林匹克森林公园南门",
  "location_lat": 40.0178,
  "location_lng": 116.3912,
  "status": "draft"
}
```

**校验规则：**

- title: 不能为空

- description: 不能为空

- max\_participants: \> 0

- registration\_deadline: \> now\(\)

- start\_time \< end\_time

- min\_age: \>= 0，0 表示不限年龄

- fee\_type: `free` 时 fee\_amount 必须为 0；`paid` 时 fee\_amount 必须 \>= 0

- city: 高级筛选需要，建议由地图逆地理编码自动带出并允许用户确认

**status 可选值：**

- `draft` → 保存草稿

- 非 `draft` → 提交审核

**提交审核规则：**

- `max_participants > 50` → 直接进入 `pending_manual_review`

- 其他活动 → 进入 `pending_ai_review`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "uuid-activity",
    "status": "pending_ai_review",
    "message": "活动已提交，请等待审核"
  }
}
```

---

#### PUT `/api/activities/:id`

更新活动（仅草稿或被驳回状态可编辑）

**Request:** 同 POST

---

#### GET `/api/activities/:id`

获取活动详情

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "uuid",
    "title": "周末奥森徒步",
    "description": "...",
    "tags": ["户外","徒步"],
    "activity_type": "户外徒步",
    "cover_image_url": "https://...",
    "start_time": "2024-06-15T08:00:00+08:00",
    "end_time": "2024-06-15T12:00:00+08:00",
    "registration_deadline": "2024-06-14T20:00:00+08:00",
    "max_participants": 30,
    "current_participants": 15,
    "min_credit_score": 60,
    "min_age": 18,
    "fee_type": "free",
    "fee_amount": 0,
    "city": "北京",
    "location_name": "北京奥林匹克森林公园南门",
    "location_lat": 40.0178,
    "location_lng": 116.3912,
    "status": "published",
    "review_reason": null,
    "is_team_activity": false,
    "team_id": null,
    "creator": {
      "id": "uuid",
      "nickname": "张三",
      "avatar_url": "https://..."
    },
    "registration_status": "not_registered",
    "display_status": "registering",
    "created_at": "2024-06-10T12:00:00Z"
  }
}
```

**registration\_status 可选值：**

- `not_registered` — 未报名

- `registered` — 已报名

- `cancelled` — 已取消

- `in_waitlist` — 等待队列中

**display\_status 可选值（由 status \+ 当前时间计算，不建议单独入库）：**

- `not_started` — 未开始

- `registering` — 报名中

- `registration_closed` — 报名已截止

- `in_progress` — 活动中

- `ended` — 已结束

- `taken_down` — 已下架

---

#### POST `/api/activities/:id/clone`

克隆活动

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "new-uuid",
    "title": "周末奥森徒步（副本）",
    "status": "draft"
  }
}
```

（将原活动除报名数据外的所有字段复制为新草稿）

---

#### DELETE `/api/activities/:id`

删除草稿活动（仅 draft 状态可删除）

**Response \(200\):**

```JSON
{ "code": 0, "message": "草稿已删除" }
```

---

#### GET `/api/activities/:id/participants`

获取活动报名用户列表（发起人可完整查看）

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "user_id": "uuid",
      "nickname": "李四",
      "avatar_url": "https://...",
      "status": "registered",
      "checked_in": false,
      "created_at": "2024-06-11T10:00:00Z"
    }
  ]
}
```

---

### 2\.5 活动发现模块 \(Discover\)

#### GET `/api/discover/recommended`

推荐活动信息流

**Query:** `?cursor=&limit=20`

逻辑：已设置兴趣标签 → 按标签匹配度排序；冷启动 → 按报名人数降序。

---

#### GET `/api/discover/latest`

最新活动信息流

**Query:** `?cursor=&limit=20`

逻辑：按 `created_at DESC`，仅 `status = 'published'`。

---

#### GET `/api/discover/nearby`

附近活动

**Query:** `?lat=40.0178&lng=116.3912&radius=5000&cursor=&limit=20`

逻辑：使用 PostGIS `ST_DWithin` 计算距离并排序。

若未授权位置，返回 `code=40010, message="需要位置权限"`。

---

#### GET `/api/discover/search`

关键词搜索

**Query:** `?q=徒步&cursor=&limit=20`

逻辑：对 `title`、`description` 做包含匹配，并对 `tags` 做数组包含/相等匹配；排序优先级建议为：标题命中 \> 标签命中 \> 简介命中 \> 发布时间倒序。

**Response 示例：**

```JSON
{
  "code": 0,
  "data": [ ... ],
  "pagination": { "next_cursor": "...", "has_more": true, "limit": 20 }
}
```

---

#### GET `/api/discover/filter`

高级筛选

**Query:**

```Plain Text
?activity_types=户外徒步,运动健身
&start_after=2024-06-15T00:00:00Z
&start_before=2024-06-16T00:00:00Z
&city=北京
&fee_type=free
&max_distance=5000
&lat=40.0178
&lng=116.3912
&cursor=&limit=20
```

- `activity_types`: 多选用逗号分隔（OR 逻辑）

- `city`: 按城市筛选，来自活动创建时保存的城市字段

- `fee_type`: `free` / `paid`

- `max_distance`: 需配合 `lat`/`lng`

---

#### GET `/api/discover/map`

地图模式点位

**Query:** `?sw_lat=40.00&sw_lng=116.30&ne_lat=40.04&ne_lng=116.40`

逻辑：根据可视区域边界框查活动点位，返回轻量数据。

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "id": "uuid",
      "title": "周末奥森徒步",
      "location_lat": 40.0178,
      "location_lng": 116.3912,
      "start_time": "2024-06-15T08:00:00+08:00",
      "current_participants": 15,
      "max_participants": 30
    }
  ]
}
```

---

### 2\.6 报名模块 \(Registrations\)

#### POST `/api/activities/:id/register`

报名活动

**流程：**

1. 校验活动状态 = published

2. 校验报名截止时间未过

3. 校验信誉分 ≥ 活动最低要求

4. 若活动设置最低年龄，则根据用户生日校验年龄 ≥ `min_age`

5. 校验名额未满

6. `SELECT * FROM activities WHERE id = :id FOR UPDATE`（行锁）

7. 插入报名记录 \+ `current_participants + 1`

8. 提交事务

**Request:**

```JSON
{
  "form_data": { "phone": "13800138000", "remark": "新人第一次参加" }
}
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "registration_id": "uuid",
    "status": "registered",
    "current_participants": 16
  }
}
```

**错误码：**

- 40401=活动不存在

- 40901=名额已满

- 40902=您已报名该活动

- 40903=报名已截止

- 40301=信誉分不满足要求（附带 current/required 分值）

- 40304=年龄不满足要求（附带 current/required 年龄）

---

#### POST `/api/activities/:id/cancel-registration`

取消报名

**约束：** 仅报名截止前可取消，且 status = 'registered'

**流程：**

1. 校验当前时间 \< `registration_deadline`

2. 在同一事务中：更新报名状态为 cancelled \+ `current_participants - 1`

3. 若有等待队列，触发递补逻辑

**Response \(200\):**

```JSON
{ "code": 0, "message": "已取消报名" }
```

---

#### POST `/api/activities/:id/join-waitlist`

加入等待队列

**约束：** 活动已满员，且用户未在队列中

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "position": 5,
    "waiting_count_ahead": 4
  }
}
```

---

#### DELETE `/api/activities/:id/leave-waitlist`

退出等待队列

---

### 2\.7 签到模块 \(Check\-in\)

#### POST `/api/activities/:id/check-in`

扫码签到

**Request:**

```JSON
{
  "qr_data": "activity:uuid:signature",
  "lat": 40.0179,
  "lng": 116.3913
}
```

**校验：**

- 用户已报名且未签到

- 若活动开启位置校验：计算距离 ≤ 500m

- qr\_data 签名验证

**Response \(200\):**

```JSON
{ "code": 0, "message": "签到成功" }
```

**错误码：**

- 40904=您已签到

- 40302=未报名该活动

- 40303=不在活动地点附近

---

#### GET `/api/activities/:id/check-in/list`

签到名单（发起人/管理员可见）

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "user_id": "uuid",
      "nickname": "李四",
      "avatar_url": "https://...",
      "registered_at": "2024-06-11T10:00:00Z",
      "checked_in": true,
      "checked_in_at": "2024-06-15T08:05:00Z"
    }
  ],
  "stats": {
    "total_registered": 30,
    "total_checked_in": 25,
    "check_in_rate": 0.83
  }
}
```

---

#### POST `/api/activities/:id/check-in/qrcode`

生成签到二维码（发起人操作）

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "qr_code_url": "https://...",
    "qr_data": "activity:uuid:signature",
    "expires_at": "2024-06-15T12:00:00+08:00"
  }
}
```

---

### 2\.8 评价模块 \(Reviews\)

#### POST `/api/activities/:id/reviews`

提交评价

**约束：**

- 活动已结束（end\_time \< now）

- 结束不超过 7 天

- 用户已签到

**Request:**

```JSON
{ "content": "组织得很好，风景优美！" }
```

**Response \(200\):**

```JSON
{ "code": 0, "data": { "id": "uuid", "created_at": "..." } }
```

---

#### GET `/api/activities/:id/reviews`

获取活动评价列表

**Query:** `?cursor=&limit=20`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "id": "uuid",
      "user": { "id": "uuid", "nickname": "李四", "avatar_url": "..." },
      "content": "组织得很好！",
      "created_at": "2024-06-15T14:00:00Z"
    }
  ],
  "pagination": { ... }
}
```

---

### 2\.9 模板模块 \(Templates\)

#### GET `/api/templates`

获取模板列表

**Query:** `?category=户外徒步`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "categories": ["运动健身","户外徒步","桌游聚会","学习交流","公益活动","城市探索"],
    "templates": [
      {
        "id": "uuid",
        "name": "户外徒步",
        "category": "户外徒步",
        "description": "一场轻松的户外徒步活动...",
        "tags": ["户外","徒步","周末"],
        "activity_type": "户外徒步",
        "preset_duration_minutes": 240,
        "preset_max_participants": 30
      }
    ]
  }
}
```

---

#### POST `/api/templates/:id/use`

使用模板创建活动

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "new-draft-uuid",
    "title": "户外徒步",
    "status": "draft"
  }
}
```

（返回预填充的草稿）

---

### 2\.10 AI 模块 \(AI\)

#### POST `/api/ai/generate-activity`

AI 活动策划

**Request:**

```JSON
{ "topic": "周末奥森徒步" }
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "title": "周末奥森徒步之旅",
    "description": "让我们一起在北京奥林匹克森林公园...",
    "tags": ["户外","徒步","自然","周末"],
    "activity_type": "户外徒步",
    "suggested_duration_minutes": 180,
    "suggested_max_participants": 25
  }
}
```

**错误码：** 50201=AI 生成失败请手动填写

---

#### POST `/api/ai/classify-images`

AI 图片分类（US24 活动总结用）

**Request \(multipart/form\-data\):**

```Plain Text
images: [file1, file2, file3, ...]
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    { "image_index": 0, "category": "group_photo" },
    { "image_index": 1, "category": "venue" },
    { "image_index": 2, "category": "process" }
  ]
}
```

---

### 2\.11 活动总结模块 \(Summaries\)

#### POST `/api/activities/:id/summary`

发布活动图文总结

**Request:**

```JSON
{
  "content": "本次活动圆满成功...",
  "images": [
    { "image_url": "https://...", "category": "group_photo", "sort_order": 0 },
    { "image_url": "https://...", "category": "venue", "sort_order": 1 }
  ]
}
```

**约束：** 至少 1 张图片，仅活动发起人可操作。

---

#### GET `/api/activities/:id/summary`

获取活动图文总结

---

### 2\.12 好友模块 \(Friends\)

#### POST `/api/friends/requests`

发送好友申请

**Request:**

```JSON
{ "target_user_id": "uuid" }
```

**响应逻辑：**

- 已是好友 → 409

- 在对方黑名单中 → 403

- 对方已发送申请 → 自动升级为好友（互关逻辑）

---

#### GET `/api/friends/requests/received`

收到的申请列表

**Query:** `?status=pending&cursor=&limit=20`

---

#### GET `/api/friends/requests/sent`

发出的申请列表

---

#### POST `/api/friends/requests/:id/accept`

接受好友申请

---

#### POST `/api/friends/requests/:id/reject`

拒绝好友申请

---

#### GET `/api/friends`

好友列表

**Query:** `?group_tag=徒步搭子&cursor=&limit=50`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "friend_id": "uuid",
      "nickname": "小王",
      "avatar_url": "https://...",
      "remark_name": "小王（徒步）",
      "group_tags": ["徒步搭子"],
      "created_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

---

#### DELETE `/api/friends/:userId`

删除好友（双向解除）

---

#### PATCH `/api/friends/:userId`

修改备注名/分组

**Request:**

```JSON
{
  "remark_name": "小王（徒步）",
  "group_tags": ["徒步搭子","周末伙伴"]
}
```

---

### 2\.13 关注模块 \(Follows\)

#### POST `/api/follows/:userId`

关注用户

**逻辑：** 若对方已关注我 → 自动升级为好友；否则 → 单向关注。

---

#### DELETE `/api/follows/:userId`

取消关注

**逻辑：** 若之前是互关好友 → 同时解除好友关系。

---

#### GET `/api/users/:id/followers`

粉丝列表（分页）

---

#### GET `/api/users/:id/following`

关注列表（分页）

---

### 2\.14 小队模块 \(Teams\)

#### POST `/api/teams`

创建小队

**Request:**

```JSON
{
  "name": "周末徒步小队",
  "description": "热爱徒步的一群人",
  "interest_tags": ["户外","徒步"],
  "join_type": "review",
  "max_members": 50,
  "avatar_url": "https://..."
}
```

---

#### GET `/api/teams`

小队发现列表

**Query:** `?q=徒步&cursor=&limit=20`

---

#### GET `/api/teams/:id`

小队详情（含队长信息、成员列表、活动数）

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "uuid",
    "name": "周末徒步小队",
    "description": "热爱徒步的一群人",
    "interest_tags": ["户外","徒步"],
    "join_type": "review",
    "max_members": 50,
    "current_members": 10,
    "avatar_url": "https://...",
    "status": "active",
    "created_at": "2024-01-01T00:00:00Z",
    "updated_at": "2024-06-01T00:00:00Z",
    "leader": {
      "id": "uuid",
      "nickname": "张三",
      "avatar_url": "https://..."
    },
    "activity_count": 5,
    "members": [
      {
        "user_id": "uuid",
        "role": "leader",
        "points": 100,
        "nickname": "张三",
        "avatar_url": "https://...",
        "joined_at": "2024-01-01T00:00:00Z"
      },
      {
        "user_id": "uuid",
        "role": "member",
        "points": 30,
        "nickname": "李四",
        "avatar_url": "https://...",
        "joined_at": "2024-01-05T00:00:00Z"
      }
    ]
  }
}
```

---

#### GET `/api/teams/:id/members`

获取小队成员列表

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "id": "uuid",
      "user_id": "uuid",
      "role": "leader",
      "points": 100,
      "joined_at": "2024-01-01T00:00:00Z",
      "nickname": "张三",
      "avatar_url": "https://...",
      "email": "user@example.com"
    }
  ]
}
```

---

#### PATCH `/api/teams/:id`

修改小队信息（仅队长）

---

#### POST `/api/teams/:id/join`

加入小队

- `join_type = public` → 直接加入

- `join_type = review` → 发送申请

---

#### POST `/api/teams/:id/leave`

退出小队

---

#### PATCH `/api/teams/:id/members/:userId/role`

修改成员角色（队长操作）

**Request:**

```JSON
{ "role": "admin" }
```

---

#### DELETE `/api/teams/:id/members/:userId`

移出成员（队长/管理员操作）

---

#### POST `/api/teams/:id/dissolve`

解散小队（仅队长）

---

#### GET `/api/teams/:id/join-requests`

加入申请列表（队长/管理员可见）

---

#### POST `/api/teams/:id/join-requests/:requestId/approve`

通过申请

---

#### POST `/api/teams/:id/join-requests/:requestId/reject`

拒绝申请

---

### 2\.15 通知模块 \(Notifications\)

#### GET `/api/notifications`

通知列表

**Query:** `?type=friend_request&is_read=false&cursor=&limit=20`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "id": "uuid",
      "type": "friend_request",
      "title": "新的好友申请",
      "content": "王五 请求添加你为好友",
      "is_read": false,
      "metadata": { "from_user_id": "uuid" },
      "created_at": "2024-06-15T10:00:00Z"
    }
  ],
  "pagination": { ... }
}
```

---

#### PATCH `/api/notifications/:id/read`

标记已读

---

#### POST `/api/notifications/read-all`

全部标记已读

---

### 2\.16 管理后台模块 \(Admin\)

> 所有后台接口需要 `role = 'admin'` 权限
> 
> 

#### GET `/api/admin/users`

用户查询

**Query:** `?q=邮箱或昵称&role=personal&status=active&cursor=&limit=20`

---

#### GET `/api/admin/users/:id`

用户详情（完整信息，但无修改入口）

---

#### POST `/api/admin/users/:id/ban`

封禁用户

**Request:**

```JSON
{
  "reason": "发布违规内容",
  "expires_at": "2024-07-15T00:00:00Z"
}
```

`expires_at` 不传 = 永久封禁。

---

#### POST `/api/admin/users/:id/unban`

解封用户

---

#### GET `/api/admin/merchants/pending`

待审核商家列表

---

#### POST `/api/admin/merchants/:id/approve`

通过商家审核

---

#### POST `/api/admin/merchants/:id/reject`

驳回商家审核

**Request:**

```JSON
{ "reason": "营业执照不清晰，请重新上传" }
```

---

#### GET `/api/admin/activities`

活动管理列表

**Query:** `?status=published&q=关键词&cursor=&limit=20`

---

#### POST `/api/admin/activities/:id/review`

活动审核

**Request:**

```JSON
{
  "action": "approve",
  "reason": ""
}
```

`action` 可选值：`approve` \| `reject` \| `request_changes`

---

#### POST `/api/admin/activities/:id/take-down`

下架活动

**Request:**

```JSON
{ "reason": "内容不符合平台规范" }
```

---

#### POST `/api/admin/activities/:id/restore`

恢复活动

---

#### GET `/api/admin/teams`

小队管理列表

---

#### GET `/api/admin/teams/:id`

小队详情

---

#### GET `/api/admin/teams/:id/members`

成员列表（需 admin 角色）

**Header:** Authorization: Bearer `<access_token>`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "id": "uuid",
      "user_id": "uuid",
      "role": "leader",
      "points": 100,
      "joined_at": "2024-01-01T00:00:00Z",
      "nickname": "张三",
      "avatar_url": "https://...",
      "email": "user@example.com"
    }
  ]
}
```

---

#### POST `/api/admin/teams/:id/disable`

停用小队

**Request:**

```JSON
{ "reason": "违规运营" }
```

---

#### POST `/api/admin/teams/:id/restore`

恢复小队

---

### 2\.17 文件上传模块 \(Files\)

#### POST `/api/files/upload`

文件上传

**Request:** multipart/form\-data

```Plain Text
file: <file>
type: avatar | activity_cover | activity_image | license | team_avatar | team_album
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "url": "https://cdn.quju.example.com/uploads/xxx.jpg",
    "key": "uploads/xxx.jpg"
  }
}
```

**限制：**

- avatar: 2MB, jpg/png

- license: 10MB, jpg/png/pdf

- activity\_image: 10MB, jpg/png

---

### 2\.18 即时通讯模块 \(IM\)

> 即时通讯支持私聊和群聊两种模式。私聊基于好友关系，仅互为好友的用户才能发送消息；群聊基于小队（Teams）成员关系，仅小队成员可发送和接收群消息。私聊与群聊共用 `im_messages` 表，通过 `entity_type` 区分。
> 
> - `entity_type = "private"` 私聊，`entity_id` 格式为 `"{userAId}:{userBId}"`（字母序拼接）
> - `entity_type = "group"` 群聊，`entity_id` 格式为 `"team:{teamId}"`
> 
> 

#### WebSocket `/ws/im?token={jwt_token}`

建立实时消息连接。

**认证方式：** URL 参数 `token` 传入 JWT access\_token 进行鉴权。

**支持的消息类型：**

| 类型 | 方向 | 说明 |
|------|------|------|
| `send_message` | 客户端 → 服务端 | 发送聊天消息 |
| `new_message` | 服务端 → 客户端 | 新消息推送 |
| `typing` | 双向 | 输入状态提示 |
| `mark_read` | 客户端 → 服务端 | 标记会话已读 |
| `read_ack` | 服务端 → 客户端 | 已读确认回执 |
| `ping` | 客户端 → 服务端 | 心跳 |
| `pong` | 服务端 → 客户端 | 心跳回复 |
| `error` | 服务端 → 客户端 | 错误消息 |

**send\_message 请求格式：**

**私聊：**
```JSON
{
  "type": "send_message",
  "entity_type": "private",
  "entity_id": "userAId:userBId",
  "content": "你好！",
  "msg_type": "text"
}
```

**群聊：**
```JSON
{
  "type": "send_message",
  "entity_type": "group",
  "entity_id": "team:{teamId}",
  "content": "大家好！",
  "msg_type": "text"
}
```

**校验规则：**
- `entity_type = "private"` → 校验双方是否为好友关系（双向 accepted）
- `entity_type = "group"` → 校验发送方是否为 `entity_id` 对应小队的成员

**new\_message 推送格式：**

**私聊推送：**
```JSON
{
  "type": "new_message",
  "message_id": "uuid",
  "entity_type": "private",
  "entity_id": "userAId:userBId",
  "sender_id": "uuid",
  "content": "你好！",
  "created_at": "2024-06-15T10:00:00"
}
```

**群聊推送：**（广播给所有在线群成员）
```JSON
{
  "type": "new_message",
  "message_id": "uuid",
  "entity_type": "group",
  "entity_id": "team:{teamId}",
  "sender_id": "uuid",
  "content": "大家好！",
  "created_at": "2024-06-15T10:00:00"
}
```

**typing 格式：**

**私聊：**
```JSON
{
  "type": "typing",
  "entity_type": "private",
  "entity_id": "userAId:userBId",
  "user_id": "uuid"
}
```

**群聊：**（广播给除发送方外的所有在线群成员）
```JSON
{
  "type": "typing",
  "entity_type": "group",
  "entity_id": "team:{teamId}",
  "user_id": "uuid"
}
```

**mark\_read 格式：**

**私聊：**
```JSON
{
  "type": "mark_read",
  "entity_type": "private",
  "entity_id": "userAId:userBId"
}
```

**群聊：**（更新用户在群中的最后阅读时间）
```JSON
{
  "type": "mark_read",
  "entity_type": "group",
  "entity_id": "team:{teamId}"
}
```

---

#### POST `/api/im/messages`

发送消息（REST 方式，与 WebSocket 二选一）

**Header:** Authorization: Bearer `<access_token>`

**Request:**

**私聊：**
```JSON
{
  "entity_type": "private",
  "entity_id": "userAId:userBId",
  "type": "text",
  "content": "你好！",
  "mention_all": false,
  "mention_user_ids": [],
  "metadata": {}
}
```

**群聊：**
```JSON
{
  "entity_type": "group",
  "entity_id": "team:{teamId}",
  "type": "text",
  "content": "大家好！",
  "mention_all": false,
  "mention_user_ids": [],
  "metadata": {}
}
```

**校验规则：**
- `entity_type = "private"` → 校验当前用户与对方是否为好友关系
- `entity_type = "group"` → 校验当前用户是否为该小队的成员
- `entity_id` 私聊格式为两个 userId 按字母序拼接，以 `:` 分隔；群聊格式为 `team:` + 小队ID

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "id": "uuid",
    "entity_type": "private",
    "entity_id": "userAId:userBId",
    "sender_id": "uuid",
    "type": "text",
    "content": "你好！",
    "recalled": false,
    "created_at": "2024-06-15T10:00:00"
  }
}
```

**错误码：**
- 40300=不是好友关系，无法发送消息
- 40300=您不是该群聊成员，无法发送消息
- 40000=私聊entity_id格式错误

---

#### POST `/api/im/messages/{id}/recall`

撤回消息

**Header:** Authorization: Bearer `<access_token>`

**约束：** 仅消息发送者可撤回，且发送时间不超过 2 分钟

**Response \(200\):**

```JSON
{ "code": 0, "message": "success" }
```

**错误码：** 40405=消息不存在, 40300=只能撤回自己的消息, 40920=消息已超过可撤回时间（2分钟）, 40920=消息已被撤回

---

#### GET `/api/im/messages`

获取消息历史（游标分页，按时间倒序）

**Header:** Authorization: Bearer `<access_token>`

**Query:**

```Plain Text
?entity_type=private
&entity_id=userAId:userBId
&cursor=2024-06-15T10:00:00
&limit=20
```

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "uuid",
        "entity_type": "private",
        "entity_id": "userAId:userBId",
        "sender_id": "uuid",
        "type": "text",
        "content": "你好！",
        "recalled": false,
        "read_at": "2024-06-15T10:01:00",
        "created_at": "2024-06-15T10:00:00"
      }
    ],
    "nextCursor": "2024-06-15T09:59:00",
    "hasMore": true,
    "limit": 20
  }
}
```

---

#### GET `/api/im/conversations`

获取会话列表（按最后消息时间倒序），返回混合了私聊和群聊的列表。

**Header:** Authorization: Bearer `<access_token>`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": [
    {
      "entity_type": "private",
      "entity_id": "userAId:userBId",
      "other_user_id": "uuid",
      "other_nickname": "张三",
      "other_avatar_url": "https://...",
      "last_message": "你好！",
      "last_message_type": "text",
      "last_message_time": "2024-06-15T10:00:00",
      "last_sender_id": "uuid",
      "last_message_recalled": false,
      "unread_count": 3
    },
    {
      "entity_type": "group",
      "entity_id": "team:{teamId}",
      "group_id": "uuid",
      "group_name": "周末徒步小队",
      "group_avatar_url": "https://...",
      "last_message": "大家好！",
      "last_message_type": "text",
      "last_message_time": "2024-06-15T09:30:00",
      "last_sender_id": "uuid",
      "last_message_recalled": false,
      "unread_count": 5
    }
  ]
}
```

**响应字段说明：**
- `entity_type = "private"` 时，返回 `other_user_id`、`other_nickname`、`other_avatar_url`
- `entity_type = "group"` 时，返回 `group_id`、`group_name`、`group_avatar_url`

---

#### POST `/api/im/messages/read`

标记会话已读（私聊和群聊均支持）

**Header:** Authorization: Bearer `<access_token>`

**Query:**

**私聊：**
```Plain Text
?entity_type=private&entity_id=userAId:userBId
```

**群聊：**
```Plain Text
?entity_type=group&entity_id=team:{teamId}
```

**处理逻辑：**
- 私聊：将对方发送的未读消息逐条标记 `read_at`
- 群聊：更新 `group_chat_read_markers` 表中该用户在该群聊的 `last_read_at` 为当前时间

**Response \(200\):**

```JSON
{ "code": 0, "message": "success" }
```

---

#### GET `/api/im/messages/unread-count`

获取指定会话的未读消息数（私聊和群聊均支持）

**Header:** Authorization: Bearer `<access_token>`

**Query:**

**私聊：**
```Plain Text
?entity_type=private&entity_id=userAId:userBId
```

**群聊：**
```Plain Text
?entity_type=group&entity_id=team:{teamId}
```

**未读数计算方式：**
- 私聊：统计对方发送且 `read_at IS NULL` 的消息数
- 群聊：统计 `created_at` 大于用户 `last_read_at` 且非自己发送的消息数

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": { "count": 3 }
}
```

---

#### GET `/api/im/messages/total-unread`

获取所有会话的总未读消息数（含私聊和群聊）

**Header:** Authorization: Bearer `<access_token>`

**Response \(200\):**

```JSON
{
  "code": 0,
  "data": { "count": 10 }
}
```

---

### 2\.19 API 汇总表

> ✓ = 需 JWT, † = 需 admin 角色
> 
> 

| 模块 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| Teams | GET | `/api/teams/:id/members` | ✓ | 成员列表 |
| Admin | GET | `/api/admin/teams/:id/members` | ✓† | 成员列表 |

---

## 第三部分：Nest\.js 模块拆分建议

```Plain Text
src/
├── app.module.ts
├── common/
│   ├── guards/
│   │   ├── jwt-auth.guard.ts
│   │   └── roles.guard.ts
│   ├── decorators/
│   │   ├── current-user.ts
│   │   └── roles.ts
│   ├── filters/
│   │   └── http-exception.filter.ts
│   ├── interceptors/
│   │   └── transform.interceptor.ts
│   └── dto/
│       └── pagination.dto.ts
├── auth/
│   ├── auth.module.ts
│   ├── auth.controller.ts
│   ├── auth.service.ts
│   ├── dto/
│   └── strategies/
│       ├── jwt.strategy.ts
│       └── jwt-refresh.strategy.ts
├── users/
│   ├── users.module.ts
│   ├── users.controller.ts
│   ├── users.service.ts
│   └── dto/
├── activities/
│   ├── activities.module.ts
│   ├── activities.controller.ts
│   ├── activities.service.ts
│   └── dto/
├── registrations/
│   ├── registrations.module.ts
│   ├── registrations.controller.ts
│   ├── registrations.service.ts
│   └── dto/
├── discover/
│   ├── discover.module.ts
│   ├── discover.controller.ts
│   └── discover.service.ts
├── reviews/
├── templates/
├── ai/
│   ├── ai.module.ts
│   ├── ai.controller.ts
│   └── ai.service.ts
├── checkin/
├── friends/
├── follows/
├── teams/
├── notifications/
├── files/
├── admin/
│   ├── admin.module.ts
│   ├── admin.controller.ts
│   └── admin.service.ts
└── prisma/
    ├── schema.prisma
    └── migrations/
```

每个模块遵循 `Module → Controller → Service → DTO` 结构，对应一个 Epic 或子领域。迭代 1 仅需实现 `auth`、`users`、`activities`、`discover`、`registrations`、`admin` 六个模块。

---

## 第四部分：迭代 1 最小表集

仅建以下表即可支撑 P0 闭环：

> 迭代 1 暂不建：merchant\_profiles、friendships、follows、teams 系列、reviews、waitlist、summaries、templates。
> 
> 



