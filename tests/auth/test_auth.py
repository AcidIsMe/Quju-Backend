"""
趣聚 API 测试 — 认证模块 (Auth)
覆盖 US01 个人注册 / US02 登录 / US07 管理员登录
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time

_ts = str(int(time.time() * 1000))[-8:]


def test_register_validation():
    """US01: 注册字段校验"""
    e1 = f"auth01_{_ts}@example.com"
    n1 = f"AuthUser_{_ts}"

    # 正常注册
    resp = post("/api/auth/register/personal", {"email": e1, "password": "Test1234", "nickname": n1})
    body = assert_pass("US01-1 正常注册", resp, 0, data_keys=["email", "activation_token"])

    # 重复邮箱
    resp = post("/api/auth/register/personal", {"email": e1, "password": "Test1234", "nickname": "OtherNick"})
    assert_pass("US01-2 重复邮箱拒绝", resp, 40001)

    # 重复昵称
    resp = post("/api/auth/register/personal", {"email": f"auth02_{_ts}@example.com", "password": "Test1234", "nickname": n1})
    assert_pass("US01-3 重复昵称拒绝", resp, 40003)

    # 弱密码
    resp = post("/api/auth/register/personal", {"email": "bad@x.com", "password": "short", "nickname": "BadPw"})
    assert_pass("US01-4 弱密码拒绝", resp, 40000)

    return body


def test_activate_and_login():
    """US02: 激活 + 登录"""
    import time as _time
    ts2 = str(int(_time.time() * 1000))[-8:]
    resp = post("/api/auth/register/personal", {
        "email": f"login_{ts2}@example.com", "password": "Test1234", "nickname": f"LoginUser_{ts2}"
    })
    body = resp.json()
    token = body["data"]["activation_token"]

    # 激活前登录 → 应拒绝
    resp = post("/api/auth/login", {"email": f"login_{ts2}@example.com", "password": "Test1234"})
    assert_pass("US02-1 未激活不可登录", resp, 40102)

    # 激活
    resp = get(f"/api/auth/activate/{token}")
    assert_pass("US02-2 激活成功", resp, 0)

    # 重复激活
    resp = get(f"/api/auth/activate/{token}")
    assert_pass("US02-3 重复激活拒绝", resp, 40006)

    # 激活后登录 → 成功
    resp = post("/api/auth/login", {"email": f"login_{ts2}@example.com", "password": "Test1234"})
    body = assert_pass("US02-4 激活后登录成功", resp, 0, data_keys=["access_token", "refresh_token", "user"])

    # 错误密码
    resp = post("/api/auth/login", {"email": f"login_{ts2}@example.com", "password": "WrongPW1"})
    assert_pass("US02-5 错误密码拒绝", resp, 40101)

    # 不存在用户
    resp = post("/api/auth/login", {"email": "noexist@x.com", "password": "Test1234"})
    assert_pass("US02-6 不存在邮箱拒绝", resp, 40101)

    return body


def test_token_refresh():
    """Token 刷新"""
    ts3 = str(int(time.time() * 1000))[-8:]
    resp = post("/api/auth/register/personal", {
        "email": f"refresh_{ts3}@example.com", "password": "Test1234", "nickname": f"Refresh_{ts3}"
    })
    token = resp.json()["data"]["activation_token"]
    get(f"/api/auth/activate/{token}")
    resp = post("/api/auth/login", {"email": f"refresh_{ts3}@example.com", "password": "Test1234"})
    body = resp.json()
    refresh_token = body["data"]["refresh_token"]

    # 正常刷新
    resp = post("/api/auth/refresh", {"refresh_token": refresh_token})
    body = assert_pass("US02-7 刷新Token成功", resp, 0, data_keys=["access_token", "refresh_token"])

    # 用旧token再次刷新 → 应失败（已撤销）
    resp = post("/api/auth/refresh", {"refresh_token": refresh_token})
    assert_pass("US02-8 旧Token已撤销", resp, 40101)


def test_admin_login():
    """US07: 管理员登录"""
    resp = post("/api/auth/login", {"email": "admin@quju.com", "password": "Admin12345"})
    body = assert_pass("US07-1 管理员登录成功", resp, 0, data_keys=["access_token", "user"])
    if body:
        user = body["data"]["user"]
        assert user["role"] == "admin", f"Expected role=admin, got {user['role']}"
        print(f"  >> admin role confirmed: {user['role']}")


def test_logout():
    """退出登录"""
    ts = str(int(time.time() * 1000))[-8:]
    resp = post("/api/auth/register/personal", {
        "email": f"logout_{ts}@example.com", "password": "Test1234", "nickname": f"Logout_{ts}"
    })
    token = resp.json()["data"]["activation_token"]
    get(f"/api/auth/activate/{token}")
    resp = post("/api/auth/login", {"email": f"logout_{ts}@example.com", "password": "Test1234"})
    body = resp.json()
    refresh_token = body["data"]["refresh_token"]

    resp = post("/api/auth/logout", {"refresh_token": refresh_token})
    assert_pass("US02-9 退出登录", resp, 0)

    # 退出后 refresh 应失效
    resp = post("/api/auth/refresh", {"refresh_token": refresh_token})
    assert_pass("US02-10 退出后refresh失效", resp, 40101)


def test_invalid_activation():
    """无效激活令牌"""
    resp = get("/api/auth/activate/fake-token-12345")
    assert_pass("激活-无效令牌", resp, 40005)

    resp = post("/api/auth/resend-activation", {"email": "noexist@x.com"})
    assert_pass("激活-重发不存在邮箱", resp, 40401)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Auth Module (US01/US02/US07)")
    print("=" * 60)
    test_register_validation()
    test_activate_and_login()
    test_token_refresh()
    test_logout()
    test_admin_login()
    test_invalid_activation()
    tu.print_summary()
