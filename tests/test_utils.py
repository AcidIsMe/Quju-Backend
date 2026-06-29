"""
趣聚 API 测试工具模块
Usage: import test_utils as tu; tu.post(...); tu.assert_pass(...)
"""

import requests
import json
import time

BASE_URL = "http://localhost:8080"

PASS = 0
FAIL = 0

# ---- 当前会话的登录态 ----
_current_token = None
_current_user = None


def post(path, payload=None, headers=None):
    return requests.post(f"{BASE_URL}{path}", json=payload, headers=headers, timeout=10)


def get(path, params=None, headers=None):
    return requests.get(f"{BASE_URL}{path}", params=params, headers=headers, timeout=10)


def put(path, payload=None, headers=None):
    return requests.put(f"{BASE_URL}{path}", json=payload, headers=headers, timeout=10)


def patch(path, payload=None, headers=None):
    return requests.patch(f"{BASE_URL}{path}", json=payload, headers=headers, timeout=10)


def delete(path, headers=None):
    return requests.delete(f"{BASE_URL}{path}", headers=headers, timeout=10)


def auth_header(token=None):
    t = token or _current_token
    return {"Authorization": f"Bearer {t}"}


def get_token():
    return _current_token


def get_user():
    return _current_user


def login_as_new_user(prefix="testuser"):
    """
    一键完成: 注册 → 激活 → 登录 → 返回 (token, user)
    调用后自动设置全局 _current_token，后续测试直接用 auth_header() 即可
    """
    global _current_token, _current_user
    ts = str(int(time.time() * 1000))[-8:]
    email = f"{prefix}_{ts}@example.com"
    nickname = f"{prefix}_{ts}"
    password = "Test1234"

    # 1. 注册
    resp = post("/api/auth/register/personal", {
        "email": email, "password": password, "nickname": nickname
    })
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! login_as_new_user: register failed - {body}")
        return None, None
    activation_token = body["data"].get("activation_token")
    if not activation_token:
        print("  !! login_as_new_user: no activation_token in response")
        return None, None

    # 2. 激活
    resp = get(f"/api/auth/activate/{activation_token}")
    if resp.json().get("code") != 0:
        print(f"  !! login_as_new_user: activate failed - {resp.json()}")
        return None, None

    # 3. 登录
    resp = post("/api/auth/login", {"email": email, "password": password})
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! login_as_new_user: login failed - {body}")
        return None, None

    _current_token = body["data"]["access_token"]
    _current_user = body["data"]["user"]
    print(f"  [auth] logged in as {_current_user['nickname']} (token={_current_token[:20]}...)")
    return _current_token, _current_user


def login_as_admin():
    """
    管理员登录: admin@quju.com / Admin12345 (由 DataInitializer 创建)
    返回 (token, user)
    """
    global _current_token, _current_user
    resp = post("/api/auth/login", {"email": "admin@quju.com", "password": "Admin12345"})
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! login_as_admin: login failed - {body}")
        return None, None
    _current_token = body["data"]["access_token"]
    _current_user = body["data"]["user"]
    print(f"  [auth] admin logged in as {_current_user['nickname']} (role={_current_user['role']})")
    return _current_token, _current_user


def assert_pass(test_name, resp, expected_code, msg_contains=None, data_keys=None):
    global PASS, FAIL
    try:
        body = resp.json()
        code = body.get("code", -1)
        msg = body.get("message", "")
        data = body.get("data")

        print(f"\n{'─'*50}")
        print(f"[{test_name}]")
        print(f"  code={code}, msg={msg}")
        if data is not None:
            s = json.dumps(data, ensure_ascii=False)
            print(f"  data: {s[:300]}{'...' if len(s)>300 else ''}")

        ok = True
        if code != expected_code:
            print(f"  >> FAIL: expected code={expected_code}, got code={code}, msg={msg}")
            ok = False
        if msg_contains and msg_contains not in msg:
            print(f"  >> FAIL: msg does not contain '{msg_contains}'")
            ok = False
        if data_keys and isinstance(data, dict):
            for key in data_keys:
                if key not in data:
                    print(f"  >> FAIL: data missing key '{key}'")
                    ok = False

        if ok:
            print(f"  >> PASS")
            PASS += 1
        else:
            FAIL += 1
        return body
    except Exception as e:
        print(f"\n[{test_name}]")
        print(f"  >> FAIL: {e}")
        FAIL += 1
        return None


def print_summary():
    total = PASS + FAIL
    print(f"\n{'='*60}")
    print(f"  Result: {PASS} passed, {FAIL} failed, {total} total")
    print(f"{'='*60}")
    return FAIL == 0


def reset_stats():
    global PASS, FAIL
    PASS = 0
    FAIL = 0
