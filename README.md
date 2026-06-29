# 趣聚 (QuJu) — 活动社交平台后端

基于 Spring Boot 3.2 + MySQL 构建的活动社交平台后端，支持用户注册登录、活动发布发现、报名签到、好友关注、小队管理等功能。

> 当前实现迭代 1（P0 闭环）：认证、用户、活动、报名、发现、管理后台。

---

## 项目结构

```
趣聚/
├── API与数据库设计.md          # 完整 API 文档与数据库设计
├── README.md
├── quju-server/                # Spring Boot 后端
│   ├── pom.xml                 # Maven 依赖
│   └── src/main/java/com/quju/
│       ├── QujuApplication.java          # 启动类
│       ├── common/         # 统一响应、错误码、认证工具、异常处理
│       ├── security/       # JWT 双Token 签发验证、认证过滤器
│       ├── entity/         # 8 个 JPA 实体
│       ├── repository/     # 8 个 Spring Data 仓库
│       ├── auth/           # 认证模块 (6 端点)
│       ├── user/           # 用户模块 (6 端点)
│       ├── activity/       # 活动模块 (6 端点)
│       ├── registration/   # 报名模块 (4 端点)
│       ├── discover/       # 发现模块 (6 端点)
│       └── admin/          # 管理模块 (4 端点)
│
└── tests/                     # Python 自动化测试（按模块分目录）
    ├── test_utils.py          # 公共工具（请求封装、登录辅助）
    ├── run_all.py             # 一键运行所有测试
    ├── auth/                  # Auth 模块测试
    ├── users/                 # Users 模块测试
    ├── activities/            # Activities 模块测试
    ├── registrations/         # Registration 模块测试
    ├── discover/              # Discover 模块测试
    └── admin/                 # Admin 模块测试
```

---

## 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 17 或 21 |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Python | 3.8+ (仅运行测试需要) |

---

## 快速启动

### 1. 准备数据库

确保 MySQL 8.0 正在运行。应用启动时会自动创建 `quju` 数据库和所有表。

### 2. 修改配置

编辑 `quju-server/src/main/resources/application.yml`，修改 MySQL 连接信息：

```yaml
spring:
  datasource:
    url: "jdbc:mysql://localhost:3306/quju?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&createDatabaseIfNotExist=true"
    username: root          # 改为你的 MySQL 用户名
    password: your_password # 改为你的 MySQL 密码
```

> 如果不想用 MySQL 快速跑起来，可以切换到 H2 内存数据库：注释掉 MySQL 段，取消 H2 段的注释即可，无需任何外部数据库。

### 3. 启动后端

```bash
cd quju-server
mvn spring-boot:run
```

看到 `Started QujuApplication in X seconds` 即启动成功，默认监听 `http://localhost:8080`。

### 4. 运行测试

在另一个终端：

```bash
cd tests
pip install requests
python run_all.py
```

预期输出所有模块 24 项测试全部通过。

---

## API 概览

### 认证 (Auth) — `/api/auth`

| 方法 | 路径 | 认证 | 说明 |
|------|------|:---:|------|
| POST | `/register/personal` | - | 个人用户注册 |
| POST | `/login` | - | 登录 |
| POST | `/logout` | - | 退出登录 |
| POST | `/refresh` | - | 刷新 Token |
| GET | `/activate/{token}` | - | 激活邮箱 |
| POST | `/resend-activation` | - | 重发激活邮件 |

### 用户 (Users) — `/api/users`

| 方法 | 路径 | 认证 | 说明 |
|------|------|:---:|------|
| GET | `/me` | ✓ | 当前用户信息 |
| PATCH | `/me` | ✓ | 更新资料 |
| GET | `/{id}` | - | 用户公开信息 |
| GET | `/check-nickname` | - | 昵称检查 |
| GET | `/me/created-activities` | ✓ | 我创建的活动 |
| GET | `/me/joined-activities` | ✓ | 我报名的活动 |

### 活动 (Activities) — `/api/activities`

| 方法 | 路径 | 认证 | 说明 |
|------|------|:---:|------|
| POST | `/` | ✓ | 创建活动 |
| PUT | `/{id}` | ✓ | 更新活动 |
| GET | `/{id}` | - | 活动详情 |
| POST | `/{id}/clone` | ✓ | 克隆活动 |
| DELETE | `/{id}` | ✓ | 删除草稿 |
| GET | `/{id}/participants` | ✓ | 报名列表 |

### 报名 (Registration)

| 方法 | 路径 | 认证 | 说明 |
|------|------|:---:|------|
| POST | `/api/activities/{id}/register` | ✓ | 报名 |
| POST | `/api/activities/{id}/cancel-registration` | ✓ | 取消报名 |
| POST | `/api/activities/{id}/join-waitlist` | ✓ | 加入等待 |
| DELETE | `/api/activities/{id}/leave-waitlist` | ✓ | 退出等待 |

### 发现 (Discover) — `/api/discover`

| 方法 | 路径 | 认证 | 说明 |
|------|------|:---:|------|
| GET | `/recommended` | - | 推荐活动 |
| GET | `/latest` | - | 最新活动 |
| GET | `/nearby` | - | 附近活动 |
| GET | `/search` | - | 关键词搜索 |
| GET | `/filter` | - | 高级筛选 |
| GET | `/map` | - | 地图点位 |

### 管理 (Admin) — `/api/admin`

| 方法 | 路径 | 认证 | 说明 |
|------|------|:---:|------|
| GET | `/users` | ✓† | 用户查询 |
| GET | `/users/{id}` | ✓† | 用户详情 |
| POST | `/users/{id}/ban` | ✓† | 封禁用户 |
| POST | `/users/{id}/unban` | ✓† | 解封用户 |

> ✓ = 需 JWT Token，† = 需 admin 角色

---

## 如何测试需要认证的接口

程序不发送真实邮件，注册后激活令牌直接在响应中返回：

```bash
# 1. 注册
curl -X POST http://localhost:8080/api/auth/register/personal \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234","nickname":"测试用户"}'

# 响应包含 activation_token
# → {"code":0, "data":{"email":"test@example.com", "activation_token":"abc123..."}}

# 2. 激活
curl http://localhost:8080/api/auth/activate/<activation_token>

# 3. 登录，获取 access_token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234"}'

# 4. 携带 Token 调用认证接口
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <access_token>"
```

Python 测试中可直接用封装好的方法：

```python
import test_utils as tu

# 一行完成 注册 + 激活 + 登录
token, user = tu.login_as_new_user("mytest")

# 之后所有请求自动带 Token
resp = tu.get("/api/users/me", headers=tu.auth_header())
```

---

## 统一响应格式

```json
// 成功
{ "code": 0, "message": "success", "data": { ... } }

// 失败
{ "code": 40001, "message": "该邮箱已被注册", "data": null }
```

错误码范围：`400xx` 参数校验、`401xx` 认证、`403xx` 权限、`404xx` 不存在、`409xx` 冲突、`429xx` 频率限制。

---

## 技术要点

- **JWT 双Token**: access_token 2h，refresh_token 7d，支持刷新和撤销
- **登录锁**: 同邮箱 5 分钟内连续 5 次失败 → 锁定 15 分钟
- **密码**: SHA-256 + 16 字节随机盐，不可逆存储
- **报名事务**: `@Transactional` + 行级锁保证名额一致性
- **活动审核**: 人数 >50 → 人工审核，否则 → AI 审核（预留）
- **H2 / MySQL 双配置**: 开发时用 H2 无需安装数据库，生产切换 MySQL
