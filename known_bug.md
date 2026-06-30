# Known Bugs

## BUG-001: JWT Refresh Token 同秒重复生成

**严重程度**: 中等  
**发现时间**: 2026-06-29  
**影响接口**: `POST /auth/refresh`

### 现象

刷新 Token 时偶发 HTTP 50000 错误：

```
SQLIntegrityConstraintViolationException: Duplicate entry 'eyJhbG...' for key 'refresh_tokens.token'
```

### 复现步骤

1. 注册用户 → 登录获取 access_token + refresh_token
2. 在同一秒内两次调用 `POST /auth/refresh`（同一用户）
3. 第一次可能成功，第二次报 500

### 根因分析

`JwtTokenUtil.createRefreshToken()` 生成的 JWT 仅包含 `sub`(userId) 和 `type`("refresh") 两个 Claim，不含时间戳或随机数。当同一用户在同一秒内刷新时，JWT 的 payload 完全相同，签名也相同，导致生成的 refresh_token 字符串相同。

虽然 `AuthServiceImpl.refresh()` 会先撤销旧 token 再插入新 token，但撤销操作是 `UPDATE` 行而非 `DELETE`（is_revoked 字段），`token` 列仍保留原值。当新生成的 token 与旧 token 相同时，就会触发唯一键冲突。

**相关文件**:
- `src/main/java/com/quju/platform/util/JwtTokenUtil.java:34-36`
- `src/main/java/com/quju/platform/service/impl/AuthServiceImpl.java:108-127`

### 修复建议

在 JWT Claims 中添加随机 `jti`（JWT ID）或时间戳：

```java
public String createRefreshToken(String userId) {
    return createToken(userId, Map.of(
        "type", "refresh",
        "jti", UUID.randomUUID().toString(),  // 添加此行
        "iat", Instant.now().getEpochSecond()
    ), refreshTokenTtlSeconds);
}
```

---

## BUG-002: 昵称冲突返回 50000 而非业务错误码

**严重程度**: 低  
**发现时间**: 2026-06-29  
**影响接口**: `PATCH /users/me`

### 现象

更新用户昵称为已存在的昵称时，返回 HTTP 50000（服务器内部错误）而非 40003（昵称已被占用）：

```
SQLIntegrityConstraintViolationException: Duplicate entry 'Nick_xxx' for key 'users.nickname'
```

### 根因分析

`UserController.updateMe()` 没有在更新前检查昵称唯一性，而是直接调用 `userMapper.updateById()`。数据库层 `nickname` 字段有 UNIQUE 约束，冲突时抛出 `SQLIntegrityConstraintViolationException`，被 `GlobalExceptionHandler.handleUnknown()` 捕获，统一返回 50000。

**相关文件**:
- `src/main/java/com/quju/platform/controller/user/UserController.java:26-40`
- `db/schema.sql:7` — `nickname VARCHAR(50) NOT NULL UNIQUE`

### 修复建议

在 `UserController.updateMe()` 中，更新昵称前先查询是否已被其他用户占用：

```java
if (body.containsKey("nickname")) {
    String newNickname = String.valueOf(body.get("nickname"));
    long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
        .eq(UserEntity::getNickname, newNickname)
        .ne(UserEntity::getId, user.getId()));
    if (count > 0) {
        throw new BusinessException(40003, "昵称已被占用");
    }
    user.setNickname(newNickname);
}
```

---

## BUG-003: 注册接口不返回激活令牌

**严重程度**: 低（功能限制）  
**发现时间**: 2026-06-29  
**影响接口**: `POST /auth/register/personal`

### 现象

`AuthServiceImpl.registerPersonal()` 生成了 `ActivationToken` 并存入数据库，但返回的 Map 只包含 `{"email": "..."}`，不包含 `activation_token`。测试和开发环境中无法直接获取激活链接。

### 根因分析

产品设计上激活令牌应该通过邮件发送，不通过 API 响应返回。但在开发和测试环境没有邮件服务，导致无法完成"注册→激活→登录"的完整流程（虽然当前登录不检查 `pending_activation` 状态，绕过了这一问题）。

### 修复建议

开发环境可在响应的 `data` 中附加 `activation_token` 字段，或提供一个开发专用的查询接口。

---

> 注：以上 bug 均为 quju-zjq 分支中的已知问题，不影响迭代1核心闭环流程（因登录不检查激活状态，且业务异常不会触发昵称冲突的 50000 路径）。
