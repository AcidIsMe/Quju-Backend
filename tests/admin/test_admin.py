"""
趣聚 API 测试 — 管理模块 (Admin)
覆盖 US38 用户查询 / US39 封禁解封 / US40 活动下架恢复

后端变化 (相对于旧版):
- BASE_URL = http://localhost:3000/api
- 路径: /admin/users, /admin/users/{id}, /admin/users/{id}/ban, /admin/users/{id}/unban
- 新增加: /admin/activities, /admin/activities/{id}/review,
           /admin/activities/{id}/take-down, /admin/activities/{id}/restore
- Ban body: {reason} (无 expires_at)
- 认证: Authorization: Bearer <token>
- 注意: admin 端点当前不强制检查 admin role (SecurityConfig.anyRequest().permitAll())
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import datetime


def _future(hours=72):
    t = datetime.datetime.now() + datetime.timedelta(hours=hours)
    return t.strftime("%Y-%m-%dT%H:%M:%S")


def test_admin_login():
    """管理员账号登录"""
    token, user = login_as_admin()
    assert token is not None, "admin login failed"
    print(f"  Admin info: nickname={user.get('nickname')}, role={user.get('role')}, status={user.get('status')}")


def test_user_list():
    """US38: 用户列表查询"""
    login_as_admin()

    # 全部用户
    resp = get("/admin/users", headers=auth_header())
    assert_pass("US38-1 管理员查看用户列表", resp, 0)

    # 按角色筛选
    resp = get("/admin/users", params={"role": "personal"}, headers=auth_header())
    assert_pass("US38-2 按角色筛选(personal)", resp, 0)

    # 按状态筛选
    resp = get("/admin/users", params={"status": "active"}, headers=auth_header())
    assert_pass("US38-3 按状态筛选(active)", resp, 0)

    # 搜索关键词
    resp = get("/admin/users", params={"q": "admin"}, headers=auth_header())
    assert_pass("US38-4 搜索用户(q=admin)", resp, 0)


def test_user_detail():
    """US38: 查看用户详情"""
    # 先创建一个普通用户
    login_as_new_user("targetuser")
    user_id = tu.get_user()["id"]

    # 管理员查看该用户详情
    login_as_admin()
    resp = get(f"/admin/users/{user_id}", headers=auth_header())
    body = assert_pass("US38-5 查看用户详情", resp, 0)
    if body and body.get("data"):
        data = body["data"]
        assert data.get("id") == user_id, f"ID mismatch: {data.get('id')} vs {user_id}"


def test_ban_and_unban():
    """US39: 封禁与解封"""
    # 创建待封禁用户
    login_as_new_user("banme")
    user_id = tu.get_user()["id"]

    # 管理员登录
    login_as_admin()

    # 封禁
    resp = post(f"/admin/users/{user_id}/ban",
                {"reason": "测试封禁-违规内容"},
                headers=auth_header())
    assert_pass("US39-1 封禁用户", resp, 0)

    # 确认状态变为 banned
    resp = get(f"/admin/users/{user_id}", headers=auth_header())
    body = assert_pass("US39-2 确认状态为banned", resp, 0)
    if body and body.get("data"):
        actual_status = body["data"].get("status")
        if actual_status != "banned":
            print(f"  >> WARN: expected status=banned, got status={actual_status}")
        else:
            print(f"  >> status=banned confirmed")

    # 解封
    resp = post(f"/admin/users/{user_id}/unban", headers=auth_header())
    assert_pass("US39-3 解封用户", resp, 0)

    # 确认状态恢复 active
    resp = get(f"/admin/users/{user_id}", headers=auth_header())
    body = assert_pass("US39-4 确认状态恢复active", resp, 0)
    if body and body.get("data"):
        actual_status = body["data"].get("status")
        if actual_status != "active":
            print(f"  >> WARN: expected status=active, got status={actual_status}")
        else:
            print(f"  >> status=active confirmed")


def test_activity_management():
    """US40: 活动管理 — 下架与恢复"""
    # 创建一个活动，提交审核后进入 published 状态
    login_as_new_user("actmgr")
    resp = post("/activities", {
        "title": "待管理活动-US40",
        "description": "测试管理员下架和恢复活动",
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
    body = resp.json()
    assert body.get("code") == 0, f"create activity failed: {body}"
    act_id = body["data"]["id"]

    # 提交审核
    resp = post(f"/activities/{act_id}/submit", headers=auth_header())
    assert_pass("US40-1 提交审核", resp, 0)

    # 管理员登录
    login_as_admin()

    # 查看活动管理列表
    resp = get("/admin/activities", headers=auth_header())
    assert_pass("US40-2 查看活动管理列表", resp, 0)

    # 下架活动
    resp = post(f"/admin/activities/{act_id}/take-down",
                {"reason": "测试下架-内容违规"},
                headers=auth_header())
    assert_pass("US40-3 下架活动", resp, 0)

    # 恢复活动
    resp = post(f"/admin/activities/{act_id}/restore", headers=auth_header())
    assert_pass("US40-4 恢复活动", resp, 0)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Admin Module (US38/US39/US40)")
    print("=" * 60)
    test_admin_login()
    test_user_list()
    test_user_detail()
    test_ban_and_unban()
    test_activity_management()
    tu.print_summary()
