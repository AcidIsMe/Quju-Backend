# API 与数据库详细设计

---

## 第一部分：数据库设计

### 1.1 ER 关系总览

```
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

### 1.2 表定义

#### 1.2.1 users（用户表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK, DEFAULT gen_random_uuid() | 主键 |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 邮箱 |
| password_hash | VARCHAR(255) | NOT NULL | bcrypt 哈希 |
| nickname | VARCHAR(50) | NOT NULL, UNIQUE | 昵称 |
| avatar_url | VARCHAR(500) | | 头像 URL |
| gender | VARCHAR(10) | CHECK IN ('male','female','other') | 性别 |
| birthday | DATE | | 生日（用于报名年龄校验） |
| bio | VARCHAR(200) | | 个性签名 |
| interest_tags | JSONB | DEFAULT '[]' | 兴趣标签数组 |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'personal', CHECK IN ('personal','merchant','admin') | 角色 |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'pending_activation', CHECK IN ('pending_activation','pending_merchant_review','active','banned') | 账号状态 |
| credit_score | INT | DEFAULT 100 | 信誉分 |
| location_lat | DECIMAL(10,7) | | 最近纬度 |
| location_lng | DECIMAL(10,7) | | 最近经度 |
| location_updated_at | TIMESTAMPTZ | | 位置更新时间 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 注册时间 |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 更新时间 |

**索引：**
- UNIQUE(email)
- UNIQUE(nickname)
- INDEX(status)
- INDEX(role)

---

#### 1.2.2 activation_tokens（激活令牌）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| token | VARCHAR(255) | NOT NULL, UNIQUE | 激活令牌 |
| expires_at | TIMESTAMPTZ | NOT NULL | 过期时间（24h） |
| used_at | TIMESTAMPTZ | | 使用时间 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 创建时间 |

**索引：** UNIQUE(token), INDEX(user_id)

---

#### 1.2.3 refresh_tokens（刷新令牌）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| token | VARCHAR(500) | NOT NULL, UNIQUE | 刷新令牌 |
| expires_at | TIMESTAMPTZ | NOT NULL | 过期时间（7d） |
| revoked_at | TIMESTAMPTZ | | 撤销时间 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 创建时间 |

**索引：** UNIQUE(token), INDEX(user_id, revoked_at)

---

#### 1.2.4 login_attempts（登录尝试记录）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| email | VARCHAR(255) | NOT NULL | 尝试登录的邮箱 |
| ip_address | VARCHAR(45) | | 客户端 IP |
| success | BOOLEAN | NOT NULL | 是否成功 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 尝试时间 |

**索引：** INDEX(email, created_at)

---

#### 1.2.5 merchant_profiles（商家资料）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL, UNIQUE, FK → users.id | 关联用户 |
| merchant_name | VARCHAR(100) | NOT NULL | 商家名称 |
| merchant_nickname | VARCHAR(50) | UNIQUE | 商家昵称 |
| activity_domains | JSONB | DEFAULT '[]' | 活动领域 |
| license_image_url | VARCHAR(500) | | 营业执照图片 |
| audit_status | VARCHAR(20) | NOT NULL, DEFAULT 'pending', CHECK IN ('pending','approved','rejected') | 审核状态 |
| audit_reason | TEXT | | 审核原因 |
| audited_by | UUID | FK → users.id | 审核人 |
| audited_at | TIMESTAMPTZ | | 审核时间 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：** UNIQUE(merchant_nickname), INDEX(audit_status)

---

#### 1.2.6 activities（活动表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| creator_id | UUID | NOT NULL, FK → users.id | 发起人 |
| title | VARCHAR(100) | NOT NULL | 活动名称 |
| description | TEXT | NOT NULL | 活动简介 |
| tags | JSONB | DEFAULT '[]' | 标签数组 |
| activity_type | VARCHAR(50) | | 活动类型 |
| cover_image_url | VARCHAR(500) | | 封面图 |
| start_time | TIMESTAMPTZ | NOT NULL | 开始时间 |
| end_time | TIMESTAMPTZ | NOT NULL | 结束时间 |
| registration_deadline | TIMESTAMPTZ | NOT NULL | 报名截止时间 |
| max_participants | INT | NOT NULL, CHECK (max_participants > 0) | 人数上限 |
| current_participants | INT | NOT NULL, DEFAULT 0 | 当前已报人数 |
| min_credit_score | INT | DEFAULT 0 | 最低信誉要求 |
| min_age | INT | DEFAULT 0 | 最低年龄要求（0 表示不限） |
| fee_type | VARCHAR(10) | NOT NULL, DEFAULT 'free', CHECK IN ('free','paid') | 费用类型 |
| fee_amount | DECIMAL(10,2) | NOT NULL, DEFAULT 0 | 活动费用金额 |
| city | VARCHAR(50) | | 活动所在城市 |
| location_name | VARCHAR(200) | | 格式化地址 |
| location_lat | DECIMAL(10,7) | | 纬度 |
| location_lng | DECIMAL(10,7) | | 经度 |
| location_geom | GEOMETRY(Point, 4326) | | PostGIS 地理坐标 |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'draft', CHECK IN ('draft','pending_ai_review','pending_manual_review','published','rejected','taken_down','ended') | 状态 |
| ai_review_result | VARCHAR(50) | | AI 审核结果 |
| review_reason | TEXT | | 审核/驳回原因 |
| reviewed_by | UUID | FK → users.id | 审核人 |
| reviewed_at | TIMESTAMPTZ | | 审核时间 |
| is_team_activity | BOOLEAN | DEFAULT FALSE | 是否队内活动 |
| team_id | UUID | FK → teams.id | 所属小队 |
| cloned_from_id | UUID | FK → activities.id | 克隆来源 |
| check_in_qr_code | VARCHAR(500) | | 签到码 |
| check_in_enabled | BOOLEAN | DEFAULT FALSE | 签到是否开启 |
| check_in_location_required | BOOLEAN | DEFAULT FALSE | 签到是否需要位置校验 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：**
- INDEX(creator_id)
- INDEX(status)
- INDEX(activity_type)
- INDEX(city)
- INDEX(fee_type)
- INDEX(start_time)
- INDEX(created_at DESC) — 最新列表
- INDEX(location_geom) USING GIST — 附近搜索
- INDEX(team_id) WHERE is_team_activity = TRUE
- GIN trigram index on (title, description) + GIN(tags) — 中文关键词搜索

---

#### 1.2.7 activity_templates（活动模板）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| name | VARCHAR(100) | NOT NULL | 模板名称 |
| category | VARCHAR(50) | NOT NULL | 分类（运动健身/户外徒步/桌游聚会/学习交流/公益活动/城市探索） |
| description | TEXT | | 预设简介 |
| tags | JSONB | DEFAULT '[]' | 预设标签 |
| activity_type | VARCHAR(50) | | 活动类型 |
| preset_duration_minutes | INT | | 预设时长（分） |
| preset_max_participants | INT | | 预设人数上限 |
| is_system | BOOLEAN | DEFAULT TRUE | 是否系统模板 |
| creator_id | UUID | FK → users.id | 创建者（用户自定义模板） |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：** INDEX(category)

---

#### 1.2.8 registrations（报名表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| activity_id | UUID | NOT NULL, FK → activities.id | 活动 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'registered', CHECK IN ('registered','cancelled','checked_in') | 状态 |
| form_data | JSONB | | 报名表单数据 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 报名时间 |
| cancelled_at | TIMESTAMPTZ | | 取消时间 |
| checked_in_at | TIMESTAMPTZ | | 签到时间 |

**索引：**
- UNIQUE(activity_id, user_id)
- INDEX(activity_id, status)
- INDEX(user_id)

---

#### 1.2.9 waitlist（等待队列）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| activity_id | UUID | NOT NULL, FK → activities.id | 活动 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| position | INT | NOT NULL | 序号 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'waiting', CHECK IN ('waiting','notified','expired','confirmed') | 状态 |
| notified_at | TIMESTAMPTZ | | 递补通知时间 |
| expires_at | TIMESTAMPTZ | | 递补确认截止时间 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：**
- UNIQUE(activity_id, user_id)
- INDEX(activity_id, status, position)

---

#### 1.2.10 reviews（评价表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| activity_id | UUID | NOT NULL, FK → activities.id | 活动 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| content | TEXT | NOT NULL | 评价内容 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 评价时间 |

**索引：** UNIQUE(activity_id, user_id), INDEX(activity_id, created_at DESC)

---

#### 1.2.11 friendships（好友关系表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| friend_id | UUID | NOT NULL, FK → users.id | 好友 |
| status | VARCHAR(20) | NOT NULL, CHECK IN ('pending','accepted','blocked') | 状态 |
| action_user_id | UUID | NOT NULL, FK → users.id | 操作发起人 |
| remark_name | VARCHAR(30) | | user_id 给 friend_id 的备注 |
| group_tags | JSONB | DEFAULT '[]' | 分组标签（如 ["徒步搭子"]） |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：**
- UNIQUE(user_id, friend_id)
- INDEX(friend_id, status)
- INDEX(user_id, status)

---

#### 1.2.12 follows（关注表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| follower_id | UUID | NOT NULL, FK → users.id | 粉丝 |
| followed_id | UUID | NOT NULL, FK → users.id | 被关注者 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：**
- PRIMARY KEY(follower_id, followed_id)
- INDEX(followed_id)
- INDEX(follower_id)

---

#### 1.2.13 teams（小队表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| name | VARCHAR(100) | NOT NULL | 小队名称 |
| description | TEXT | | 小队简介 |
| interest_tags | JSONB | DEFAULT '[]' | 兴趣标签 |
| join_type | VARCHAR(10) | NOT NULL, DEFAULT 'public', CHECK IN ('public','review') | 加入方式 |
| max_members | INT | | 人数上限 |
| current_members | INT | NOT NULL, DEFAULT 1 | 当前人数 |
| leader_id | UUID | NOT NULL, FK → users.id | 队长 |
| avatar_url | VARCHAR(500) | | 小队头像 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'active', CHECK IN ('active','dissolved','disabled') | 状态 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：** INDEX(status), INDEX(name)

---

#### 1.2.14 team_members（小队成员表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| team_id | UUID | NOT NULL, FK → teams.id | 小队 |
| user_id | UUID | NOT NULL, FK → users.id | 成员 |
| role | VARCHAR(10) | NOT NULL, DEFAULT 'member', CHECK IN ('leader','admin','member') | 角色 |
| joined_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 加入时间 |

**索引：** UNIQUE(team_id, user_id), INDEX(user_id)

---

#### 1.2.15 team_join_requests（小队加入申请）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| team_id | UUID | NOT NULL, FK → teams.id | 小队 |
| user_id | UUID | NOT NULL, FK → users.id | 申请人 |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'pending', CHECK IN ('pending','approved','rejected') | 状态 |
| reviewed_by | UUID | FK → users.id | 审核人 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：** UNIQUE(team_id, user_id) WHERE status = 'pending'（部分唯一索引）

---

#### 1.2.16 team_blacklist（小队黑名单）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| team_id | UUID | NOT NULL, FK → teams.id | 小队 |
| user_id | UUID | NOT NULL, FK → users.id | 用户 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：** PRIMARY KEY(team_id, user_id)

---

#### 1.2.17 activity_summaries（活动图文总结）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| activity_id | UUID | NOT NULL, UNIQUE, FK → activities.id | 活动 |
| content | TEXT | | 文字总结 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

---

#### 1.2.18 summary_images（总结图片）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| summary_id | UUID | NOT NULL, FK → activity_summaries.id | 所属总结 |
| image_url | VARCHAR(500) | NOT NULL | 图片 URL |
| category | VARCHAR(20) | NOT NULL, CHECK IN ('group_photo','venue','process','supplies','result') | 分类 |
| sort_order | INT | DEFAULT 0 | 排序 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

**索引：** INDEX(summary_id, category)

---

#### 1.2.19 user_bans（用户封禁记录）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL, FK → users.id | 被封用户 |
| reason | TEXT | NOT NULL | 封禁原因 |
| banned_by | UUID | NOT NULL, FK → users.id | 操作管理员 |
| banned_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 封禁时间 |
| expires_at | TIMESTAMPTZ | | 过期时间（NULL=永久） |
| is_active | BOOLEAN | DEFAULT TRUE | 是否生效中 |
| revoked_by | UUID | FK → users.id | 解封操作人 |
| revoked_at | TIMESTAMPTZ | | 解封时间 |

**索引：** INDEX(user_id, is_active)

---

#### 1.2.20 notifications（通知表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL, FK → users.id | 接收用户 |
| type | VARCHAR(30) | NOT NULL | 通知类型 |
| title | VARCHAR(200) | NOT NULL | 标题 |
| content | TEXT | | 内容 |
| is_read | BOOLEAN | DEFAULT FALSE | 是否已读 |
| metadata | JSONB | | 关联数据（如 activity_id, user_id 等） |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

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

**索引：** INDEX(user_id, is_read, created_at DESC)

---

### 1.3 活动关键词搜索配置

迭代 1 优先保证中文关键词可用，采用标题/简介包含匹配 + 标签数组匹配：

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_activities_title_trgm
  ON activities USING GIN (title gin_trgm_ops);

CREATE INDEX idx_activities_description_trgm
  ON activities USING GIN (description gin_trgm_ops);

CREATE INDEX idx_activities_tags_gin
  ON activities USING GIN (tags);
```

