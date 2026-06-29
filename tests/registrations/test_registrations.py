"""
趣聚 API 测试 — 报名模块 (Registration)
覆盖 US19 活动报名校验 / US20 取消报名
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from test_utils import *
import time

_ts = str(int(time.time() * 1000))[-8:]

def _future(hours=72):
    import datetime
    t = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))) + datetime.timedelta(hours=hours)
    return t.strftime("%Y-%m-%dT%H:%M:%S+08:00")

def _create_published_activity():
    """创建一个已发布的活动，返回 activity_id"""
    login_as_new_user(f"org_{_ts}")
    resp = post("/api/activities", {
        "title": f"报名测试活动_{int(time.time())}",
        "description": "用于测试报名流程的活动",
        "tags": ["测试"],
        "start_time": _future(120),
        "end_time": _future(124),
        "registration_deadline": _future(96),
        "max_participants": 5,
        "city": "北京",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "published"
    }, headers=auth_header())
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! create activity failed: {body}")
        return None
    return body["data"]["id"]


def test_register_and_cancel():
    """US19+US20: 报名 → 重复报名拒绝 → 取消 → 重复取消拒绝"""
    activity_id = _create_published_activity()
    if not activity_id:
        print("  !! SKIP: 无法创建已发布活动")
        return

    login_as_new_user(f"part_{_ts}")
    resp = post(f"/api/activities/{activity_id}/register", {}, headers=auth_header())
    body = assert_pass("US19-1 报名成功", resp, 0, data_keys=["registration_id", "status"])
    if body and body.get("data", {}).get("status") != "registered":
        print(f"  >> WARN: status={body['data'].get('status')}, expected 'registered'")

    resp = post(f"/api/activities/{activity_id}/register", {}, headers=auth_header())
    assert_pass("US19-2 重复报名拒绝(40902)", resp, 40902)

    resp = post(f"/api/activities/{activity_id}/cancel-registration", {}, headers=auth_header())
    assert_pass("US20-1 取消报名成功", resp, 0)

    resp = post(f"/api/activities/{activity_id}/cancel-registration", {}, headers=auth_header())
    assert_pass("US20-2 重复取消失败(40900)", resp, 40900)


def test_register_full():
    """US19-3: 名额已满"""
    activity_id = _create_published_activity()
    if not activity_id:
        return

    for i in range(5):
        login_as_new_user(f"fill{i}_{_ts}")
        post(f"/api/activities/{activity_id}/register", {}, headers=auth_header())

    login_as_new_user(f"over_{_ts}")
    resp = post(f"/api/activities/{activity_id}/register", {}, headers=auth_header())
    assert_pass("US19-3 名额已满拒绝(40901)", resp, 40901)


def test_register_nonexistent():
    """US19-4: 报名不存在的活动"""
    login_as_new_user(f"noact_{_ts}")
    resp = post("/api/activities/non-existent-id/register", {}, headers=auth_header())
    assert_pass("US19-4 活动不存在(40401)", resp, 40401)


def test_unauthorized():
    """未认证拦截"""
    resp = post("/api/activities/fake-id/register", {})
    assert_pass("REG-未认证报名拦截(40101)", resp, 40101)
    resp = post("/api/activities/fake-id/cancel-registration", {})
    assert_pass("REG-未认证取消拦截(40101)", resp, 40101)
    resp = post("/api/activities/fake-id/join-waitlist", {})
    assert_pass("REG-未认证等待拦截(40101)", resp, 40101)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Registration (US19/US20)")
    print("=" * 60)
    test_unauthorized()
    test_register_nonexistent()
    test_register_and_cancel()
    test_register_full()
    print_summary()
