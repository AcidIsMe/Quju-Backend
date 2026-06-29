"""
趣聚 API 测试 — 认证模块 (Auth)
POST /api/auth/register/personal | login | logout | refresh
GET  /api/auth/activate/{token}
POST /api/auth/resend-activation
"""

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import random
import time

# 使用时间戳确保每次测试邮箱唯一
_ts = str(int(time.time() * 1000))[-8:]


def test_register():
    e1 = f"auth01_{_ts}@example.com"
    e2 = f"auth02_{_ts}@example.com"
    n1 = f"AuthTest01_{_ts}"
    
    resp = post("/api/auth/register/personal", {
        "email": e1, "password": "Test1234", "nickname": n1
    })
    assert_pass("normal register", resp, 0, data_keys=["email"])

    resp = post("/api/auth/register/personal", {
        "email": e1, "password": "Test1234", "nickname": "OtherNick"
    })
    assert_pass("duplicate email", resp, 40001)

    resp = post("/api/auth/register/personal", {
        "email": e2, "password": "Test1234", "nickname": n1
    })
    assert_pass("duplicate nickname", resp, 40003)

    resp = post("/api/auth/register/personal", {
        "email": "bad@x.com", "password": "short", "nickname": "ShortPw"
    })
    assert_pass("short password", resp, 40000)


def test_login():
    post("/api/auth/register/personal", {
        "email": f"logintest_{_ts}@example.com", "password": "Login12", "nickname": f"LoginUser_{_ts}"
    })
    resp = post("/api/auth/login", {"email": f"logintest_{_ts}@example.com", "password": "Login12"})
    assert_pass("bad credentials (not activated)", resp, 40101)

    resp = post("/api/auth/login", {"email": "logintest@example.com", "password": "WrongPW1"})
    assert_pass("wrong password", resp, 40101)

    resp = post("/api/auth/login", {"email": "noexist@x.com", "password": "Test1234"})
    assert_pass("nonexistent email", resp, 40101)


def test_activate():
    resp = get("/api/auth/activate/fake-token-12345")
    assert_pass("invalid activation token", resp, 40005)

    resp = post("/api/auth/resend-activation", {"email": "noexist@x.com"})
    assert_pass("resend to nonexistent", resp, 40401)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Auth Module")
    print("=" * 60)
    test_register()
    test_login()
    test_activate()
    tu.print_summary()
