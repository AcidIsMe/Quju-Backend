"""
趣聚 API 全面测试运行入口
运行所有模块的接口测试
"""

import subprocess
import sys
import os

os.chdir(os.path.dirname(os.path.abspath(__file__)))

modules = [
    ("auth",        "auth/test_auth.py"),
    ("users",       "users/test_users.py"),
    ("activities",  "activities/test_activities.py"),
    ("registrations", "registrations/test_registrations.py"),
    ("discover",    "discover/test_discover.py"),
    ("admin",       "admin/test_admin.py"),
]

total_pass = 0
total_fail = 0

for name, script in modules:
    print(f"\n{'#'*60}")
    print(f"#  Module: {name}")
    print(f"{'#'*60}")
    result = subprocess.run([sys.executable, script], capture_output=False)

print(f"\n{'='*60}")
print(f"  All module tests completed")
print(f"{'='*60}")
