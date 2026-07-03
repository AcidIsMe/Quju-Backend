"""
趣聚 API 测试 — 小队模块 (Teams)
覆盖: 创建 / 列表 / 详情 / 更新 / 加入 / 审批 / 退出 / 角色管理 /
      转让队长 / 黑名单 / 积分榜 / 解散 / 我的小队 / Admin管理

需要服务端运行在 localhost:3000/api
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'tests'))

import requests
import json
import time

# ── 复制 test_utils 核心逻辑，避免 import * 引起的变量遮蔽 ──

BASE_URL = "http://localhost:3002/api"
PASS = 0
FAIL = 0
_current_token = None
_current_user = None
_last_resp = None


def _post(path, payload=None, headers=None):
    global _last_resp
    _last_resp = requests.post(f"{BASE_URL}{path}", json=payload, headers=headers, timeout=10)
    return _last_resp


def _get(path, params=None, headers=None):
    global _last_resp
    _last_resp = requests.get(f"{BASE_URL}{path}", params=params, headers=headers, timeout=10)
    return _last_resp


def _put(path, payload=None, headers=None):
    global _last_resp
    _last_resp = requests.put(f"{BASE_URL}{path}", json=payload, headers=headers, timeout=10)
    return _last_resp


def _patch(path, payload=None, headers=None):
    global _last_resp
    _last_resp = requests.patch(f"{BASE_URL}{path}", json=payload, headers=headers, timeout=10)
    return _last_resp


def _delete(path, headers=None):
    global _last_resp
    _last_resp = requests.delete(f"{BASE_URL}{path}", headers=headers, timeout=10)
    return _last_resp


def auth_header(token=None):
    t = token or _current_token
    return {"Authorization": f"Bearer {t}"} if t else {}


def get_token():
    return _current_token


def get_user():
    return _current_user


def login_as_new_user(prefix="testuser"):
    global _current_token, _current_user
    ts = str(int(time.time() * 1000))[-8:]
    email = f"{prefix}_{ts}@example.com"
    nickname = f"{prefix}_{ts}"
    password = "Test1234"

    resp = _post("/auth/register/personal", {
        "email": email, "password": password, "nickname": nickname
    })
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! login_as_new_user: register failed - {body}")
        return None, None

    activation_token = body["data"].get("activation_token")
    if activation_token:
        resp = _get(f"/auth/activate/{activation_token}")
        if resp.json().get("code") != 0:
            print(f"  !! login_as_new_user: activate failed - {resp.json()}")
            return None, None

    resp = _post("/auth/login", {"email": email, "password": password})
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! login_as_new_user: login failed - {body}")
        return None, None

    _current_token = body["data"]["access_token"]
    _current_user = body["data"]["user"]
    print(f"  [auth] logged in as {_current_user['nickname']} (id={_current_user['id'][:8]}...)")
    return _current_token, _current_user


def login_as_admin():
    global _current_token, _current_user
    resp = _post("/auth/login", {"email": "admin@quju.com", "password": "Admin12345"})
    body = resp.json()

    if body.get("code") == 0:
        _current_token = body["data"]["access_token"]
        _current_user = body["data"]["user"]
        print(f"  [auth] admin logged in (role={_current_user['role']})")
        return _current_token, _current_user

    code = body.get("code")
    # 未激活，尝试重新注册
    if code == 40102:
        resp = _post("/auth/register/personal", {
            "email": "admin@quju.com", "password": "Admin12345", "nickname": "AdminMaster"
        })
        body = resp.json()
        activation_token = body.get("data", {}).get("activation_token")
        if activation_token:
            _get(f"/auth/activate/{activation_token}")

    resp = _post("/auth/login", {"email": "admin@quju.com", "password": "Admin12345"})
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! login_as_admin: login failed - {body}")
        return None, None

    _current_token = body["data"]["access_token"]
    _current_user = body["data"]["user"]
    print(f"  [auth] admin logged in (role={_current_user['role']})")
    return _current_token, _current_user


def assert_pass(test_name, resp, expected_code, msg_contains=None, data_keys=None):
    global PASS, FAIL
    try:
        body = resp.json()
        code = body.get("code", -1)
        msg = body.get("message", "")
        data = body.get("data")

        print(f"\n{'─'*50}")
        print(f"[{test_name}]")
        print(f"  code={code}, msg={msg}")
        if data is not None:
            s = json.dumps(data, ensure_ascii=False)
            print(f"  data: {s[:300]}{'...' if len(s)>300 else ''}")

        ok = True
        if code != expected_code:
            print(f"  >> FAIL: expected code={expected_code}, got code={code}, msg={msg}")
            ok = False
        if msg_contains and msg_contains not in str(msg):
            print(f"  >> FAIL: msg does not contain '{msg_contains}'")
            ok = False
        if data_keys and isinstance(data, dict):
            for key in data_keys:
                if key not in data:
                    print(f"  >> FAIL: data missing key '{key}'")
                    ok = False

        if ok:
            print(f"  >> PASS")
            PASS += 1
        else:
            FAIL += 1
        return body
    except Exception as e:
        print(f"\n[{test_name}]")
        print(f"  >> FAIL: {e}")
        FAIL += 1
        return None


def print_summary():
    total = PASS + FAIL
    print(f"\n{'='*60}")
    print(f"  Result: {PASS} passed, {FAIL} failed, {total} total")
    print(f"{'='*60}")
    return FAIL == 0


# ── 辅助函数 ──

_ts = str(int(time.time() * 1000))[-6:]


def _create_team(token, name_suffix="", join_type="public", max_members=20):
    """创建小队，返回 team data dict"""
    resp = _post("/teams", {
        "name": f"测队{name_suffix}_{_ts}",
        "description": "自动化测试小队",
        "interest_tags": ["测试", "自动化"],
        "join_type": join_type,
        "max_members": max_members,
        "avatar_url": ""
    }, headers=auth_header(token))
    body = resp.json()
    if body.get("code") != 0:
        print(f"  !! _create_team failed: {body}")
        return None
    return body["data"]


def _extract_id(data):
    """从 assert_pass 返回的 body 中提取 data.id"""
    if data and data.get("data"):
        return data["data"].get("id")
    return None


# ── 用户端测试 ──


def test_create_team():
    """创建小队"""
    token, user = login_as_new_user("crLeader")
    team = _create_team(token)
    body = assert_pass("01-创建 正常创建小队", _last_resp, 0, data_keys=["id", "name", "status"])
    assert team is not None and team.get("status") == "active", "status 应为 active"
    return team, token, user


def test_list_teams():
    """小队列表与搜索"""
    token, _ = login_as_new_user("liUser")
    _create_team(token, "搜索A")

    _get("/teams", headers=auth_header(token))
    assert_pass("02-列表 获取发现列表", _last_resp, 0)

    _get("/teams", params={"q": f"搜索A_{_ts}"}, headers=auth_header(token))
    assert_pass("02-列表 按名称搜索", _last_resp, 0)

    _get("/teams", params={"q": "xyz不存在的队伍999"}, headers=auth_header(token))
    assert_pass("02-列表 搜索不存在(返回空)", _last_resp, 0)


def test_get_detail():
    """小队详情"""
    token, _ = login_as_new_user("dtUser")
    team = _create_team(token, "详情")

    _get(f"/teams/{team['id']}", headers=auth_header(token))
    body = assert_pass("03-详情 查看小队详情", _last_resp, 0, data_keys=["id", "name", "leader_id"])
    if body and body.get("data"):
        assert body["data"]["name"] == team["name"], "名称不匹配"

    _get("/teams/fake-team-id-00000", headers=auth_header(token))
    assert_pass("03-详情 不存在小队", _last_resp, 40404)

    return team, token


def test_update_team():
    """修改小队信息"""
    token, _ = login_as_new_user("upLeader")
    team = _create_team(token, "更新")

    _put(f"/teams/{team['id']}", {
        "name": f"已更新_{_ts}",
        "description": "更新后的描述",
        "interest_tags": ["户外"],
        "join_type": "review",
        "max_members": 30
    }, headers=auth_header(token))
    body = assert_pass("04-更新 队长修改信息", _last_resp, 0)
    if body and body.get("data"):
        assert body["data"]["name"] == f"已更新_{_ts}", "名称未更新"

    # 非队长修改
    token2, _ = login_as_new_user("upOther")
    _put(f"/teams/{team['id']}", {"name": "恶意"}, headers=auth_header(token2))
    assert_pass("04-更新 非队长修改", _last_resp, 40300)

    return team, token


def test_join_public():
    """加入公开小队"""
    token_a, _ = login_as_new_user("jpLeader")
    team = _create_team(token_a, "公开", "public")

    token_b, user_b = login_as_new_user("jpJoiner")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    body = assert_pass("05-加入 直接加入公开小队", _last_resp, 0)
    if body:
        assert body.get("data", {}).get("status") == "joined", "status 应为 joined"

    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    assert_pass("05-加入 重复加入", _last_resp, 40901)

    return team, token_a, token_b, user_b


def test_join_review():
    """加入审核小队 + 重复申请检测"""
    token_a, _ = login_as_new_user("jrLeader")
    team = _create_team(token_a, "审核", "review")

    token_b, user_b = login_as_new_user("jrJoiner")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    body = assert_pass("06-加入 提交审核申请", _last_resp, 0)
    if body:
        assert body.get("data", {}).get("status") == "pending", "status 应为 pending"

    # 重复申请
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    assert_pass("06-加入 重复申请", _last_resp, 40903)

    return team, token_a, token_b, user_b


def test_join_requests_list():
    """查看入队申请"""
    token_a, _ = login_as_new_user("rqLeader")
    team = _create_team(token_a, "申请", "review")

    token_b, _ = login_as_new_user("rqJoiner")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    _get(f"/teams/{team['id']}/join-requests", headers=auth_header(token_a))
    assert_pass("07-申请 队长查看申请列表", _last_resp, 0)

    token_c, _ = login_as_new_user("rqStranger")
    _get(f"/teams/{team['id']}/join-requests", headers=auth_header(token_c))
    assert_pass("07-申请 非队长查看", _last_resp, 40300)

    return team, token_a, token_b


def test_approve_request():
    """通过入队申请"""
    token_a, user_a = login_as_new_user("apLeader")
    team = _create_team(token_a, "审批", "review")

    token_b, user_b = login_as_new_user("apJoiner")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    # 队长获取申请列表
    _get(f"/teams/{team['id']}/join-requests", headers=auth_header(token_a))
    requests = _last_resp.json().get("data", [])
    assert len(requests) > 0, "没有待审批申请"
    req_id = requests[0]["id"]

    _post(f"/teams/{team['id']}/join-requests/{req_id}/approve", headers=auth_header(token_a))
    assert_pass("08-审批 通过入队申请", _last_resp, 0)

    # 验证成员已加入
    _get(f"/teams/{team['id']}/members", headers=auth_header(token_a))
    members = _last_resp.json().get("data", [])
    joined = any(m.get("userId") == user_b["id"] for m in members)
    assert joined, f"user_b({user_b['id'][:8]}) 未出现在成员列表中"

    return team, token_a, token_b, user_b


def test_reject_request():
    """拒绝入队申请"""
    token_a, _ = login_as_new_user("rjLeader")
    team = _create_team(token_a, "拒绝", "review")

    token_b, _ = login_as_new_user("rjJoiner")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    _get(f"/teams/{team['id']}/join-requests", headers=auth_header(token_a))
    req_id = _last_resp.json()["data"][0]["id"]

    _post(f"/teams/{team['id']}/join-requests/{req_id}/reject", headers=auth_header(token_a))
    assert_pass("09-审批 拒绝入队申请", _last_resp, 0)


def test_members_list():
    """成员列表"""
    token_a, _ = login_as_new_user("mbLeader")
    team = _create_team(token_a, "成员")

    _get(f"/teams/{team['id']}/members", headers=auth_header(token_a))
    body = assert_pass("10-成员 查看成员列表", _last_resp, 0)
    if body and body.get("data"):
        assert len(body["data"]) >= 1, "至少包含队长"

    return team, token_a


def test_change_role():
    """修改成员角色"""
    token_a, user_a = login_as_new_user("rlLeader")
    team = _create_team(token_a, "角色", "public")

    token_b, user_b = login_as_new_user("rlMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    # 提升为管理员
    _patch(f"/teams/{team['id']}/members/{user_b['id']}/role",
           {"role": "admin"}, headers=auth_header(token_a))
    assert_pass("11-角色 提升为管理员", _last_resp, 0)

    # 降级为成员
    _patch(f"/teams/{team['id']}/members/{user_b['id']}/role",
           {"role": "member"}, headers=auth_header(token_a))
    assert_pass("11-角色 降级为成员", _last_resp, 0)

    # 非队长操作
    _patch(f"/teams/{team['id']}/members/{user_b['id']}/role",
           {"role": "admin"}, headers=auth_header(token_b))
    assert_pass("11-角色 非队长修改", _last_resp, 40300)

    # 无效角色
    _patch(f"/teams/{team['id']}/members/{user_b['id']}/role",
           {"role": "superadmin"}, headers=auth_header(token_a))
    assert_pass("11-角色 无效角色值", _last_resp, 40000)

    return team, token_a, token_b, user_b


def test_remove_member():
    """移除成员"""
    token_a, _ = login_as_new_user("rmLeader")
    team = _create_team(token_a, "移除", "public")

    token_b, user_b = login_as_new_user("rmMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    _delete(f"/teams/{team['id']}/members/{user_b['id']}", headers=auth_header(token_a))
    assert_pass("12-移除 队长移除成员", _last_resp, 0)

    # 非队长操作
    token_c, user_c = login_as_new_user("rmOther")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_c))
    _delete(f"/teams/{team['id']}/members/{user_c['id']}", headers=auth_header(token_c))
    assert_pass("12-移除 非队长移除", _last_resp, 40300)

    return team, token_a


def test_leave_team():
    """退出小队"""
    token_a, _ = login_as_new_user("lvLeader")
    team = _create_team(token_a, "退出", "public")

    token_b, _ = login_as_new_user("lvMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    _post(f"/teams/{team['id']}/leave", headers=auth_header(token_b))
    assert_pass("13-退出 成员正常退出", _last_resp, 0)

    _post(f"/teams/{team['id']}/leave", headers=auth_header(token_a))
    assert_pass("13-退出 队长退出被拒", _last_resp, 40300)

    return team, token_a


def test_transfer_leader():
    """转让队长"""
    token_a, user_a = login_as_new_user("tfLeader")
    team = _create_team(token_a, "转让", "public")

    token_b, user_b = login_as_new_user("tfNewLdr")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    # 队长转让给 B
    _post(f"/teams/{team['id']}/transfer-leader",
          {"new_leader_id": user_b["id"]}, headers=auth_header(token_a))
    assert_pass("14-转让 队长转让成功", _last_resp, 0)

    # 验证 A 降级为 admin（用 B 的 token 查看）
    _get(f"/teams/{team['id']}/members", headers=auth_header(token_b))
    members = _last_resp.json().get("data", [])
    old = next((m for m in members if m.get("userId") == user_a["id"]), None)
    if old:
        assert old.get("role") == "admin", f"原队长应降级, 实际: {old.get('role')}"
        print(f"  [验证] 原队长 role={old.get('role')} ✓")

    # 原队长(A)再尝试转让
    _post(f"/teams/{team['id']}/transfer-leader",
          {"new_leader_id": user_a["id"]}, headers=auth_header(token_a))
    assert_pass("14-转让 非队长转让", _last_resp, 40300)

    return team, token_b, user_a, user_b


def test_leaderboard():
    """积分排行榜"""
    token_a, _ = login_as_new_user("lbLeader")
    team = _create_team(token_a, "积分", "public")

    token_b, user_b = login_as_new_user("lbMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    _post(f"/teams/{team['id']}/points",
          {"user_id": user_b["id"], "points": 50}, headers=auth_header(token_a))

    _get(f"/teams/{team['id']}/leaderboard", headers=auth_header(token_a))
    assert_pass("15-积分 查看排行榜", _last_resp, 0)

    return team, token_a, token_b, user_b


def test_add_points():
    """加减积分"""
    token_a, _ = login_as_new_user("ptLeader")
    team = _create_team(token_a, "加分", "public")

    token_b, user_b = login_as_new_user("ptMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    _post(f"/teams/{team['id']}/points",
          {"user_id": user_b["id"], "points": 10}, headers=auth_header(token_a))
    assert_pass("16-积分 给成员加分", _last_resp, 0)

    _post(f"/teams/{team['id']}/points",
          {"user_id": "fake-user-99999", "points": 5}, headers=auth_header(token_a))
    assert_pass("16-积分 给非成员加分", _last_resp, 40404)

    return team, token_a, token_b, user_b


def test_blacklist():
    """黑名单管理"""
    token_a, _ = login_as_new_user("blLeader")
    team = _create_team(token_a, "黑名单", "public")

    token_b, user_b = login_as_new_user("blMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    # 拉黑（自动移出）
    _post(f"/teams/{team['id']}/blacklist",
          {"user_id": user_b["id"]}, headers=auth_header(token_a))
    assert_pass("17-黑名单 拉黑成员", _last_resp, 0)

    # 被拉黑后尝试加入
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    assert_pass("17-黑名单 被拉黑加入", _last_resp, 40301)

    # 重复拉黑
    _post(f"/teams/{team['id']}/blacklist",
          {"user_id": user_b["id"]}, headers=auth_header(token_a))
    assert_pass("17-黑名单 重复拉黑", _last_resp, 40901)

    # 解除
    _delete(f"/teams/{team['id']}/blacklist/{user_b['id']}", headers=auth_header(token_a))
    assert_pass("17-黑名单 解除拉黑", _last_resp, 0)

    # 重复解除
    _delete(f"/teams/{team['id']}/blacklist/{user_b['id']}", headers=auth_header(token_a))
    assert_pass("17-黑名单 重复解除", _last_resp, 40404)

    # 非管理员操作
    token_c, user_c = login_as_new_user("blStranger")
    _post(f"/teams/{team['id']}/blacklist",
          {"user_id": user_c["id"]}, headers=auth_header(token_c))
    assert_pass("17-黑名单 非管理员拉黑", _last_resp, 40300)

    # 拉黑队长
    _post(f"/teams/{team['id']}/blacklist",
          {"user_id": get_user()["id"]}, headers=auth_header(token_a))
    assert_pass("17-黑名单 拉黑队长", _last_resp, 40000)


def test_dissolve():
    """解散小队"""
    token_a, _ = login_as_new_user("dsLeader")
    team = _create_team(token_a, "解散")

    token_b, _ = login_as_new_user("dsMember")
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))

    # 非队长解散
    _post(f"/teams/{team['id']}/dissolve", headers=auth_header(token_b))
    assert_pass("18-解散 非队长解散", _last_resp, 40300)

    # 队长解散
    _post(f"/teams/{team['id']}/dissolve", headers=auth_header(token_a))
    assert_pass("18-解散 队长解散", _last_resp, 0)

    # 解散后加入
    _post(f"/teams/{team['id']}/join", headers=auth_header(token_b))
    assert_pass("18-解散 解散后加入", _last_resp, 40001)


def test_my_teams():
    """我的小队列表"""
    token_a, _ = login_as_new_user("myLeader")
    _create_team(token_a, "我建的")

    token_b, _ = login_as_new_user("myOther")
    team_b = _create_team(token_b, "他的", "public")
    # A 加入 B 的小队
    _post(f"/teams/{team_b['id']}/join", headers=auth_header(token_a))

    _get("/users/me/teams", headers=auth_header(token_a))
    body = assert_pass("19-我的 我的小队列表", _last_resp, 0)
    if body and body.get("data"):
        teams = body["data"]
        assert len(teams) >= 2, f"应有至少2个小队, 实际 {len(teams)}"
        for t in teams:
            assert "my_role" in t, "缺少 my_role"


# ── 管理端测试 ──


def test_admin_list_teams():
    """管理端小队列表（含搜索、筛选）"""
    token_u, _ = login_as_new_user("adList")
    _create_team(token_u, "管理", "public")

    login_as_admin()

    _get("/admin/teams", headers=auth_header())
    assert_pass("A1-列表 查看全部小队", _last_resp, 0)

    _get("/admin/teams", params={"status": "active"}, headers=auth_header())
    assert_pass("A1-列表 按状态筛选", _last_resp, 0)

    _get("/admin/teams", params={"q": f"管理_{_ts}"}, headers=auth_header())
    assert_pass("A1-列表 按名称搜索", _last_resp, 0)


def test_admin_team_detail():
    """管理端小队详情"""
    token_u, _ = login_as_new_user("adDetail")
    team = _create_team(token_u, "管详")

    login_as_admin()
    _get(f"/admin/teams/{team['id']}", headers=auth_header())
    body = assert_pass("A2-详情 查看详情(含成员)", _last_resp, 0)
    if body and body.get("data"):
        assert "members" in body["data"], "应包含 members"
        assert "activity_count" in body["data"], "应包含 activity_count"

    _get("/admin/teams/fake-admin-999", headers=auth_header())
    assert_pass("A2-详情 不存在小队", _last_resp, 40404)


def test_admin_team_members():
    """管理端成员列表"""
    token_u, _ = login_as_new_user("adMem")
    team = _create_team(token_u, "管成")

    login_as_admin()
    _get(f"/admin/teams/{team['id']}/members", headers=auth_header())
    body = assert_pass("A3-成员 查看成员列表", _last_resp, 0)
    if body and body.get("data"):
        assert len(body["data"]) >= 1, "应至少包含队长"


def test_admin_disable_restore():
    """停用与恢复小队"""
    token_u, _ = login_as_new_user("adDR")
    team = _create_team(token_u, "停恢")

    login_as_admin()

    # 不带 reason 停用 → 应拦截
    _post(f"/admin/teams/{team['id']}/disable", headers=auth_header())
    code = _last_resp.json().get("code")
    if code == 40020:
        print(f"\n  [info] 无 reason 停用被正确拒绝 (code=40020)")
    # 带 reason 停用
    _post(f"/admin/teams/{team['id']}/disable",
          {"reason": "测试停用"}, headers=auth_header())
    assert_pass("A4-停用 停用小队", _last_resp, 0)

    # 恢复
    _post(f"/admin/teams/{team['id']}/restore", headers=auth_header())
    assert_pass("A5-恢复 恢复小队", _last_resp, 0)

    # 再次恢复（幂等）
    _post(f"/admin/teams/{team['id']}/restore", headers=auth_header())
    assert_pass("A5-恢复 再次恢复", _last_resp, 0)


# ── 主入口 ──

if __name__ == "__main__":
    print("=" * 60)
    print("    QuJu API Test — Teams Module (小队管理)")
    print("=" * 60)
    print(f"  BASE_URL: {BASE_URL}")
    print(f"  timestamp: {_ts}")
    print()

    # ─── 用户端 ───
    print("▼" * 30)
    print("  一、用户端 API 测试 (19项)")
    print("▼" * 30)

    test_create_team()
    test_list_teams()
    test_get_detail()
    test_update_team()
    test_join_public()
    test_join_review()
    test_join_requests_list()
    test_approve_request()
    test_reject_request()
    test_members_list()
    test_change_role()
    test_remove_member()
    test_leave_team()
    test_transfer_leader()
    test_leaderboard()
    test_add_points()
    test_blacklist()
    test_dissolve()
    test_my_teams()

    # ─── 管理端 ───
    print("\n" + "▼" * 30)
    print("  二、管理端 API 测试 (5项)")
    print("▼" * 30)

    test_admin_list_teams()
    test_admin_team_detail()
    test_admin_team_members()
    test_admin_disable_restore()

    # ─── 汇总 ───
    print()
    print_summary()
