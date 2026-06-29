"""
趣聚 API 测试 — 用户模块 (Users)
覆盖 US03 个人资料管理
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time


def test_get_me():
    """US03: 获取当前用户信息"""
    token, user = login_as_new_user("userme")
    assert token is not None, "login failed"

    resp = get("/api/users/me", headers=auth_header())
    body = assert_pass("US03-1 获取个人信息", resp, 0, data_keys=["id", "email", "nickname", "role", "status"])

    if body:
        assert body["data"]["nickname"] == user["nickname"], "nickname mismatch"


def test_update_profile():
    """US03: 更新个人资料"""
    login_as_new_user("updprofile")

    new_nick = f"Updated_{str(int(time.time() * 1000))[-12:]}"
    resp = patch("/api/users/me", {"nickname": new_nick}, headers=auth_header())
    body = assert_pass("US03-2 更新昵称", resp, 0)
    if body:
        assert body["data"]["nickname"] == new_nick, "nickname not updated"

    resp = patch("/api/users/me", {"bio": "热爱户外运动"}, headers=auth_header())
    body = assert_pass("US03-3 更新个性签名", resp, 0)
    if body:
        assert body["data"]["bio"] == "热爱户外运动", "bio not updated"

    resp = patch("/api/users/me", {"interest_tags": ["户外", "徒步", "摄影"]}, headers=auth_header())
    assert_pass("US03-4 更新兴趣标签", resp, 0)

    resp = get("/api/users/me", headers=auth_header())
    body = assert_pass("US03-5 验证资料持久化", resp, 0)
    if body:
        assert body["data"]["nickname"] == new_nick, "nickname not persisted"
        assert body["data"]["bio"] == "热爱户外运动", "bio not persisted"


def test_nickname_unique():
    """昵称唯一性校验"""
    ts = str(int(time.time() * 1000))[-8:]
    shared_nick = f"SharedNick_{ts}"

    login_as_new_user("userA")
    resp = patch("/api/users/me", {"nickname": shared_nick}, headers=auth_header())
    assert_pass("US03-6a 设置昵称", resp, 0)

    login_as_new_user("userB")
    resp = patch("/api/users/me", {"nickname": shared_nick}, headers=auth_header())
    assert_pass("US03-6b 昵称不可重复", resp, 40003)


def test_public_profile():
    """查看用户公开信息"""
    token, user = login_as_new_user("pubprofile")

    resp = get(f"/api/users/{user['id']}")
    body = assert_pass("US03-7 查看公开信息", resp, 0, data_keys=["id", "nickname", "credit_score"])

    resp = get("/api/users/fake-id-12345")
    assert_pass("US03-8 不存在用户", resp, 40401)


def test_check_nickname():
    """昵称检查（公开接口）"""
    resp = get("/api/users/check-nickname", params={"nickname": "UniqueNick_99999"})
    assert_pass("US03-9 昵称可用", resp, 0)


def test_unauthorized():
    """未认证访问保护"""
    resp = get("/api/users/me")
    assert_pass("US03-10 未认证被拦截", resp, 40101)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Users Module (US03)")
    print("=" * 60)
    test_unauthorized()
    test_get_me()
    test_update_profile()
    test_nickname_unique()
    test_public_profile()
    test_check_nickname()
    tu.print_summary()
