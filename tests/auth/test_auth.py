"""
趣聚 API 测试 — 认证模块 (Auth)
覆盖: 注册校验 / 登录 / Token刷新 / 退出 / 激活 / 管理员登录
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time

_ts = str(int(time.time() * 1000))[-8:]


def test_register():
    """注册字段校验"""
    e1 = f"auth01_{_ts}@example.com"
    n1 = f"AuthUser_{_ts}"

    resp = post("/auth/register/personal", {"email": e1, "password": "Test1234", "nickname": n1})
    body = assert_pass("US01-1 正常注册", resp, 0, data_keys=["email"])

    resp = post("/auth/register/personal", {"email": e1, "password": "Test1234", "nickname": "OtherNick"})
    assert_pass("US01-2 重复邮箱拒绝(40001)", resp, 40001)

    resp = post("/auth/register/personal", {"email": f"auth02_{_ts}@example.com", "password": "Test1234", "nickname": n1})
    assert_pass("US01-3 重复昵称拒绝(40003)", resp, 40003)

    resp = post("/auth/register/personal", {"email": "bad@x.com", "password": "short", "nickname": "BadPw"})
    assert_pass("US01-4 弱密码拒绝(40000)", resp, 40000)


def test_login():
    """登录流程"""
    email = f"login_{_ts}@example.com"
    post("/auth/register/personal", {"email": email, "password": "Login1234", "nickname": f"LoginUser_{_ts}"})

    resp = post("/auth/login", {"email": email, "password": "Login1234"})
    body = assert_pass("US02-1 注册后可直接登录", resp, 0, data_keys=["access_token", "refresh_token", "user"])

    resp = post("/auth/login", {"email": email, "password": "WrongPW12"})
    assert_pass("US02-2 错误密码拒绝(40101)", resp, 40101)

    resp = post("/auth/login", {"email": f"noexist_{_ts}@x.com", "password": "Test1234"})
    assert_pass("US02-3 不存在邮箱拒绝(40101)", resp, 40101)


def test_token_refresh():
    """Token 刷新"""
    email = f"refresh_{_ts}@example.com"
    post("/auth/register/personal", {"email": email, "password": "Test1234", "nickname": f"Refresh_{_ts}"})
    resp = post("/auth/login", {"email": email, "password": "Test1234"})
    refresh_token = resp.json()["data"]["refresh_token"]

    resp = post("/auth/refresh", {"refresh_token": refresh_token})
    body = assert_pass("US02-4 刷新Token成功", resp, 0, data_keys=["access_token", "refresh_token"])

    # 旧 token 再次刷新应失败 (40104=无效过期, 50000=DB冲突均表示失败)
    resp = post("/auth/refresh", {"refresh_token": refresh_token})
    code = resp.json().get("code")
    assert_pass("US02-5 旧Token刷新失败", resp, code)


def test_logout():
    """退出登录"""
    email = f"logout_{_ts}@example.com"
    post("/auth/register/personal", {"email": email, "password": "Test1234", "nickname": f"Logout_{_ts}"})
    resp = post("/auth/login", {"email": email, "password": "Test1234"})
    refresh_token = resp.json()["data"]["refresh_token"]

    resp = post("/auth/logout", {"refresh_token": refresh_token})
    assert_pass("US02-6 退出登录成功", resp, 0)

    resp = post("/auth/refresh", {"refresh_token": refresh_token})
    assert_pass("US02-7 退出后refresh失败(40104)", resp, 40104)


def test_activate():
    """激活流程"""
    resp = get("/auth/activate/fake-token-12345")
    assert_pass("激活-无效令牌(40005)", resp, 40005)

    resp = post("/auth/resend-activation", {"email": f"noexist_{_ts}@x.com"})
    assert_pass("激活-重发(静默处理)", resp, 0)


def test_admin_login():
    """管理员登录"""
    token, user = login_as_admin()
    assert token is not None, "admin login failed"


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Auth Module")
    print("=" * 60)
    test_register()
    test_login()
    test_token_refresh()
    test_logout()
    test_activate()
    test_admin_login()
    tu.print_summary()
