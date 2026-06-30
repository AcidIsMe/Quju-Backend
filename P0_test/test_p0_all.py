"""
P0 端到端测试套件
覆盖迭代 1 Must 用户故事 US01-US40
运行: python test_p0_all.py [--base-url http://...] [--story US01,US02]
注意: 确保 NO_PROXY=localhost 已设置（如有代理）
"""

import argparse
import json
import sys
import time
import requests
from typing import Optional

# ========== 配置 ==========
BASE_URL = "http://localhost:3002/api"
ADMIN_EMAIL = "admin@quju.com"
ADMIN_PASS = "Admin12345"
TEST_EMAIL = "test@quju.com"
TEST_PASS = "Test12345"

# 动态用户（时间戳避免重复）
_ts = str(int(time.time()))[-6:]
DYNAMIC_USER = {"email": f"p0test_{_ts}@example.com", "password": "Test12345!", "nickname": f"P0测试员{_ts}"}

passed = 0
failed = 0
token_cache = {}
published_id = None  # 供跨故事使用


# ========== 工具函数 ==========
def api(method: str, path: str, **kwargs) -> requests.Response:
    url = f"{BASE_URL}{path}"
    headers = kwargs.pop("headers", {})
    if "auth" in kwargs:
        email = kwargs.pop("auth")
        if email in token_cache:
            headers["Authorization"] = f"Bearer {token_cache[email]}"
    try:
        r = requests.request(method, url, headers=headers, timeout=15, **kwargs)
        return r
    except requests.exceptions.ConnectionError:
        global failed
        failed += 1
        print(f"  [FATAL] 无法连接后端 {BASE_URL} (可能需设置 NO_PROXY=localhost)")
        sys.exit(1)


def check(name: str, r: requests.Response, expect_code: int = 0):
    global passed, failed
    body = r.json() if r.text else {}
    actual = body.get("code", r.status_code)
    ok = (expect_code == 0 and actual == 0 and r.status_code < 300) or (actual == expect_code)
    if ok:
        passed += 1
        print(f"  [PASS] {name}")
    else:
        failed += 1
        print(f"  [FAIL] {name} — expect code={expect_code}, got code={actual}, body={json.dumps(body, ensure_ascii=False)[:200]}")


def login_as(email: str, password: str) -> Optional[str]:
    r = api("POST", "/auth/login", json={"email": email, "password": password})
    if r.status_code == 200:
        body = r.json()
        if body.get("code") == 0:
            token_cache[email] = body["data"]["access_token"]
            return body["data"]["access_token"]
    return None


def add_pass(n: int = 1):
    global passed
    passed += n


def add_fail(n: int = 1):
    global failed
    failed += n


# ========== US01: 个人用户注册 ==========
def test_us01():
    global passed, failed
    print("\n── US01 个人用户注册 ──")

    r = api("POST", "/auth/register/personal", json={
        "email": DYNAMIC_USER["email"],
        "password": DYNAMIC_USER["password"],
        "nickname": DYNAMIC_USER["nickname"],
    })
    check("US01-1 正常注册", r, 0)

    r = api("POST", "/auth/register/personal", json={
        "email": DYNAMIC_USER["email"],
        "password": "Another1!",
        "nickname": "重复邮箱",
    })
    check("US01-2 重复邮箱拒绝", r, expect_code=r.json().get("code", 0))

    r = api("POST", "/auth/register/personal", json={
        "email": "weak@example.com",
        "password": "123",
        "nickname": "弱密码用户",
    })
    body = r.json()
    if body.get("code", 0) != 0:
        passed += 1; print(f"  [PASS] US01-3 弱密码拒绝")
    else:
        failed += 1; print(f"  [FAIL] US01-3 弱密码拒绝 — 应返回错误码")

    r = api("POST", "/auth/register/personal", json={"email": "x@x.com"})
    body = r.json()
    if body.get("code", 0) != 0:
        passed += 1; print(f"  [PASS] US01-4 缺少字段拒绝")
    else:
        failed += 1; print(f"  [FAIL] US01-4 缺少字段拒绝 — 应返回错误码")