> 说明：原 `to_tsvector('simple', ...)` 对中文短词检索不稳定，不适合作为本项目 MVP 的唯一搜索方案。后续如需更高相关度排序，可接入中文分词插件或独立搜索服务。

### 1.4 PostGIS 空间索引

```sql
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

### 2.1 通用规范

#### 基础 URL
- 开发环境：`http://localhost:3000/api`
- 生产环境：`https://api.quju.example.com/api`

#### 认证方式
- Header: `Authorization: Bearer <access_token>`
- access_token 有效期：2h
- refresh_token 有效期：7d

#### 统一响应格式

成功：
```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "pagination": { ... }   // 仅分页接口
}
```

失败：
```json
{
  "code": 40001,
  "message": "该邮箱已被注册",
  "data": null
}
```

#### 错误码规范

| 范围 | 含义 |
|------|------|
| 400xx | 参数校验错误 |
| 401xx | 认证错误 |
| 403xx | 权限错误 |
| 404xx | 资源不存在 |
| 409xx | 冲突（重复操作、并发冲突等） |
| 429xx | 频率限制 |
| 500xx | 服务端错误 |
| 502xx | 第三方服务错误（AI 超时等） |

#### 分页规范

游标分页：
```
GET /api/activities?cursor=2024-01-01T00:00:00Z&limit=20
```

