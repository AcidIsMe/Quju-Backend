# P0 端到端测试套件

> 测试范围：迭代 1 P0 用户故事（Must）  
> 测试框架：Python 3 + requests  
> 后端分支：`quju-zfz-YY`

---

## 一、环境准备

### 1.1 依赖安装

```bash
pip install requests
```

### 1.2 关键配置

| 配置项 | 值 |
|---|---|
| 后端地址 | `http://localhost:3002/api` |
| 数据库 | MySQL `quju_platform` (localhost:3306) |
| 管理员账号 | `admin@quju.com` / `Admin12345` |
| 测试用户 | `test@quju.com` / `Test12345` |
| Mobile AppID | `wx5796d1140304e815` |
| Web 端口 | `http://localhost:5173` |

### 1.3 启动后端

```bash
cd Quju-Backend
mvnw spring-boot:run
# 启动后自动创建 admin@quju.com 和 test@quju.com
```

### 1.4 启动 Web 管理后台（可选，用于手工验证）

```bash
cd Quju-Web-main
set VITE_API_TARGET=http://localhost:3002
npx vite --port 5173
```

### 1.5 启动 Mobile 小程序（可选，用于手工验证）

```bash
cd Quju-Mobile-main
npm run dev:mp-weixin
# 微信开发者工具打开 dist/dev/mp-weixin
# 勾选: 不校验合法域名
```

---

## 二、运行测试

### 2.1 运行全部测试

```bash
python test_p0_all.py
```

### 2.2 运行指定故事

```bash
python test_p0_all.py --story US01,US02
```

### 2.3 指定后端地址

```bash
python test_p0_all.py --base-url http://192.168.1.100:3002/api
```

---

## 三、测试覆盖矩阵

| 编号 | 用户故事 | 测试数 | 涉及端点 |
|---|------|:---:|------|
| US01 | 个人用户注册 | 4 | POST /auth/register/personal |
| US02 | 个人用户登录 | 6 | POST /auth/login, /auth/refresh, /auth/logout |
| US07 | 管理员登录 | 2 | POST /auth/login (admin) |
| US08 | 创建活动 | 3 | POST /activities |
| US13 | 人工审核 | 3 | POST /admin/activities/{id}/review |
| US14 | 信息流 | 2 | GET /discover/recommended, /latest |
| US15 | 搜索 | 1 | GET /discover/search |
| US17 | 地图 | 2 | GET /discover/nearby, /map |
| US18 | 详情页 | 2 | GET /activities/{id} |
| US19 | 报名 | 3 | POST /activities/{id}/register |
| US20 | 取消报名 | 1 | POST /activities/{id}/cancel-registration |
| US40 | 活动管理 | 3 | POST /admin/activities/{id}/take-down, restore |

---

## 四、测试流程说明

```
US01 注册 → US02 登录 → US08 创建活动 → US12 自动AI审核
  → US13 人工审核 → US14/US15 发现 → US18 详情 → US19 报名
  → US20 取消报名 → US40 下架/恢复
```

---

## 五、第三方组件说明

### 地图组件
- **Mobile**: 使用微信原生 `<map>` 组件，GCJ-02 坐标系
- **后端**: `/discover/map` 使用 MySQL 经纬度 + Haversine 公式计算距离
- **逆地理编码**: Mobile `location-picker` 页面暂仅保存坐标，未逆解析地址

### 腾讯地图 SDK
- 当前未配置。如需逆地理编码，需在 `manifest.json` 中配置腾讯地图 key

### WebSocket IM
- 后端已实现 `ImWebSocketServer`，Mobile 前端未对接

---

## 附录：关键配置文件位置

### 后端 (`Quju-Backend/`)

| 文件 | 说明 |
|---|---|
| `src/main/resources/application.yml` | 端口(3002)、数据库连接、JWT密钥、SNAKE_CASE |
| `src/main/resources/db/schema.sql` | 数据库建表 DDL |

### Web 管理后台 (`Quju-Web-main/`)

| 文件 | 说明 |
|---|---|
| `vite.config.ts` | 前端端口(5173)、`/api` 代理目标 |
| `.env.development` | `VITE_API_TARGET=http://localhost:3002` |
| `src/utils/request.ts` | Axios 实例、Token 注入、401 拦截 |

### Mobile 小程序 (`Quju-Mobile-main/`)

| 文件 | 说明 |
|---|---|
| `src/manifest.json` | 小程序 AppID(`wx5796d1140304e815`)、位置权限 |
| `project.config.json` | 微信开发者工具项目配置 |
| `src/services/http.ts` | BASE_URL(`http://localhost:3002/api`)、Token 管理 |

### 数据库

| 配置项 | 值 |
|---|---|
| 地址 | `localhost:3306` |
| 数据库名 | `quju_platform`（自动创建） |
| 用户名/密码 | `root` / `Caesium137!`（见 `application.yml`） |

### 代理排除

如系统有 HTTP 代理（Clash/VPN），运行测试前：

```bash
set NO_PROXY=localhost,127.0.0.1
```
