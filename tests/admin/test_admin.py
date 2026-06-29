"""
趣聚 API 测试 — 管理模块 (Admin)
覆盖 US38 用户查询 / US39 封禁解封
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from test_utils import *
import time

_ts = str(int(time.time() * 1000))[-8:]


def test_admin_list_users():
    """US38-1: 管理员查看用户列表"""
    login_as_admin()
    resp = get("/api/admin/users", params={"limit": "20"}, headers=auth_header())
    assert_pass("US38-1 管理员查看用户列表", resp, 0)

    resp = get("/api/admin/users", params={"role": "personal", "limit": "10"}, headers=auth_header())
    assert_pass("US38-2 按角色筛选(role=personal)", resp, 0)

    resp = get("/api/admin/users", params={"q": "admin", "limit": "10"}, headers=auth_header())
    assert_pass("US38-3 搜索用户(q=admin)", resp, 0)


def test_admin_user_detail():
    """US38-4: 管理员查看用户详情"""
    login_as_new_user(f"target_{_ts}")
    user_id = get_user()["id"]

    login_as_admin()
    resp = get(f"/api/admin/users/{user_id}", headers=auth_header())
    body = assert_pass("US38-4 查看用户详情", resp, 0, data_keys=["id", "email", "nickname", "status"])
    if body:
        assert body["data"]["id"] == user_id, f"expected id={user_id}, got {body['data']['id']}"


def test_ban_and_unban():
    """US39: 封禁与解封"""
    login_as_new_user(f"banme_{_ts}")
    user_id = get_user()["id"]

    login_as_admin()

    # 封禁
    resp = post(f"/api/admin/users/{user_id}/ban", {
        "reason": "测试封禁: 发布违规内容"
    }, headers=auth_header())
    assert_pass("US39-1 封禁用户", resp, 0)

    # 确认用户状态已变为 banned
    resp = get(f"/api/admin/users/{user_id}", headers=auth_header())
    body = assert_pass("US39-2 确认状态为banned", resp, 0)
    if body and body.get("data"):
        assert body["data"]["status"] == "banned", f"expected banned, got {body['data']['status']}"

    # 解封
    resp = post(f"/api/admin/users/{user_id}/unban", {}, headers=auth_header())
    assert_pass("US39-3 解封用户", resp, 0)

    # 确认恢复
    resp = get(f"/api/admin/users/{user_id}", headers=auth_header())
    body = assert_pass("US39-4 确认状态恢复active", resp, 0)
    if body and body.get("data"):
        assert body["data"]["status"] == "active", f"expected active, got {body['data']['status']}"


def test_non_admin_rejected():
    """普通用户被拒绝"""
    login_as_new_user(f"normal_{_ts}")
    resp = get("/api/admin/users", headers=auth_header())
    assert_pass("ADM-普通用户拒绝(40300)", resp, 40300)

    resp = get(f"/api/admin/users/{get_user()['id']}", headers=auth_header())
    assert_pass("ADM-普通用户查看详情拒绝(40300)", resp, 40300)

    resp = post(f"/api/admin/users/{get_user()['id']}/ban", {"reason": "test"}, headers=auth_header())
    assert_pass("ADM-普通用户封禁拒绝(40300)", resp, 40300)


def test_unauthorized():
    """未认证被拦截"""
    resp = get("/api/admin/users")
    assert_pass("ADM-未认证拦截(40101)", resp, 40101)

    resp = get("/api/admin/users/some-id")
    assert_pass("ADM-未认证详情拦截(40101)", resp, 40101)

    resp = post("/api/admin/users/some-id/ban", {"reason": "test"})
    assert_pass("ADM-未认证封禁拦截(40101)", resp, 40101)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Admin (US38/US39)")
    print("=" * 60)
    test_unauthorized()
    test_non_admin_rejected()
    test_admin_list_users()
    test_admin_user_detail()
    test_ban_and_unban()
    print_summary()