响应：
```json
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

### 2.2 认证模块 (Auth)

#### POST `/api/auth/register/personal`

个人用户注册

**Request:**
```json
{
  "email": "user@example.com",
  "password": "Abc12345",
  "nickname": "张三"
}
```

**Response (200):**
```json
{
  "code": 0,
  "message": "注册成功，请前往邮箱激活账号",
  "data": { "email": "user@example.com" }
}
```

**校验规则：**
- email: 合法邮箱格式
- password: 至少 8 位，包含字母和数字
- nickname: 2-50 字

**错误码：** 40001=邮箱已注册, 40002=密码不合规, 40003=昵称已被占用

---

#### POST `/api/auth/register/merchant`

商家用户注册（multipart/form-data）

**Request:**
```
email: merchant@example.com
password: Abc12345
nickname: 某某商家
merchant_name: 某某餐饮管理有限公司
activity_domains: ["美食","聚会"]
license_image: <file>
```

**Response (200):**
```json
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
```json
{
  "email": "user@example.com",
  "password": "Abc12345"
}
```

**Response (200):**
```json
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

**Response (200):**
```json
{ "code": 0, "message": "已退出登录" }
```

（服务端标记 refresh_token 为 revoked）

---

#### POST `/api/auth/refresh`

刷新 Token

**Request:**
```json
{ "refresh_token": "dGhpcyBp..." }
```

**Response (200):**
```json
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

