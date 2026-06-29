"""
趣聚 API 测试 — 发现模块 (Discover)
GET /api/discover/recommended | latest | nearby | search | filter | map
"""

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from test_utils import *


def test_all():
    resp = get("/api/discover/recommended", {"cursor": 0, "limit": 5})
    assert_pass("recommended", resp, 0, data_keys=["data", "pagination"])

    resp = get("/api/discover/latest", {"cursor": 0, "limit": 5})
    assert_pass("latest", resp, 0, data_keys=["data", "pagination"])

    resp = get("/api/discover/nearby", {"lat": 40.0178, "lng": 116.3912, "radius": 5000, "cursor": 0, "limit": 5})
    assert_pass("nearby", resp, 0, data_keys=["data", "pagination"])

    resp = get("/api/discover/search", {"q": "hiking", "cursor": 0, "limit": 5})
    assert_pass("search", resp, 0, data_keys=["data", "pagination"])

    resp = get("/api/discover/filter", {"activity_types": "sport,music", "cursor": 0, "limit": 5})
    assert_pass("filter by types", resp, 0, data_keys=["data", "pagination"])

    resp = get("/api/discover/filter", {"city": "Beijing", "fee_type": "free", "cursor": 0, "limit": 5})
    assert_pass("filter by city+free", resp, 0, data_keys=["data", "pagination"])

    resp = get("/api/discover/map", {"sw_lat": 39.9, "sw_lng": 116.3, "ne_lat": 40.1, "ne_lng": 116.5})
    assert_pass("map view", resp, 0)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Discover Module")
    print("=" * 60)
    test_all()
    print_summary()
