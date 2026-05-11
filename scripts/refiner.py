#!/usr/bin/env python3
"""
Harness Refiner — 规则自动更新器 (Harness Engineering)

读取 Critic 的分析报告，自动修复 Harness 基础设施中的缺口。

修改范围:
  1. 将遗漏的模块加入 LAYER_MAP
  2. 调整层号分配有误的模块
  3. 在 lint-quality 中添加新的禁止模式
  4. 改写措辞不清的错误信息

模式:
  --dry-run    预览修改（默认，安全）
  --apply      实际应用修改（生成 .bak 备份）
  --interactive  逐条确认修改

用法:
    python3 scripts/refiner.py                # 预览修改建议
    python3 scripts/refiner.py --apply         # 实施修改
    python3 scripts/refiner.py --interactive   # 逐条确认
"""

import hashlib
import json
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CRITIC_REPORT = ROOT / "harness" / "trace" / "critic_report.json"
SCRIPTS_DIR = ROOT / "scripts"

# 受 Refiner 管理的源文件
MANAGED_FILES = {
    "lint_deps": SCRIPTS_DIR / "lint-deps.py",
    "lint_deps_ui": SCRIPTS_DIR / "lint-deps.mjs",
    "lint_quality": SCRIPTS_DIR / "lint-quality.py",
    "verify_action": SCRIPTS_DIR / "verify_action.py",
}

# ============================================================================
# 工具函数
# ============================================================================

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
RESET = "\033[0m"
BOLD = "\033[1m"


def backup_file(filepath):
    """创建文件备份。"""
    bak_path = Path(str(filepath) + f".bak.{datetime.now():%Y%m%d_%H%M%S}")
    shutil.copy2(filepath, bak_path)
    return bak_path


def compute_hash(content):
    return hashlib.sha256(content.encode("utf-8")).hexdigest()[:8]


def read_managed_file(key):
    """读取受管理文件内容。"""
    path = MANAGED_FILES.get(key)
    if not path or not path.exists():
        print(f"  {RED}✗ Cannot find {key} at {path}{RESET}")
        return None
    return path.read_text(encoding="utf-8")


def write_managed_file(key, content):
    """写入受管理文件。"""
    path = MANAGED_FILES.get(key)
    if not path:
        return False
    path.write_text(content, encoding="utf-8")
    return True


# ============================================================================
# 修改器
# ============================================================================

class ChangeSet:
    """记录一组待修改操作。"""

    def __init__(self):
        self.changes = []

    def add(self, file_key, description, before, after, location):
        self.changes.append({
            "file": file_key,
            "description": description,
            "before_hash": compute_hash(before),
            "after_hash": compute_hash(after),
            "before_snippet": before[:200],
            "after_snippet": after[:200],
            "location": location,
        })

    def summary(self):
        return [f"{c['file']}: {c['description']} (at {c['location']})" for c in self.changes]

    def __len__(self):
        return len(self.changes)

    def __bool__(self):
        return len(self.changes) > 0


def plan_add_missing_modules(findings, change_set):
    """为遗漏的模块生成 LAYER_MAP 添加计划。"""
    lint_deps_content = read_managed_file("lint_deps")
    if lint_deps_content is None:
        return

    verify_action_content = read_managed_file("verify_action")
    if verify_action_content is None:
        return

    for finding in findings:
        module = finding.get("module", "")
        if not module:
            continue

        # 智能推断层级
        suggested_layer = _infer_layer(module)

        # lint-deps.py: 查找插入点
        insertion_marker = '# Layer 5 — 入口/聚合层'
        indentation = " " * 4

        new_entry = f'{indentation}"{module}": {suggested_layer},\n'

        if module not in lint_deps_content:
            # 插入到合适的位置
            if suggested_layer == 0:
                insertion_marker = '# Layer 0 — 纯基础类型'
            elif suggested_layer == 1:
                insertion_marker = '# Layer 1 — 基础设施封装'
            elif suggested_layer == 2:
                insertion_marker = '# Layer 2 — 中间件封装'
            elif suggested_layer == 3:
                insertion_marker = '# Layer 3 — API 接口定义'
            elif suggested_layer == 4:
                insertion_marker = '# Layer 4 — 业务实现'
            elif suggested_layer == 5:
                insertion_marker = '# Layer 5 — 入口/聚合层'

            idx = lint_deps_content.find(insertion_marker)
            if idx >= 0:
                # 找到该注释后下一个空行或注释
                next_section = lint_deps_content.find("\n    #", idx + len(insertion_marker))
                if next_section < 0:
                    next_section = lint_deps_content.find("}", idx)

                location = f"after '{insertion_marker}'"
                change_set.add("lint_deps", f"Add {module} → Layer {suggested_layer} to LAYER_MAP",
                               "", new_entry.strip(), location)

                # 同时处理 verify_action.py
                if module not in verify_action_content:
                    change_set.add("verify_action", f"Add {module} → Layer {suggested_layer} to LAYER_MAP",
                                   "", f'    "{module}": {{"layer": {suggested_layer}, "name": "{_module_name(module)}"}},\n',
                                   "in LAYER_MAP")