**Response (200):**
```json
{ "code": 0, "message": "账号激活成功，请登录" }
```

**错误码：** 40005=激活链接无效或已过期, 40006=账号已激活

---

#### POST `/api/auth/resend-activation`

重发激活邮件

**Request:**
```json
{ "email": "user@example.com" }
```

**Response (200):**
```json
{ "code": 0, "message": "激活邮件已重新发送" }
```

---

### 2.3 用户模块 (Users)

#### GET `/api/users/me`

获取当前用户信息

**Response (200):**
```json
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
```json
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

**Response (200):**
```json
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

**Response (200):**
```json
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

### 2.4 活动创建模块 (Activities)

#### POST `/api/activities`

创建活动

**Request:**
```json
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
- max_participants: > 0
- registration_deadline: > now()
- start_time < end_time
- min_age: >= 0，0 表示不限年龄
- fee_type: `free` 时 fee_amount 必须为 0；`paid` 时 fee_amount 必须 >= 0
- city: 高级筛选需要，建议由地图逆地理编码自动带出并允许用户确认

**status 可选值：**
- `draft` → 保存草稿
- 非 `draft` → 提交审核

**提交审核规则：**
- `max_participants > 50` → 直接进入 `pending_manual_review`
- 其他活动 → 进入 `pending_ai_review`

**Response (200):**
```json
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

