#!/usr/bin/env python3
"""
端到端功能验证器 (Harness Engineering)

在 build + lint + test 都通过后，验证功能层面的正确性。
这是"代码能跑"到"功能正确"的最后一道保险。

当前为占位骨架 — 请根据项目实际 API 接口填充验证逻辑。

典型验证项:
  - CURL 请求后端 /admin/user/page 检查返回 JSON 结构
  - 检查 Swagger 文档可访问
  - 检查健康检查端点

用法:
    python3 scripts/verify/run.py
"""

import json
import sys
import urllib.request
import urllib.error

BASE_URL = "http://localhost:9999/boot"

CHECKS = [
    {
        "name": "Swagger Doc accessible",
        "url": f"{BASE_URL}/doc.html",
        "expected_status": 200,
        "check_body": False,
    },
    # 更多检查项请根据实际 API 添加:
    # {
    #     "name": "Admin user list API returns JSON",
    #     "url": f"{BASE_URL}/admin/user/user-info/page?current=1&size=1",
    #     "expected_status": 200,
    #     "check_body": True,
    #     "expected_json_key": "code",
    # },
]

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
RESET = "\033[0m"
BOLD = "\033[1m"


def run_check(check):
    """执行单个检查项。"""
    url = check["url"]
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            body = resp.read().decode("utf-8", errors="ignore")[:500]

            if status != check.get("expected_status", 200):
                return False, f"Expected status {check['expected_status']}, got {status}"

            if check.get("check_body"):
                data = json.loads(body)
                expected_key = check.get("expected_json_key")
                if expected_key and expected_key not in data:
                    return False, f"Response missing key '{expected_key}': {body[:200]}"

            return True, f"Status {status} ✓"
    except urllib.error.URLError as e:
        return False, f"Connection error: {e.reason}"
    except json.JSONDecodeError:
        return False, f"Invalid JSON response"
    except Exception as e:
        return False, f"Error: {str(e)[:100]}"


def main():
    print(f"{BOLD}=== Aryn Mall — E2E Functional Verify ==={RESET}\n")
    print(f"  {YELLOW}⚠ Note: Backend must be running at {BASE_URL}{RESET}\n")

    passed = 0
    failed = 0

    for check in CHECKS:
        name = check["name"]
        success, detail = run_check(check)

        marker = f"{GREEN}✓{RESET}" if success else f"{RED}✗{RESET}"
        print(f"  {marker} {name}")
        print(f"       {detail}")

        if success:
            passed += 1
        else:
            failed += 1

    print(f"\n{BOLD}--- Verify Summary ---{RESET}")
    print(f"  Passed: {GREEN}{passed}{RESET}")
    print(f"  Failed: {RED}{failed}{RESET}")

    if failed > 0:
        print(f"\n{RED}{BOLD}✗ E2E verify failed — {failed} check(s) did not pass.{RESET}")
        sys.exit(1)
    else:
        print(f"\n{GREEN}{BOLD}✓ E2E verify passed — all checks OK.{RESET}")


if __name__ == "__main__":
    main()