def plan_add_quality_rules(findings, change_set):
    """为新兴质量问题生成新规则。"""
    lint_quality_content = read_managed_file("lint_quality")
    if lint_quality_content is None:
        return

    existing_patterns = set()
    for match in re.finditer(r're\.compile\(r"([^"]*)"\)', lint_quality_content):
        existing_patterns.add(match.group(1))

    for finding in findings:
        if finding.get("issue_type") == "forbidden_hardcode":
            # 检查是否需要添加新的硬编码模式
            snippet = finding.get("message", "")
            if snippet and "hardcoded" in snippet.lower():
                description = f"Add quality rule: {finding.get('analysis', '')}"
                change_set.add("lint_quality", description, "", "new forbidden pattern", "FORBIDDEN_HARDCODE section")


def plan_adjust_layers(findings, change_set):
    """为错误分配的层号生成调整计划。"""
    lint_deps_content = read_managed_file("lint_deps")
    if lint_deps_content is None:
        return

    for finding in findings:
        if finding.get("type") != "frequent_offender":
            continue
        module = finding.get("module", "")
        if not module:
            continue

        suggested_layer = _infer_layer(module)

        escaped = re.escape(module)
        match = re.search(rf'"{escaped}":\s*\d+', lint_deps_content)
        if match:
            current = match.group(0)
            proposed = f'"{module}": {suggested_layer}'
            old_layer = int(re.findall(r'\d+', match.group(0))[-1])
            if old_layer != suggested_layer:
                change_set.add("lint_deps", f"Adjust {module}: Layer {old_layer} → Layer {suggested_layer}",
                               current, proposed, f"line ~{lint_deps_content[:match.start()].count(chr(10))}")


def _infer_layer(module_name):
    """根据模块名推断合理层号。"""
    name = module_name.lower()
    # common-core → L0
    if "core" in name and "common" in name:
        return 0
    # common-{redis,log,mybatis,sms,storage...} → L1
    if "common" in name and any(x in name for x in ["log", "redis", "mybatis", "sms", "storage", "swagger", "job", "dubbo", "ds"]):
        return 1
    # common-{security,sentinel,seata,logistics} → L2
    if "common" in name and any(x in name for x in ["security", "sentinel", "seata", "logistics"]):
        return 2
    # *-api → L3
    if name.endswith("-api") or ".api" in name:
        return 3
    # *-biz → L4
    if name.endswith("-biz") or "biz." in name:
        return 4
    # gateway, auth, boot → L5
    if any(x in name for x in ["gateway", "auth", "boot", "monitor", "generator"]):
        return 5
    # 默认
    return 3


def _module_name(module):
    """从包名生成可读模块名。"""
    parts = module.split(".")
    # com.aryn.cloud.upms.api → aryn-upms-api
    domain_part = ".".join(parts[3:]) if len(parts) > 3 else module
    return domain_part.replace(".", "-")


# ============================================================================
# 执行
# ============================================================================