**Response (200):**
```json
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

**registration_status 可选值：**
- `not_registered` — 未报名
- `registered` — 已报名
- `cancelled` — 已取消
- `in_waitlist` — 等待队列中

**display_status 可选值（由 status + 当前时间计算，不建议单独入库）：**
- `not_started` — 未开始
- `registering` — 报名中
- `registration_closed` — 报名已截止
- `in_progress` — 活动中
- `ended` — 已结束
- `taken_down` — 已下架

---

#### POST `/api/activities/:id/clone`

克隆活动

**Response (200):**
```json
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

**Response (200):**
```json
{ "code": 0, "message": "草稿已删除" }
```

---

#### GET `/api/activities/:id/participants`

获取活动报名用户列表（发起人可完整查看）

**Response (200):**
```json
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

### 2.5 活动发现模块 (Discover)

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

逻辑：对 `title`、`description` 做包含匹配，并对 `tags` 做数组包含/相等匹配；排序优先级建议为：标题命中 > 标签命中 > 简介命中 > 发布时间倒序。

**Response 示例：**
```json
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
```
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

**Response (200):**
```json
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

### 2.6 报名模块 (Registrations)

#### POST `/api/activities/:id/register`

报名活动

**流程：**
1. 校验活动状态 = published
2. 校验报名截止时间未过
3. 校验信誉分 ≥ 活动最低要求
4. 若活动设置最低年龄，则根据用户生日校验年龄 ≥ `min_age`
5. 校验名额未满
6. `SELECT * FROM activities WHERE id = :id FOR UPDATE`（行锁）
7. 插入报名记录 + `current_participants + 1`
8. 提交事务

**Request:**
```json
{
  "form_data": { "phone": "13800138000", "remark": "新人第一次参加" }
}
```

