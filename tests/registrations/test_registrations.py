"""
趣聚 API 测试 — 报名模块 (Registration)
覆盖: 报名 / 取消报名 / 名额已满
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time
import datetime


def _future(hours=72):
    t = datetime.datetime.now() + datetime.timedelta(hours=hours)
    return t.strftime("%Y-%m-%dT%H:%M:%S")


def _create_and_publish():
    """创建活动 → 提交审核 → 管理员批准 → 返回已发布的 activity_id"""
    login_as_new_user("org")
    resp = post("/activities", {
        "title": f"报名测试_{int(time.time())}",
        "description": "报名测试活动",
        "tags": ["测试"],
        "start_time": _future(120),
        "end_time": _future(124),
        "registration_deadline": _future(96),
        "max_participants": 5,
        "city": "北京",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())
    activity_id = resp.json()["data"]["id"]
    post(f"/activities/{activity_id}/submit", headers=auth_header())

    # 管理员审批使活动变为 published
    login_as_admin()
    post(f"/admin/activities/{activity_id}/review", {"action": "approve"}, headers=auth_header())

    return activity_id


def test_register():
    """报名活动"""
    activity_id = _create_and_publish()

    login_as_new_user("participant")
    resp = post(f"/activities/{activity_id}/register", {}, headers=auth_header())
    body = assert_pass("US19-1 报名成功", resp, 0, data_keys=["registration_id"])

    resp = post(f"/activities/{activity_id}/register", {}, headers=auth_header())
    assert_pass("US19-2 重复报名拒绝(40902)", resp, 40902)


def test_cancel():
    """取消报名"""
    activity_id = _create_and_publish()

    login_as_new_user("canceler")
    post(f"/activities/{activity_id}/register", {}, headers=auth_header())

    resp = post(f"/activities/{activity_id}/cancel-registration", {}, headers=auth_header())
    assert_pass("US20-1 取消报名成功", resp, 0)


def test_full():
    """名额已满"""
    activity_id = _create_and_publish()

    for i in range(5):
        login_as_new_user(f"filler{i}")
        post(f"/activities/{activity_id}/register", {}, headers=auth_header())

    login_as_new_user("overflow")
    resp = post(f"/activities/{activity_id}/register", {}, headers=auth_header())
    assert_pass("US19-3 名额已满拒绝(40901)", resp, 40901)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Registration Module")
    print("=" * 60)
    test_register()
    test_cancel()
    test_full()
    tu.print_summary()
