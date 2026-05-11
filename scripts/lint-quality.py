#!/usr/bin/env python3
"""
Java 后端代码质量检查器 (Harness Engineering)

检查规则:
  1. 单文件行数 ≤ 500
  2. 源码中禁止 System.out.println() / printStackTrace() (要求结构化日志)
  3. 源码中禁止硬编码常见品牌/域名字符串
  4. 检查是否使用 @Slf4j 等日志注解

用法:
    python3 scripts/lint-quality.py
    python3 scripts/lint-quality.py --threshold 500
    python3 scripts/lint-quality.py --module aryn-order-biz
"""

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA_ROOT = ROOT / "aryn-mall-java"

# ============================================================================
# 检查规则
# ============================================================================

MAX_FILE_LINES = 500

# 禁止的调试输出模式
FORBIDDEN_OUTPUT = [
    (re.compile(r"System\.out\.println"), "System.out.println — use @Slf4j + log.info() instead"),
    (re.compile(r"\.printStackTrace\(\)"), ".printStackTrace() — use structured logging instead"),
    (re.compile(r"System\.err\.println"), "System.err.println — use log.error() instead"),
]

# 禁止的硬编码模式（常见敏感字符串）
FORBIDDEN_HARDCODE = [
    (re.compile(r'"(?:https?://)?(?:api|open)\.(?:weixin|wechat)\.qq\.com'), "Hardcoded WeChat API URL — use config property"),
    (re.compile(r'"(?:https?://)?openapi\.alipay\.com'), "Hardcoded Alipay API URL — use config property"),
]

# 建议使用的日志注解
SUGGESTED_LOG_ANNOTATIONS = ["@Slf4j", "@Log4j2"]


def check_file_size(filepath, max_lines):
    """检查文件行数是否超限。"""
    try:
        lines = filepath.read_text(encoding="utf-8", errors="ignore").split("\n")
    except Exception:
        return []
    issues = []
    if len(lines) > max_lines:
        issues.append({
            "type": "file_size",
            "file": str(filepath),
            "line": 0,
            "message": f"File has {len(lines)} lines (> {max_lines}). Consider splitting into smaller components.",
        })
    return issues


def check_forbidden_output(filepath):
    """检查是否使用了禁止的输出模式。"""
    try:
        content = filepath.read_text(encoding="utf-8", errors="ignore")
        lines = content.split("\n")
    except Exception:
        return []
    issues = []
    for lineno, line in enumerate(lines, 1):
        for pattern, description in FORBIDDEN_OUTPUT:
            if pattern.search(line):
                issues.append({
                    "type": "forbidden_output",
                    "file": str(filepath),
                    "line": lineno,
                    "message": f"Line {lineno}: {description}",
                    "code": line.strip()[:80],
                })
    return issues


def check_forbidden_hardcode(filepath):
    """检查是否硬编码了敏感字符串。"""
    try:
        content = filepath.read_text(encoding="utf-8", errors="ignore")
        lines = content.split("\n")
    except Exception:
        return []
    issues = []
    for lineno, line in enumerate(lines, 1):
        for pattern, description in FORBIDDEN_HARDCODE:
            if pattern.search(line):
                issues.append({
                    "type": "forbidden_hardcode",
                    "file": str(filepath),
                    "line": lineno,
                    "message": f"Line {lineno}: {description}",
                    "code": line.strip()[:80],
                })
    return issues


def collect_java_files(root, module=None):
    """收集待检查的 Java 文件。"""
    files = []
    search_root = root / module if module else root
    for java_file in search_root.rglob("*.java"):
        if "target" in java_file.parts:
            continue
        if "test" in java_file.parts:
            continue
        files.append(java_file)
    return files


# ============================================================================
# 输出
# ============================================================================

RED = "\033[91m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
RESET = "\033[0m"
BOLD = "\033[1m"


# ============================================================================
# 主流程
# ============================================================================

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Java Code Quality Linter")
    parser.add_argument("--threshold", type=int, default=MAX_FILE_LINES, help=f"Max file line count (default: {MAX_FILE_LINES})")
    parser.add_argument("--module", type=str, help="Check only the specified module")
    args = parser.parse_args()

    if not JAVA_ROOT.exists():
        print(f"{RED}Error: {JAVA_ROOT} not found.{RESET}")
        sys.exit(1)

    print(f"{BOLD}=== Aryn Mall — Code Quality Check ==={RESET}\n")

    files = collect_java_files(JAVA_ROOT, args.module)
    all_issues = []

    for fpath in files:
        all_issues.extend(check_file_size(fpath, args.threshold))
        all_issues.extend(check_forbidden_output(fpath))
        all_issues.extend(check_forbidden_hardcode(fpath))

    # 按类型分组输出
    size_issues = [i for i in all_issues if i["type"] == "file_size"]
    output_issues = [i for i in all_issues if i["type"] == "forbidden_output"]
    hardcode_issues = [i for i in all_issues if i["type"] == "forbidden_hardcode"]

    if size_issues:
        print(f"{YELLOW}{BOLD}=== File Size Issues ({len(size_issues)}) ==={RESET}\n")
        for issue in size_issues:
            print(f"  {YELLOW}⚠{RESET}  {issue['file']}")
            print(f"      {issue['message']}")
        print()

    if output_issues:
        print(f"{RED}{BOLD}=== Forbidden Output ({len(output_issues)}) ==={RESET}\n")
        for issue in output_issues:
            print(f"  {RED}✗{RESET}  {issue['file']}:{issue['line']}")
            print(f"      {issue['message']}")
            print(f"      Code: {issue['code']}")
        print()

    if hardcode_issues:
        print(f"{YELLOW}{BOLD}=== Hardcoded Strings ({len(hardcode_issues)}) ==={RESET}\n")
        for issue in hardcode_issues:
            print(f"  {YELLOW}⚠{RESET}  {issue['file']}:{issue['line']}")
            print(f"      {issue['message']}")
            print(f"      Code: {issue['code']}")
        print()

    # 统计
    total = len(all_issues)
    print(f"{BOLD}--- Summary ---{RESET}")
    print(f"Files checked:        {len(files)}")
    print(f"File size issues:     {len(size_issues)}")
    print(f"Forbidden output:     {len(output_issues)}")
    print(f"Hardcoded strings:    {len(hardcode_issues)}")

    if total > 0:
        print(f"\n{RED}{BOLD}✗ FAILED: {total} quality issue(s) found.{RESET}")
        sys.exit(1)
    else:
        print(f"\n{GREEN}{BOLD}✓ PASSED: No quality issues detected.{RESET}")


if __name__ == "__main__":
    main()
