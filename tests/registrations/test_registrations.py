"""
趣聚 API 测试 — 报名模块 (Registrations)
测试:
  - POST /api/activities/{id}/register
  - POST /api/activities/{id}/cancel-registration
  - POST /api/activities/{id}/join-waitlist
  - DELETE /api/activities/{id}/leave-waitlist
"""

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from test_utils import *


def test_register_unauthorized():
    resp = post("/api/activities/fake-id/register")
    assert_pass("未认证报名", resp, 40101, "登录")


def test_cancel_unauthorized():
    resp = post("/api/activities/fake-id/cancel-registration")
    assert_pass("未认证取消报名", resp, 40101, "登录")


def test_join_waitlist_unauthorized():
    resp = post("/api/activities/fake-id/join-waitlist")
    assert_pass("未认证加入等待", resp, 40101, "登录")


if __name__ == "__main__":
    print("=" * 60)
    print("    趣聚 API 测试 — Registration 报名模块")
    print("=" * 60)
    print("\n> 确认 Spring Boot 已启动: http://localhost:8080\n")

    test_register_unauthorized()
    test_cancel_unauthorized()
    test_join_waitlist_unauthorized()

    print_summary()