**Response (200):**
```json
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
1. 校验当前时间 < `registration_deadline`
2. 在同一事务中：更新报名状态为 cancelled + `current_participants - 1`
3. 若有等待队列，触发递补逻辑

**Response (200):**
```json
{ "code": 0, "message": "已取消报名" }
```

---

#### POST `/api/activities/:id/join-waitlist`

加入等待队列

**约束：** 活动已满员，且用户未在队列中

**Response (200):**
```json
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

### 2.7 签到模块 (Check-in)

#### POST `/api/activities/:id/check-in`

扫码签到

**Request:**
```json
{
  "qr_data": "activity:uuid:signature",
  "lat": 40.0179,
  "lng": 116.3913
}
```

**校验：**
- 用户已报名且未签到
- 若活动开启位置校验：计算距离 ≤ 500m
- qr_data 签名验证

**Response (200):**
```json
{ "code": 0, "message": "签到成功" }
```

**错误码：**
- 40904=您已签到
- 40302=未报名该活动
- 40303=不在活动地点附近

---

#### GET `/api/activities/:id/check-in/list`

签到名单（发起人/管理员可见）

**Response (200):**
```json
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

**Response (200):**
```json
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

### 2.8 评价模块 (Reviews)

#### POST `/api/activities/:id/reviews`

提交评价

**约束：**
- 活动已结束（end_time < now）
- 结束不超过 7 天
- 用户已签到

**Request:**
```json
{ "content": "组织得很好，风景优美！" }
```

**Response (200):**
```json
{ "code": 0, "data": { "id": "uuid", "created_at": "..." } }
```

---

#### GET `/api/activities/:id/reviews`

获取活动评价列表

**Query:** `?cursor=&limit=20`

**Response (200):**
```json
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

### 2.9 模板模块 (Templates)

#### GET `/api/templates`

获取模板列表

**Query:** `?category=户外徒步`

**Response (200):**
```json
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

**Response (200):**
```json
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

### 2.10 AI 模块 (AI)

#### POST `/api/ai/generate-activity`

AI 活动策划

**Request:**
```json
{ "topic": "周末奥森徒步" }
```

**Response (200):**
```json
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

**Request (multipart/form-data):**
```
images: [file1, file2, file3, ...]
```

**Response (200):**
```json
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

### 2.11 活动总结模块 (Summaries)

#### POST `/api/activities/:id/summary`

发布活动图文总结

**Request:**
```json
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

### 2.12 好友模块 (Friends)

#### POST `/api/friends/requests`

发送好友申请

**Request:**
```json
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

**Response (200):**
```json
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
```json
{
  "remark_name": "小王（徒步）",
  "group_tags": ["徒步搭子","周末伙伴"]
}
```

---

### 2.13 关注模块 (Follows)

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

### 2.14 小队模块 (Teams)

#### POST `/api/teams`

创建小队

**Request:**
```json
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

小队详情

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
```json
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

### 2.15 通知模块 (Notifications)

#### GET `/api/notifications`

通知列表

**Query:** `?type=friend_request&is_read=false&cursor=&limit=20`

**Response (200):**
```json
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

### 2.16 管理后台模块 (Admin)

> 所有后台接口需要 `role = 'admin'` 权限

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
```json
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
```json
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
```json
{
  "action": "approve",
  "reason": ""
}
```

`action` 可选值：`approve` | `reject` | `request_changes`

---

#### POST `/api/admin/activities/:id/take-down`

下架活动

**Request:**
```json
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

#### POST `/api/admin/teams/:id/disable`

停用小队

**Request:**
```json
{ "reason": "违规运营" }
```

---

#### POST `/api/admin/teams/:id/restore`

恢复小队

---

### 2.17 文件上传模块 (Files)

#### POST `/api/files/upload`

文件上传

**Request:** multipart/form-data

```
file: <file>
type: avatar | activity_cover | activity_image | license | team_avatar | team_album
```