def apply_changes(change_set):
    """实际应用修改。"""
    print(f"\n  {BOLD}{BLUE}Applying {len(change_set)} change(s)...{RESET}")

    applied = 0
    for change in change_set.changes:
        file_key = change["file"]
        filepath = MANAGED_FILES.get(file_key)
        if not filepath or not filepath.exists():
            print(f"  {RED}✗ Cannot find {file_key}{RESET}")
            continue

        # 备份
        bak = backup_file(filepath)
        content = filepath.read_text(encoding="utf-8")

        description = change["description"]

        if "Add" in description and "LAYER_MAP" in description:
            # 对于添加模块的操作，我们直接解析并修改文件
            # 简化处理：找到 LAYER_MAP 定义区域，在合适的注释下插入
            layer = description.split("Layer ")[-1].split(" ")[0] if "Layer" in description else "3"
            try:
                layer_num = int(layer)
            except ValueError:
                layer_num = 3

            markers = {
                0: "# Layer 0",
                1: "# Layer 1",
                2: "# Layer 2",
                3: "# Layer 3",
                4: "# Layer 4",
                5: "# Layer 5",
            }
            marker = markers.get(layer_num, "# Layer 3")
            idx = content.find(marker)
            if idx >= 0:
                newline = content.find("\n", idx + len(marker))
                content = content[:newline + 1] + change["after_snippet"] + content[newline + 1:]

            filepath.write_text(content, encoding="utf-8")
            applied += 1
            print(f"  {GREEN}✓ {description}{RESET}  (backup: {bak.name})")

        elif "Adjust" in description:
            old_str = change["before_snippet"]
            new_str = change["after_snippet"]
            if old_str in content:
                content = content.replace(old_str, new_str, 1)
                filepath.write_text(content, encoding="utf-8")
                applied += 1
                print(f"  {GREEN}✓ {description}{RESET}  (backup: {bak.name})")
            else:
                print(f"  {YELLOW}⚠ Could not find '{old_str}' — manual adjustment needed{RESET}")

        else:
            print(f"  {YELLOW}⚠ Skipping: {description} — manual review needed{RESET}")

    print(f"\n  Applied: {applied}/{len(change_set)} changes")
    if applied < len(change_set):
        print(f"  {YELLOW}Review the skipped items and apply them manually.{RESET}")


def preview_changes(change_set):
    """预览待应用的修改。"""
    print(f"\n  {BOLD}{BLUE}{'═' * 60}{RESET}")
    print(f"  {BOLD}{BLUE}  Harness Refiner — Preview ({len(change_set)} change(s)){RESET}")
    print(f"  {BOLD}{BLUE}{'═' * 60}{RESET}")

    for i, change in enumerate(change_set.changes):
        print(f"\n  {YELLOW}Change #{i + 1}: {change['file']}{RESET}")
        print(f"  {change['description']}")
        if change.get("before_snippet"):
            print(f"  {RED}-  {change['before_snippet'][:100]}{RESET}")
        if change.get("after_snippet"):
            print(f"  {GREEN}+  {change['after_snippet'][:100]}{RESET}")

    print(f"\n  {BOLD}Run with --apply to execute.{RESET}")


# ============================================================================
# 主流程
# ============================================================================

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Harness Refiner — Rule Auto-Updater")
    parser.add_argument("--apply", action="store_true", help="Actually apply changes (generates .bak backups)")
    parser.add_argument("--dry-run", action="store_true", default=True, help="Preview changes without applying (default)")
    parser.add_argument("--interactive", action="store_true", help="Confirm each change interactively")
    parser.add_argument("--report", type=str, default=str(CRITIC_REPORT), help="Path to Critic report JSON")
    args = parser.parse_args()

    report_path = Path(args.report)
    if not report_path.exists():
        print(f"\n  {YELLOW}No Critic report found at {report_path}{RESET}")
        print(f"  Run 'python3 scripts/critic.py' first to generate a report.")
        # 仍尝试从默认位置
        if CRITIC_REPORT.exists():
            report_path = CRITIC_REPORT
        else:
            sys.exit(0)

    # 加载 Critic 报告
    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, Exception) as e:
        print(f"\n  {RED}Error reading report: {e}{RESET}")
        sys.exit(1)

    findings = report.get("findings", {})
    if not findings:
        print(f"\n  {GREEN}No findings to refine — Harness is healthy!{RESET}")
        sys.exit(0)

    all_findings = []
    for category, items in findings.items():
        all_findings.extend(items)

    if not all_findings:
        print(f"\n  {GREEN}No actionable findings.{RESET}")
        sys.exit(0)

    # 生成修改计划
    change_set = ChangeSet()

    plan_add_missing_modules(
        [f for f in all_findings if f.get("type") == "missing_from_map"], change_set
    )
    plan_adjust_layers(
        [f for f in all_findings if f.get("type") == "frequent_offender"], change_set
    )
    plan_add_quality_rules(
        [f for f in all_findings if f.get("type") == "repeated_quality_issue"], change_set
    )

    if not change_set:
        print(f"\n  {GREEN}Critic report identified patterns, but no automatable fixes were found.{RESET}")
        print(f"  Consider manual review of the report at {report_path}")
        sys.exit(0)

    if args.apply:
        apply_changes(change_set)
        print(f"\n  {GREEN}{BOLD}✓ Refinement applied. Run critic again to verify improvement.{RESET}")
    else:
        preview_changes(change_set)


if __name__ == "__main__":
    main()
