"""
趣聚 API 测试 — 用户模块 (Users)
GET  /api/users/me
PATCH /api/users/me
GET  /api/users/{id}
GET  /api/users/check-nickname
GET  /api/users/me/created-activities
GET  /api/users/me/joined-activities
"""

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from test_utils import *


def test_check_nickname():
    resp = get("/api/users/check-nickname", {"nickname": "UniqueNickXYZ123"})
    assert_pass("nickname available", resp, 0, data_keys=["available"])


def test_get_me_unauthorized():
    resp = get("/api/users/me")
    assert_pass("get me without auth", resp, 40101)


def test_get_public_profile():
    resp = get("/api/users/nonexistent-id")
    assert_pass("get nonexistent user", resp, 40401)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Users Module")
    print("=" * 60)
    test_check_nickname()
    test_get_me_unauthorized()
    test_get_public_profile()
    print_summary()
