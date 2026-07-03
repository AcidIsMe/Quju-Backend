"""
趣聚 API 测试 — 商家模块 (Merchants)
覆盖: US04 商家注册 / US05 商家资料管理 / US06 商家审核 / 公开商家列表 / 领域筛选
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import test_utils as tu
from test_utils import *
import time

_ts = str(int(time.time() * 1000))[-8:]


def _register_merchant(prefix, domains=None):
    """注册商家并激活，返回 (token, user, merchant_profile)"""
    email = f"{prefix}_{_ts}@example.com"
    nickname = f"{prefix}_{_ts}"
    password = "Merchant123"
    resp = post("/auth/register/merchant", {
        "email": email,
        "password": password,
        "nickname": nickname,
        "merchant_name": f"{prefix}商家-{_ts}",
        "activity_domains": domains or ["户外", "运动"],
        "license_image_url": "https://example.com/license.jpg"
    })
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! merchant register failed: {body}")
        return None, None, None

    # 激活
    activation_token = body["data"].get("activation_token")
    if activation_token:
        resp = get(f"/auth/activate/{activation_token}")
        if resp.json().get("code") != 0:
            print(f"  !! merchant activate failed: {resp.json()}")
            return None, None, None

    # 登录
    resp = post("/auth/login", {"email": email, "password": password})
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! merchant login failed: {body}")
        return None, None, None

    token = body["data"]["access_token"]
    user = body["data"]["user"]
    tu._current_token = token
    tu._current_user = user
    print(f"  [auth] merchant logged in as {user['nickname']} (role={user['role']})")

    # 获取商家资料
    resp = get("/merchants/me", headers=auth_header(token))
    profile = resp.json().get("data") if resp.json().get("code") == 0 else None
    return token, user, profile


# ==================== US04 商家注册 ====================

def test_merchant_register():
    """US04: 商家注册 —— 正常注册并验证审核状态"""
    email = f"us04_{_ts}@example.com"
    resp = post("/auth/register/merchant", {
        "email": email,
        "password": "Merchant123",
        "nickname": f"US04_{_ts}",
        "merchant_name": f"US04测试商家",
        "activity_domains": ["美食", "社交"],
        "license_image_url": "https://example.com/lic.jpg"
    })
    body = assert_pass("US04-1 商家注册成功", resp, 0, data_keys=["email", "audit_status", "activation_token"])
    if body and body.get("data"):
        assert body["data"]["audit_status"] == "pending", "audit_status should be pending"

    # 重复邮箱
    resp = post("/auth/register/merchant", {
        "email": email,
        "password": "Merchant123",
        "nickname": "AnotherNick_" + _ts,
        "merchant_name": "另一个商家"
    })
    assert_pass("US04-2 重复邮箱拒绝(40001)", resp, 40001)


def test_merchant_register_validation():
    """US04: 商家注册 —— 字段校验"""
    # 弱密码
    resp = post("/auth/register/merchant", {
        "email": f"badpw_{_ts}@x.com",
        "password": "123",
        "nickname": f"BadPw_{_ts}",
        "merchant_name": "弱密码商家"
    })
    code = resp.json().get("code")
    assert code != 0, f"weak password should fail, got code={code}"

    # 缺字段
    resp = post("/auth/register/merchant", {
        "email": f"nomerchant_{_ts}@x.com",
        "password": "Test12345",
        "nickname": f"NoName_{_ts}"
    })
    code = resp.json().get("code")
    assert code != 0, f"missing merchant_name should fail, got code={code}"


# ==================== US05 商家资料管理 ====================

def test_merchant_profile():
    """US05: 查看和更新商家资料"""
    token, user, profile = _register_merchant("profile")
    if not token:
        print("  !! skip US05 - registration failed")
        return

    # 查看资料
    resp = get("/merchants/me", headers=auth_header(token))
    body = assert_pass("US05-1 查看商家资料", resp, 0, data_keys=["id", "merchant_name", "audit_status"])
    profile_id = body["data"]["id"]

    # 更新资料
    resp = patch("/merchants/me", {
        "merchant_name": "更新后的商家名",
        "activity_domains": ["读书", "旅行", "摄影"]
    }, headers=auth_header(token))
    body = assert_pass("US05-2 更新商家资料", resp, 0)
    if body and body.get("data"):
        assert body["data"]["merchant_name"] == "更新后的商家名", "merchant_name should be updated"
        domains = body["data"]["activity_domains"]
        assert "读书" in str(domains), f"activity_domains should contain 读书, got {domains}"

    # 更新昵称
    resp = patch("/merchants/me", {
        "merchant_nickname": f"新昵称_{_ts}"
    }, headers=auth_header(token))
    body = assert_pass("US05-3 更新商家昵称", resp, 0)
    if body and body.get("data"):
        assert body["data"]["merchant_nickname"] == f"新昵称_{_ts}", "nickname should be updated"

    # 昵称唯一性 — 尝试改成已被其他商家占用的昵称
    token2, _, _ = _register_merchant("profile2")
    resp = patch("/merchants/me", {
        "merchant_nickname": f"新昵称_{_ts}"
    }, headers=auth_header(token2))
    assert_pass("US05-4 昵称唯一性校验(40003)", resp, 40003)

    # 未认证访问
    resp = get("/merchants/me")
    assert_pass("US05-5 未认证访问(40100)", resp, 40100)


# ==================== US06 商家审核 ====================

def test_merchant_audit():
    """US06: 管理员审核商家 — 通过 / 驳回 / 待审核列表"""
    # 注册两个商家
    token1, user1, profile1 = _register_merchant("audit_a")
    token2, user2, profile2 = _register_merchant("audit_b")
    if not token1 or not token2:
        print("  !! skip US06 - registration failed")
        return

    merchant_id_a = profile1["id"]
    merchant_id_b = profile2["id"]

    # 管理员查看待审核列表
    login_as_admin()
    resp = get("/admin/merchants/pending", headers=auth_header())
    body = assert_pass("US06-1 管理员查看待审核列表", resp, 0)
    if body and body.get("data"):
        data = body["data"]
        assert len(data) >= 2, f"expected >= 2 pending merchants, got {len(data)}"
        print(f"  pending count: {len(data)}")

    # 审核通过第一个
    resp = post(f"/admin/merchants/{merchant_id_a}/approve", headers=auth_header())
    assert_pass("US06-2 审核通过", resp, 0)

    # 驳回第二个
    resp = post(f"/admin/merchants/{merchant_id_b}/reject",
                {"reason": "资料不完整，请补充营业执照"}, headers=auth_header())
    assert_pass("US06-3 审核驳回", resp, 0)

    # 驳回无原因应失败
    token3, _, profile3 = _register_merchant("audit_c")
    if token3 and profile3:
        resp = post(f"/admin/merchants/{profile3['id']}/reject",
                    {}, headers=auth_header())
        assert_pass("US06-4 驳回缺少原因(40019)", resp, 40019)

    # 重复审核应失败
    resp = post(f"/admin/merchants/{merchant_id_a}/approve", headers=auth_header())
    assert_pass("US06-5 重复审核拒绝(40018)", resp, 40018)


# ==================== 公开商家列表 ====================

def test_merchant_list():
    """公开商家列表 — 只显示通过审核的商家"""
    # 注册并审核通过2个商家
    token1, _, profile1 = _register_merchant("list_a", ["户外", "徒步"])
    token2, _, profile2 = _register_merchant("list_b", ["桌游", "社交"])
    # 再注册1个不审核的
    _register_merchant("list_pending", ["美食"])

    if not profile1 or not profile2:
        print("  !! skip merchant list test - registration failed")
        return

    # 管理员通过前两个
    login_as_admin()
    post(f"/admin/merchants/{profile1['id']}/approve", headers=auth_header())
    post(f"/admin/merchants/{profile2['id']}/approve", headers=auth_header())

    # 公开访问（无需 token）
    resp = get("/merchants")
    body = assert_pass("LIST-1 公开商家列表", resp, 0)
    if body and body.get("data"):
        data = body["data"]
        # 只应返回审核通过的2个
        names = [m["merchant_name"] for m in data]
        print(f"  merchant names: {names}")
        assert len(data) >= 2, f"expected >= 2 approved merchants, got {len(data)}"

    # 关键词搜索
    resp = get("/merchants", params={"q": "list_a"})
    body = assert_pass("LIST-2 关键词搜索", resp, 0)
    if body and body.get("data"):
        data = body["data"]
        assert len(data) >= 1, "should find at least 1 by keyword"

    # 领域筛选
    resp = get("/merchants", params={"domain": "桌游"})
    body = assert_pass("LIST-3 领域筛选", resp, 0)
    if body and body.get("data"):
        data = body["data"]
        for m in data:
            domains = str(m.get("activity_domains", ""))
            assert "桌游" in domains or "社交" in domains, f"unexpected domains: {domains}"

    # 分页
    resp = get("/merchants", params={"page": 1, "size": 1})
    body = assert_pass("LIST-4 分页查询", resp, 0)
    if body and body.get("data"):
        assert len(body["data"]) == 1, "page size 1 should return 1 item"
        pagination = body.get("pagination", {})
        assert pagination.get("page") == 1, f"pagination.page should be 1, got {pagination}"


if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Merchant Module (US04/US05/US06)")
    print("=" * 60)
    test_merchant_register()
    test_merchant_register_validation()
    test_merchant_profile()
    test_merchant_audit()
    test_merchant_list()
    tu.print_summary()
