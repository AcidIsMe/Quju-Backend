"""
趣聚 API 测试 — 管理后台模块 (Admin)
GET  /api/admin/users
GET  /api/admin/users/{id}
POST /api/admin/users/{id}/ban
POST /api/admin/users/{id}/unban
"""

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from test_utils import *


def test_unauthorized():
    resp = get("/api/admin/users")
    assert_pass("admin list without auth", resp, 40101)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Admin Module")
    print("=" * 60)
    test_unauthorized()
    print_summary()
