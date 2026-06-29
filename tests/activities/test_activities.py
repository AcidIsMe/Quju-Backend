"""
趣聚 API 测试 — 活动模块 (Activities)
覆盖 US08 创建活动 / US11 草稿 / US18 活动详情 / US10 克隆
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time


def _future(hours=72):
    """返回 ISO 8601 格式的未来时间"""
    import datetime
    t = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))) + datetime.timedelta(hours=hours)
    return t.strftime("%Y-%m-%dT%H:%M:%S+08:00")


def test_create_draft():
    """US08+US11: 创建草稿活动"""
    login_as_new_user("activitycreator")

    resp = post("/api/activities", {
        "title": "周末徒步活动",
        "description": "一起去爬山徒步，享受大自然",
        "tags": ["户外", "徒步", "周末"],
        "activity_type": "户外徒步",
        "start_time": _future(72),
        "end_time": _future(76),
        "registration_deadline": _future(48),
        "max_participants": 20,
        "city": "北京",
        "location_name": "香山公园",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())

    body = assert_pass("US08-1 创建草稿", resp, 0, data_keys=["id", "status"])
    if body:
        assert body["data"]["status"] == "draft", f"Expected draft, got {body['data']['status']}"
    return body


def test_create_and_publish():
    """US08: 创建并发布活动（进入审核）"""
    login_as_new_user("publisher")

    resp = post("/api/activities", {
        "title": "桌游之夜",
        "description": "周末一起来玩桌游，认识新朋友",
        "tags": ["桌游", "社交"],
        "activity_type": "桌游聚会",
        "start_time": _future(96),
        "end_time": _future(100),
        "registration_deadline": _future(72),
        "max_participants": 15,
        "city": "上海",
        "location_name": "人民广场",
        "fee_type": "paid",
        "fee_amount": 30,
        "status": "pending_ai_review"
    }, headers=auth_header())

    body = assert_pass("US08-2 发布活动进入审核", resp, 0, data_keys=["id", "status"])
    if body:
        status = body["data"]["status"]
        assert status != "draft", "Status should not be draft when publishing"
    return body


def test_create_large_activity():
    """US08+US13: 大型活动进入人工审核"""
    login_as_new_user("bigevent")

    resp = post("/api/activities", {
        "title": "大型音乐节",
        "description": "年度大型音乐节活动",
        "tags": ["音乐", "大型活动"],
        "activity_type": "演出",
        "start_time": _future(200),
        "end_time": _future(210),
        "registration_deadline": _future(150),
        "max_participants": 100,
        "city": "北京",
        "location_name": "奥林匹克公园",
        "fee_type": "paid",
        "fee_amount": 199,
        "status": "pending_ai_review"
    }, headers=auth_header())

    body = assert_pass("US08-3 超50人进入人工审核", resp, 0)
    if body:
        assert body["data"]["status"] == "pending_manual_review", \
            f"Expected pending_manual_review, got {body['data']['status']}"


def test_get_activity_detail():
    """US18: 查看活动详情"""
    login_as_new_user("detailviewer")
    resp = post("/api/activities", {
        "title": "详情测试活动",
        "description": "测试查看详情功能",
        "tags": ["测试"],
        "start_time": _future(72),
        "end_time": _future(76),
        "registration_deadline": _future(48),
        "max_participants": 10,
        "city": "深圳",
        "location_name": "深圳湾",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())
    activity_id = resp.json()["data"]["id"]

    resp = get(f"/api/activities/{activity_id}")
    body = assert_pass("US18-1 查看活动详情", resp, 0, data_keys=["id", "title", "creator_id"])
    if body:
        assert body["data"]["title"] == "详情测试活动", "title mismatch"

    resp = get("/api/activities/fake-id-99999")
    assert_pass("US18-2 不存在活动", resp, 40401)


def test_update_activity():
    """US11: 编辑草稿活动"""
    login_as_new_user("editor")
    resp = post("/api/activities", {
        "title": "待编辑活动",
        "description": "原始描述",
        "tags": ["测试"],
        "start_time": _future(72),
        "end_time": _future(76),
        "registration_deadline": _future(48),
        "max_participants": 10,
        "city": "北京",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())
    activity_id = resp.json()["data"]["id"]

    resp = put(f"/api/activities/{activity_id}", {
        "title": "已编辑活动",
        "description": "修改后的描述",
        "tags": ["测试", "编辑"],
        "start_time": _future(72),
        "end_time": _future(76),
        "registration_deadline": _future(48),
        "max_participants": 15,
        "city": "北京",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())

    body = assert_pass("US11-1 编辑草稿", resp, 0, data_keys=["id", "status"])
    if body:
        assert body["data"]["status"] == "draft", "status should be draft after edit"


def test_clone_activity():
    """US10: 克隆活动"""
    login_as_new_user("cloner")
    resp = post("/api/activities", {
        "title": "原始活动",
        "description": "被克隆的原活动",
        "tags": ["测试"],
        "start_time": _future(72),
        "end_time": _future(76),
        "registration_deadline": _future(48),
        "max_participants": 20,
        "city": "北京",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())
    activity_id = resp.json()["data"]["id"]

    resp = post(f"/api/activities/{activity_id}/clone", headers=auth_header())
    body = assert_pass("US10-1 克隆活动", resp, 0, data_keys=["id", "status"])
    if body:
        assert body["data"]["status"] == "draft", "Cloned activity should be draft"
        assert body["data"]["id"] != activity_id, "Clone should have different id"


def test_delete_draft():
    """删除草稿"""
    login_as_new_user("deleter")
    resp = post("/api/activities", {
        "title": "待删除草稿",
        "description": "这个草稿将被删除",
        "tags": ["测试"],
        "start_time": _future(72),
        "end_time": _future(76),
        "registration_deadline": _future(48),
        "max_participants": 10,
        "city": "北京",
        "fee_type": "free",
        "fee_amount": 0,
        "status": "draft"
    }, headers=auth_header())
    activity_id = resp.json()["data"]["id"]

    resp = delete(f"/api/activities/{activity_id}", headers=auth_header())
    assert_pass("US11-2 删除草稿", resp, 0)

    resp = get(f"/api/activities/{activity_id}")
    assert_pass("US11-3 确认已删除", resp, 40401)


def test_unauthorized():
    """未认证创建活动被拦截"""
    resp = post("/api/activities", {
        "title": "test", "description": "test",
        "start_time": _future(72), "end_time": _future(76),
        "registration_deadline": _future(48), "max_participants": 10
    })
    assert_pass("US08-4 未认证被拦截", resp, 40101)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Activities Module (US08/10/11/18)")
    print("=" * 60)
    test_unauthorized()
    test_create_draft()
    test_create_and_publish()
    test_create_large_activity()
    test_get_activity_detail()
    test_update_activity()
    test_clone_activity()
    test_delete_draft()
    tu.print_summary()