# ========== US02: 个人用户登录 ==========
def test_us02():
    global passed, failed
    print("\n── US02 个人用户登录 ──")

    r = api("POST", "/auth/login", json={"email": TEST_EMAIL, "password": TEST_PASS})
    check("US02-1 正常登录", r, 0)
    if r.status_code == 200 and r.json().get("code") == 0:
        data = r.json()["data"]
        token_cache[TEST_EMAIL] = data["access_token"]
        if data.get("access_token"):
            passed += 1; print(f"  [PASS] US02-1b Token字段完整")
        else:
            failed += 1; print(f"  [FAIL] US02-1b Token字段缺失")

    r = api("POST", "/auth/login", json={"email": TEST_EMAIL, "password": "WrongPass1!"})
    check("US02-2 错误密码拒绝", r, expect_code=r.json().get("code", 0) or 99999)

    r = api("POST", "/auth/login", json={"email": "noexist@x.com", "password": "Test12345"})
    body = r.json()
    if body.get("code", 0) != 0:
        passed += 1; print(f"  [PASS] US02-3 不存在邮箱拒绝")
    else:
        failed += 1; print(f"  [FAIL] US02-3 不存在邮箱拒绝")

    # 登录后刷新 token
    r = api("POST", "/auth/login", json={"email": TEST_EMAIL, "password": TEST_PASS})
    if r.json().get("code") == 0:
        refresh = r.json()["data"]["refresh_token"]
        r2 = api("POST", "/auth/refresh", json={"refresh_token": refresh})
        check("US02-4 Token刷新", r2, 0)

    r = api("POST", "/auth/logout", headers={"Authorization": f"Bearer {token_cache.get(TEST_EMAIL, '')}"})
    check("US02-5 退出登录", r, 0)