**Response (200):**
```json
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
- activity_image: 10MB, jpg/png

---

### 2.18 API 汇总表

| 模块 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|:---:|------|
| Auth | POST | `/api/auth/register/personal` | ✗ | 个人注册 |
| Auth | POST | `/api/auth/register/merchant` | ✗ | 商家注册 |
| Auth | POST | `/api/auth/login` | ✗ | 登录 |
| Auth | POST | `/api/auth/logout` | ✓ | 退出 |
| Auth | POST | `/api/auth/refresh` | ✗ | 刷新Token |
| Auth | GET | `/api/auth/activate/:token` | ✗ | 激活邮箱 |
| Auth | POST | `/api/auth/resend-activation` | ✗ | 重发激活邮件 |
| Users | GET | `/api/users/me` | ✓ | 当前用户信息 |
| Users | PATCH | `/api/users/me` | ✓ | 更新资料 |
| Users | GET | `/api/users/:id` | ✓ | 用户公开信息 |
| Users | GET | `/api/users/check-nickname` | ✗ | 昵称检查 |
| Users | GET | `/api/users/me/created-activities` | ✓ | 我创建的 |
| Users | GET | `/api/users/me/joined-activities` | ✓ | 我报名的 |
| Activities | POST | `/api/activities` | ✓ | 创建活动 |
| Activities | PUT | `/api/activities/:id` | ✓ | 更新活动 |
| Activities | GET | `/api/activities/:id` | ✓ | 活动详情 |
| Activities | POST | `/api/activities/:id/clone` | ✓ | 克隆活动 |
| Activities | DELETE | `/api/activities/:id` | ✓ | 删除草稿 |
| Activities | GET | `/api/activities/:id/participants` | ✓ | 报名列表 |
| Discover | GET | `/api/discover/recommended` | ✓ | 推荐 |
| Discover | GET | `/api/discover/latest` | ✓ | 最新 |
| Discover | GET | `/api/discover/nearby` | ✓ | 附近 |
| Discover | GET | `/api/discover/search` | ✓ | 搜索 |
| Discover | GET | `/api/discover/filter` | ✓ | 高级筛选 |
| Discover | GET | `/api/discover/map` | ✓ | 地图点位 |
| Registration | POST | `/api/activities/:id/register` | ✓ | 报名 |
| Registration | POST | `/api/activities/:id/cancel-registration` | ✓ | 取消报名 |
| Registration | POST | `/api/activities/:id/join-waitlist` | ✓ | 加入等待 |
| Registration | DELETE | `/api/activities/:id/leave-waitlist` | ✓ | 退出等待 |
| Check-in | POST | `/api/activities/:id/check-in` | ✓ | 签到 |
| Check-in | GET | `/api/activities/:id/check-in/list` | ✓ | 签到名单 |
| Check-in | POST | `/api/activities/:id/check-in/qrcode` | ✓ | 生成签到码 |
| Reviews | POST | `/api/activities/:id/reviews` | ✓ | 提交评价 |
| Reviews | GET | `/api/activities/:id/reviews` | ✓ | 评价列表 |
| Templates | GET | `/api/templates` | ✓ | 模板列表 |
| Templates | POST | `/api/templates/:id/use` | ✓ | 使用模板 |
| AI | POST | `/api/ai/generate-activity` | ✓ | AI策划 |
| AI | POST | `/api/ai/classify-images` | ✓ | AI图片分类 |
| Summary | POST | `/api/activities/:id/summary` | ✓ | 发布总结 |
| Summary | GET | `/api/activities/:id/summary` | ✓ | 查看总结 |
| Friends | POST | `/api/friends/requests` | ✓ | 发送申请 |
| Friends | GET | `/api/friends/requests/received` | ✓ | 收到的申请 |
| Friends | GET | `/api/friends/requests/sent` | ✓ | 发出的申请 |
| Friends | POST | `/api/friends/requests/:id/accept` | ✓ | 接受申请 |
| Friends | POST | `/api/friends/requests/:id/reject` | ✓ | 拒绝申请 |
| Friends | GET | `/api/friends` | ✓ | 好友列表 |
| Friends | DELETE | `/api/friends/:userId` | ✓ | 删除好友 |
| Friends | PATCH | `/api/friends/:userId` | ✓ | 修改备注/分组 |
| Follows | POST | `/api/follows/:userId` | ✓ | 关注 |
| Follows | DELETE | `/api/follows/:userId` | ✓ | 取消关注 |
| Follows | GET | `/api/users/:id/followers` | ✓ | 粉丝列表 |
| Follows | GET | `/api/users/:id/following` | ✓ | 关注列表 |
| Teams | POST | `/api/teams` | ✓ | 创建小队 |
| Teams | GET | `/api/teams` | ✓ | 小队发现 |
| Teams | GET | `/api/teams/:id` | ✓ | 小队详情 |
| Teams | PATCH | `/api/teams/:id` | ✓ | 修改信息 |
| Teams | POST | `/api/teams/:id/join` | ✓ | 加入小队 |
| Teams | POST | `/api/teams/:id/leave` | ✓ | 退出小队 |
| Teams | PATCH | `/api/teams/:id/members/:userId/role` | ✓ | 修改角色 |
| Teams | DELETE | `/api/teams/:id/members/:userId` | ✓ | 移出成员 |
| Teams | POST | `/api/teams/:id/dissolve` | ✓ | 解散小队 |
| Teams | GET | `/api/teams/:id/join-requests` | ✓ | 申请列表 |
| Teams | POST | `/api/teams/:id/join-requests/:id/approve` | ✓ | 通过申请 |
| Teams | POST | `/api/teams/:id/join-requests/:id/reject` | ✓ | 拒绝申请 |
| Notifications | GET | `/api/notifications` | ✓ | 通知列表 |
| Notifications | PATCH | `/api/notifications/:id/read` | ✓ | 标记已读 |
| Notifications | POST | `/api/notifications/read-all` | ✓ | 全部已读 |
| Files | POST | `/api/files/upload` | ✓ | 文件上传 |
| Admin | GET | `/api/admin/users` | ✓† | 用户查询 |
| Admin | GET | `/api/admin/users/:id` | ✓† | 用户详情 |
| Admin | POST | `/api/admin/users/:id/ban` | ✓† | 封禁用户 |
| Admin | POST | `/api/admin/users/:id/unban` | ✓† | 解封用户 |
| Admin | GET | `/api/admin/merchants/pending` | ✓† | 待审商家 |
| Admin | POST | `/api/admin/merchants/:id/approve` | ✓† | 通过商家 |
| Admin | POST | `/api/admin/merchants/:id/reject` | ✓† | 驳回商家 |
| Admin | GET | `/api/admin/activities` | ✓† | 活动管理 |
| Admin | POST | `/api/admin/activities/:id/review` | ✓† | 审核活动 |
| Admin | POST | `/api/admin/activities/:id/take-down` | ✓† | 下架活动 |
| Admin | POST | `/api/admin/activities/:id/restore` | ✓† | 恢复活动 |
| Admin | GET | `/api/admin/teams` | ✓† | 小队管理 |
| Admin | GET | `/api/admin/teams/:id` | ✓† | 小队详情 |
| Admin | POST | `/api/admin/teams/:id/disable` | ✓† | 停用小队 |
| Admin | POST | `/api/admin/teams/:id/restore` | ✓† | 恢复小队 |

> ✓ = 需 JWT, † = 需 admin 角色

---

## 第三部分：Nest.js 模块拆分建议

```
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

| 表 | 说明 |
|----|------|
| users | 用户 + 管理员 |
| activation_tokens | 激活 |
| refresh_tokens | 会话 |
| login_attempts | 登录保护 |
| activities | 活动 |
| registrations | 报名 |
| notifications | 通知 |
| user_bans | 封禁记录 |

> 迭代 1 暂不建：merchant_profiles、friendships、follows、teams 系列、reviews、waitlist、summaries、templates。
