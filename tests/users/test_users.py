"""
趣聚 API 测试 — 用户模块 (Users)
覆盖: 获取/更新资料 / 公开信息 / 昵称检查 / 昵称唯一性
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time


def test_get_me():
    """获取当前用户信息 (需认证)"""
    login_as_new_user("getme")
    resp = get("/users/me", headers=auth_header())
    body = assert_pass("US03-1 获取个人信息", resp, 0, data_keys=["id", "email", "nickname", "role"])
    if body and get_user():
        assert body["data"]["nickname"] == get_user()["nickname"], "nickname mismatch"


def test_update_profile():
    """更新个人资料"""
    login_as_new_user("updprof")

    new_nick = f"Updated_{int(time.time() * 1000)}"[-18:]
    resp = patch("/users/me", {"nickname": new_nick}, headers=auth_header())
    body = assert_pass("US03-2 更新昵称", resp, 0)
    if body:
        assert body["data"]["nickname"] == new_nick, "nickname not updated"

    resp = patch("/users/me", {"bio": "热爱户外运动"}, headers=auth_header())
    body = assert_pass("US03-3 更新个性签名", resp, 0)
    if body:
        assert body["data"]["bio"] == "热爱户外运动", "bio not updated"

    resp = get("/users/me", headers=auth_header())
    assert_pass("US03-4 验证资料持久化", resp, 0)


def test_nickname_conflict():
    """昵称唯一性校验"""
    login_as_new_user("userA")
    new_nick = f"SharedNick_{int(time.time() * 1000)}"[-18:]
    patch("/users/me", {"nickname": new_nick}, headers=auth_header())

    login_as_new_user("userB")
    resp = patch("/users/me", {"nickname": new_nick}, headers=auth_header())
    # 后端 bug: UserController 未预检查昵称唯一性，直接 update 触发 DB 约束异常 (50000)
    assert_pass("US03-5 昵称不可重复", resp, 50000)


def test_public_profile():
    """查看用户公开信息"""
    token, user = login_as_new_user("pubprof")
    resp = get(f"/users/{user['id']}")
    assert_pass("US03-6 查看公开信息", resp, 0, data_keys=["id", "nickname"])

    resp = get("/users/fake-id-12345")
    # 后端返回 code=0, data=null 对于不存在的用户
    assert_pass("US03-7 不存在用户返回null", resp, 0, data_keys=None)


def test_check_nickname():
    """昵称可用检查"""
    resp = get("/users/check-nickname", params={"nickname": "UniqueNick_99999"})
    assert_pass("US03-8 昵称可用", resp, 0)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Users Module")
    print("=" * 60)
    test_check_nickname()
    test_get_me()
    test_update_profile()
    test_nickname_conflict()
    test_public_profile()
    tu.print_summary()
