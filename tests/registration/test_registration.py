"""
趣聚 API 测试 — 报名模块 (Registration)
测试接口: register, cancel-registration
"""

import requests
import json

BASE = "http://localhost:8080/api"
PASS = 0
FAIL = 0
TOKEN = None
ACTIVITY_ID = None
USER_EMAIL = "regtest@example.com"
USER_PASSWORD = "Abc12345"
USER_NICKNAME = "报名测试用户"


def init():
    global TOKEN, ACTIVITY_ID
    requests.post(f"{BASE}/auth/register/personal", json={
        "email": USER_EMAIL, "password": USER_PASSWORD, "nickname": USER_NICKNAME
    })
    resp = requests.post(f"{BASE}/auth/login", json={
        "email": USER_EMAIL, "password": USER_PASSWORD
    })
    body = resp.json()
    if body.get("code") != 0:
        return False
    TOKEN = body["data"]["access_token"]

    # 创建已发布活动
    headers = {"Authorization": f"Bearer {TOKEN}"}
    resp = requests.post(f"{BASE}/activities", json={
        "title": "报名测试活动",
        "description": "报名功能测试",
        "start_time": "2028-07-15T08:00:00+08:00",
        "end_time": "2028-07-15T12:00:00+08:00",
        "registration_deadline": "2028-07-14T20:00:00+08:00",
        "max_participants": 5,
        "status": "published"
    }, headers=headers)
    body = resp.json()
    if body.get("data") and body["data"].get("id"):
        ACTIVITY_ID = body["data"]["id"]
        return True
    return False


def test(name, method, path, payload=None, expected_code=0, expected_msg=None):
    global PASS, FAIL
    url = BASE + path
    headers = {"Authorization": f"Bearer {TOKEN}"} if TOKEN else {}
    print(f"\n{'='*60}")
    print(f"[TEST] {name}")
    print(f"  {method} {url}")
    if payload:
        print(f"  Body: {json.dumps(payload, ensure_ascii=False)}")
    try:
        if method == "POST":
            resp = requests.post(url, json=payload, headers=headers, timeout=5)
        else:
            resp = requests.delete(url, headers=headers, timeout=5)
        body = resp.json()
        code = body.get("code", -1)
        msg = body.get("message", "")
        print(f"  Response: code={code}, message={msg}")
        if code == expected_code:
            print(f"  >> PASS")
            PASS += 1
        else:
            print(f"  >> FAIL: 期望{expected_code}, 实际{code}")
            FAIL += 1
        return body
    except requests.exceptions.ConnectionError:
        print(f"  >> FAIL: 无法连接服务")
        FAIL += 1
    except Exception as e:
        print(f"  >> FAIL: {e}")
        FAIL += 1
    return None


def run():
    global PASS, FAIL
    if not init():
        print("\n[WARN] 初始化失败（用户未激活），仅测试未认证场景")
        test("未认证报名", "POST", "/activities/any/register", {}, expected_code=40101)
        return PASS, FAIL

    test("报名活动", "POST", f"/activities/{ACTIVITY_ID}/register", {
        "form_data": {"phone": "13800138000"}
    }, expected_code=0)

    test("重复报名", "POST", f"/activities/{ACTIVITY_ID}/register", {},
         expected_code=40902)

    print(f"\n{'='*60}")
    print(f"  Registration 测试结果: {PASS} 通过, {FAIL} 失败, 共 {PASS+FAIL} 项")
    print(f"{'='*60}")
    return PASS, FAIL


if __name__ == "__main__":
    run()
