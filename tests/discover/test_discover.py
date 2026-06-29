"""
趣聚 API 测试 — 发现模块 (Discover)
覆盖: 推荐 / 最新 / 搜索 / 筛选 / 附近 / 地图
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *


def test_recommended():
    resp = get("/discover/recommended", params={"limit": "10"})
    assert_pass("US14-1 推荐活动", resp, 0)


def test_latest():
    resp = get("/discover/latest", params={"limit": "10"})
    assert_pass("US14-2 最新活动", resp, 0)


def test_search():
    resp = get("/discover/search", params={"q": "徒步", "limit": "10"})
    assert_pass("US15-1 关键词搜索", resp, 0)


def test_filter():
    resp = get("/discover/search", params={"activityTypes": "户外徒步,桌游聚会", "limit": "10"})
    assert_pass("US16-1 按类型筛选", resp, 0)
    resp = get("/discover/search", params={"city": "北京", "feeType": "free", "limit": "10"})
    assert_pass("US16-2 按城市+费用筛选", resp, 0)


def test_nearby():
    resp = get("/discover/nearby", params={"lat": "40.0178", "lng": "116.3912", "radius": "5000", "limit": "10"})
    assert_pass("US17-1 附近活动", resp, 0)


def test_map_view():
    resp = get("/discover/nearby", params={"lat": "40.0", "lng": "116.3", "limit": "20"})
    assert_pass("US17-2 地图点位", resp, 0)


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Discover Module")
    print("=" * 60)
    test_recommended()
    test_latest()
    test_search()
    test_filter()
    test_nearby()
    test_map_view()
    tu.print_summary()
