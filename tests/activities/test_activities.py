"""
趣聚 API 测试 — 活动模块 (Activities)
覆盖: 创建 / 详情 / 更新 / 克隆 / 删除 / 提交审核
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


def test_create_draft():
    """创建草稿活动"""
    login_as_new_user("act_creator")
    resp = post("/activities", {
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
    return body


def test_create_and_submit():
    """创建活动并提交审核"""
    login_as_new_user("publisher")
    resp = post("/activities", {
        "title": "桌游之夜",
        "description": "周末一起来玩桌游",
        "tags": ["桌游", "社交"],
        "activity_type": "桌游聚会",
        "start_time": _future(96),
        "end_time": _future(100),
        "registration_deadline": _future(72),
        "max_participants": 15,
        "city": "上海",
        "fee_type": "paid",
        "fee_amount": 30,
        "status": "draft"
    }, headers=auth_header())
    activity_id = resp.json()["data"]["id"]

    resp = post(f"/activities/{activity_id}/submit", headers=auth_header())
    body = assert_pass("US08-2 提交审核", resp, 0)
    return body


def test_get_detail():
    """查看活动详情"""
    login_as_new_user("detailv")
    resp = post("/activities", {
        "title": "详情测试活动",
        "description": "测试查看详情",
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

    resp = get(f"/activities/{activity_id}")
    body = assert_pass("US18-1 查看活动详情", resp, 0, data_keys=["id", "title"])

    resp = get("/activities/fake-id-99999")
    assert_pass("US18-2 不存在活动(40401)", resp, 40401)


def test_update_draft():
    """编辑草稿活动"""
    login_as_new_user("editor")
    resp = post("/activities", {
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

    resp = put(f"/activities/{activity_id}", {
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
    body = assert_pass("US11-1 编辑草稿", resp, 0)


def test_clone():
    """克隆活动"""
    login_as_new_user("cloner")
    resp = post("/activities", {
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

    resp = post(f"/activities/{activity_id}/clone", headers=auth_header())
    body = assert_pass("US10-1 克隆活动", resp, 0, data_keys=["id", "status"])


def test_delete_draft():
    """删除草稿"""
    login_as_new_user("deleter")
    resp = post("/activities", {
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

    resp = delete(f"/activities/{activity_id}", headers=auth_header())
    assert_pass("US11-2 删除草稿", resp, 0)

    resp = get(f"/activities/{activity_id}")
    assert_pass("US11-3 确认已删除(40401)", resp, 40401)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Activities Module")
    print("=" * 60)
    test_create_draft()
    test_create_and_submit()
    test_get_detail()
    test_update_draft()
    test_clone()
    test_delete_draft()
    tu.print_summary()