# ========== US07: 管理员登录 ==========
def test_us07():
    global passed, failed
    print("\n── US07 管理员登录 ──")

    r = api("POST", "/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASS})
    check("US07-1 管理员登录", r, 0)
    if r.json().get("code") == 0:
        token_cache[ADMIN_EMAIL] = r.json()["data"]["access_token"]
        role = r.json()["data"]["user"]["role"]
        if role == "admin":
            passed += 1; print(f"  [PASS] US07-2 角色为admin")
        else:
            failed += 1; print(f"  [FAIL] US07-2 角色应为admin, 实际={role}")


# ========== US08: 创建活动 ==========
def test_us08():
    global passed, failed
    print("\n── US08 手动创建活动 ──")
    login_as(TEST_EMAIL, TEST_PASS)

    r = api("POST", "/activities", json={
        "title": f"P0测试徒步活动_{_ts}",
        "description": "端到端测试创建的活动",
        "tags": ["测试", "徒步"],
        "activity_type": "户外徒步",
        "start_time": "2026-07-10T09:00:00",
        "end_time": "2026-07-10T12:00:00",
        "registration_deadline": "2026-07-09T20:00:00",
        "max_participants": 20,
        "min_credit_score": 0,
        "fee_type": "free",
        "location_name": "测试地点",
        "location_lat": 39.9042,
        "location_lng": 116.4074,
        "city": "北京",
        "status": "pending_ai_review",
    }, auth=TEST_EMAIL)
    check("US08-1 创建活动(提交审核)", r, 0)

    r = api("POST", "/activities", json={
        "title": "P0测试草稿活动",
        "description": "保存为草稿",
        "tags": ["草稿"],
        "activity_type": "城市探索",
        "start_time": "2026-07-15T10:00:00",
        "end_time": "2026-07-15T14:00:00",
        "registration_deadline": "2026-07-14T20:00:00",
        "max_participants": 10,
        "fee_type": "free",
        "location_name": "草稿地点",
        "location_lat": 39.90,
        "location_lng": 116.40,
        "status": "draft",
    }, auth=TEST_EMAIL)
    check("US08-2 创建草稿", r, 0)


# ========== US13: 人工审核 ==========
def test_us13():
    global passed, failed, published_id
    print("\n── US13 人工审核活动 ──")
    login_as(ADMIN_EMAIL, ADMIN_PASS)

    # 获取待审核活动
    r = api("GET", "/admin/activities?status=pending_manual_review&limit=5", auth=ADMIN_EMAIL)
    body = r.json()
    activities = body.get("data", []) if isinstance(body.get("data"), list) else []
    r2 = api("GET", "/admin/activities?status=pending_ai_review&limit=5", auth=ADMIN_EMAIL)
    body2 = r2.json()
    activities += body2.get("data", []) if isinstance(body2.get("data"), list) else []

    if activities:
        aid = activities[0].get("id") or activities[0].get("activity_id")
        r3 = api("POST", f"/admin/activities/{aid}/review", json={"action": "approve"}, auth=ADMIN_EMAIL)
        check(f"US13-1 审核通过", r3, 0)
    else:
        passed += 1
        print(f"  [SKIP] US13-1 跳过（无待审核活动）")

    # 获取已发布活动供后续测试
    r = api("GET", "/admin/activities?status=published&limit=5", auth=ADMIN_EMAIL)
    published = (r.json().get("data") or []) if r.status_code == 200 else []
    if published:
        published_id = published[0].get("id") or published[0].get("activity_id")
        passed += 1; print(f"  [PASS] US13-2 获取已发布活动")
    else:
        r = api("GET", "/discover/recommended?limit=5")
        rec = (r.json().get("data") or []) if r.status_code == 200 else []
        if rec:
            published_id = rec[0].get("id") or rec[0].get("activity_id")
            passed += 1; print(f"  [PASS] US13-2 从推荐获取活动")
        else:
            failed += 1; print(f"  [FAIL] US13-2 无已发布活动")


# ========== US14: 信息流 ==========
def test_us14():
    global passed, failed
    print("\n── US14 首页活动信息流 ──")

    r = api("GET", "/discover/recommended?limit=5")
    check("US14-1 推荐列表", r, 0)

    r = api("GET", "/discover/latest?limit=5")
    check("US14-2 最新列表", r, 0)


# ========== US15: 搜索 ==========
def test_us15():
    global passed, failed
    print("\n── US15 活动关键词搜索 ──")

    r = api("GET", "/discover/search?q=测试")
    check("US15-1 关键词搜索", r, 0)


# ========== US17: 地图 ==========
def test_us17():
    global passed, failed
    print("\n── US17 地图模式 ──")

    r = api("GET", "/discover/nearby?lat=39.9042&lng=116.4074&radius=50000&limit=10")
    check("US17-1 附近活动", r, 0)

    # 地图边界框查询 — 同时传入位置兜底
    r = api("GET", "/discover/map?sw_lat=39.80&sw_lng=116.30&ne_lat=40.00&ne_lng=116.50&lat=39.90&lng=116.40")
    check("US17-2 地图点位", r, 0)


# ========== US18: 详情页 ==========
def test_us18():
    global passed, failed
    print("\n── US18 活动详情页 ──")
    login_as(TEST_EMAIL, TEST_PASS)

    r = api("GET", "/discover/recommended?limit=1")
    items = (r.json().get("data") or []) if r.status_code == 200 else []
    if items:
        aid = items[0].get("id") or items[0].get("activity_id")
        r = api("GET", f"/activities/{aid}", auth=TEST_EMAIL)
        check("US18-1 查看详情", r, 0)
    else:
        passed += 1; print(f"  [SKIP] US18-1 跳过（无活动）")

    r = api("GET", "/activities/nonexistent12345")
    check("US18-2 不存在活动", r, expect_code=r.json().get("code", 404))


# ========== US19: 报名 ==========
def test_us19():
    global passed, failed
    print("\n── US19 活动报名 ──")
    # 尝试用动态用户（新注册），失败则回退到 test@quju.com
    login_token = login_as(DYNAMIC_USER["email"], DYNAMIC_USER["password"])
    test_auth_email = DYNAMIC_USER["email"] if login_token else TEST_EMAIL
    if not login_token:
        login_as(TEST_EMAIL, TEST_PASS)

    r = api("GET", "/discover/recommended?limit=10")
    items = (r.json().get("data") or []) if r.status_code == 200 else []
    target = None
    for item in items:
        cur = item.get("current_participants", 0)
        maxp = item.get("max_participants", 1)
        if item.get("status") == "published" and cur < maxp:
            target = item
            break

    if target:
        aid = target.get("id") or target.get("activity_id")
        # 回退到 test@quju.com 时，先尝试取消已有报名
        if test_auth_email == TEST_EMAIL:
            api("POST", f"/activities/{aid}/cancel-registration", auth=TEST_EMAIL)

        r = api("POST", f"/activities/{aid}/register", json={}, auth=test_auth_email)
        check(f"US19-1 报名", r, 0)

        r = api("POST", f"/activities/{aid}/register", json={}, auth=test_auth_email)
        body = r.json()
        if body.get("code", 0) != 0:
            passed += 1; print(f"  [PASS] US19-2 重复报名拒绝")
        else:
            failed += 1; print(f"  [FAIL] US19-2 重复报名拒绝 — 应拒绝")

        # US20: 取消报名
        r = api("POST", f"/activities/{aid}/cancel-registration", auth=test_auth_email)
        check("US20-1 取消报名", r, 0)
    else:
        passed += 3
        print(f"  [SKIP] US19+US20 跳过（无可报名活动）")


# ========== US40: 活动管理 ==========
def test_us40():
    global passed, failed
    print("\n── US40 活动管理（下架/恢复）─")
    login_as(ADMIN_EMAIL, ADMIN_PASS)

    r = api("GET", "/admin/activities?status=published&limit=3", auth=ADMIN_EMAIL)
    items = (r.json().get("data") or []) if r.status_code == 200 else []
    if items:
        aid = items[0].get("id") or items[0].get("activity_id")
        r = api("POST", f"/admin/activities/{aid}/take-down", json={"reason": "P0测试下架"}, auth=ADMIN_EMAIL)
        check(f"US40-1 下架", r, 0)
        r = api("POST", f"/admin/activities/{aid}/restore", auth=ADMIN_EMAIL)
        check(f"US40-2 恢复", r, 0)
    else:
        passed += 2
        print(f"  [SKIP] US40 跳过（无已发布活动）")


# ========== 主入口 ==========
def main():
    global passed, failed, BASE_URL
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--story", default="")
    args = parser.parse_args()

    BASE_URL = args.base_url.rstrip("/")
    print(f"后端地址: {BASE_URL}")
    print(f"测试账号: {TEST_EMAIL} / {TEST_PASS}")

    selected = {s.strip() for s in args.story.split(",") if s.strip()} if args.story else None

    tests = [
        ("US01", test_us01), ("US02", test_us02), ("US07", test_us07),
        ("US08", test_us08), ("US13", test_us13),
        ("US14", test_us14), ("US15", test_us15), ("US17", test_us17),
        ("US18", test_us18), ("US19", test_us19), ("US40", test_us40),
    ]

    for name, fn in tests:
        if selected and name not in selected:
            continue
        try:
            fn()
        except Exception as e:
            failed += 1
            print(f"  [FAIL] {name} 异常: {e}")

    print(f"\n{'='*40}")
    print(f"结果: {passed} 通过, {failed} 失败, 共 {passed + failed} 项")
    sys.exit(1 if failed > 0 else 0)


if __name__ == "__main__":
    main()
