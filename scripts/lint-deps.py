#!/usr/bin/env python3
"""
Java 后端层级依赖检查器 (Harness Engineering)

扫描 aryn-mall-java 下所有 Java 源文件的 import 语句，
对照层级映射表，检测跨层依赖违规。

规则: 高层可以 import 低层，反向禁止。
      L0 模块禁止 import 任何 com.aryn.cloud.* 内部包。
      L4 biz 模块之间不可直接 import 对方 (通过 Dubbo RPC 通信)。

用法:
    python3 scripts/lint-deps.py                    # 检查全部
    python3 scripts/lint-deps.py --changed-only     # 仅检查变更文件
    python3 scripts/lint-deps.py --module aryn-user-biz  # 检查指定模块
"""

import os
import re
import sys
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parent.parent
JAVA_ROOT = ROOT / "aryn-mall-java"

# ============================================================================
# 层级映射表
# ============================================================================

LAYER_MAP = {
    # Layer 0 — 纯基础类型，禁止 import 任何 aryn 内部包
    "com.aryn.cloud.common.core": 0,

    # Layer 1 — 基础设施封装
    "com.aryn.cloud.common.log": 1,
    "com.aryn.cloud.common.redis": 1,
    "com.aryn.cloud.common.mybatis": 1,
    "com.aryn.cloud.common.storage": 1,
    "com.aryn.cloud.common.sms": 1,
    "com.aryn.cloud.common.swagger": 1,
    "com.aryn.cloud.common.job": 1,
    "com.aryn.cloud.common.dubbo": 1,
    "com.aryn.cloud.common.ds": 1,

    # Layer 2 — 中间件封装
    "com.aryn.cloud.common.security": 2,
    "com.aryn.cloud.common.sentinel": 2,
    "com.aryn.cloud.common.seata": 2,
    "com.aryn.cloud.common.logistics": 2,

    # Layer 3 — API 接口定义 (DTO / Remote Service)
    "com.aryn.cloud.upms.api": 3,
    "com.aryn.cloud.user.api": 3,
    "com.aryn.cloud.order.api": 3,
    "com.aryn.cloud.pay.api": 3,
    "com.aryn.cloud.product.api": 3,
    "com.aryn.cloud.promotion.api": 3,

    # Layer 4 — 业务实现
    "com.aryn.cloud.upms": 4,
    "com.aryn.cloud.user": 4,
    "com.aryn.cloud.order": 4,
    "com.aryn.cloud.pay": 4,
    "com.aryn.cloud.product": 4,
    "com.aryn.cloud.promotion": 4,

    # Layer 5 — 入口/聚合层
    "com.aryn.cloud.gateway": 5,
    "com.aryn.cloud.auth": 5,
    "com.aryn.cloud.boot": 5,
    "com.aryn.cloud.monitor": 5,
    "com.aryn.cloud.generator": 5,
}

# L4 biz 模块之间禁止直接互相 import
BIZ_PACKAGES = {
    "com.aryn.cloud.upms",
    "com.aryn.cloud.user",
    "com.aryn.cloud.order",
    "com.aryn.cloud.pay",
    "com.aryn.cloud.product",
    "com.aryn.cloud.promotion",
}

# 模块名 → 可读名称
MODULE_NAMES = {
    "com.aryn.cloud.common.core": "aryn-common-core",
    "com.aryn.cloud.common.log": "aryn-common-log",
    "com.aryn.cloud.common.redis": "aryn-common-redis",
    "com.aryn.cloud.common.mybatis": "aryn-common-mybatis",
    "com.aryn.cloud.common.storage": "aryn-common-storage",
    "com.aryn.cloud.common.sms": "aryn-common-sms",
    "com.aryn.cloud.common.swagger": "aryn-common-swagger",
    "com.aryn.cloud.common.job": "aryn-common-job",
    "com.aryn.cloud.common.dubbo": "aryn-common-dubbo",
    "com.aryn.cloud.common.ds": "aryn-common-datasource",
    "com.aryn.cloud.common.security": "aryn-common-security",
    "com.aryn.cloud.common.sentinel": "aryn-common-sentinel",
    "com.aryn.cloud.common.seata": "aryn-common-seata",
    "com.aryn.cloud.common.logistics": "aryn-common-logistics",
    "com.aryn.cloud.upms.api": "aryn-upms-api",
    "com.aryn.cloud.user.api": "aryn-user-api",
    "com.aryn.cloud.order.api": "aryn-order-api",
    "com.aryn.cloud.pay.api": "aryn-pay-api",
    "com.aryn.cloud.product.api": "aryn-product-api",
    "com.aryn.cloud.promotion.api": "aryn-promotion-api",
    "com.aryn.cloud.upms": "aryn-upms-biz",
    "com.aryn.cloud.user": "aryn-user-biz",
    "com.aryn.cloud.order": "aryn-order-biz",
    "com.aryn.cloud.pay": "aryn-pay-biz",
    "com.aryn.cloud.product": "aryn-product-biz",
    "com.aryn.cloud.promotion": "aryn-promotion-biz",
    "com.aryn.cloud.gateway": "aryn-gateway",
    "com.aryn.cloud.auth": "aryn-auth",
    "com.aryn.cloud.boot": "aryn-boot",
    "com.aryn.cloud.monitor": "aryn-monitor",
    "com.aryn.cloud.generator": "aryn-generator",
}

# ============================================================================
# 工具函数
# ============================================================================

ARN_IMPORT_RE = re.compile(r"^import\s+(com\.aryn\.cloud\.[a-zA-Z0-9_.]+);")

# 众所周知的子包（不是独立模块，属于父模块内部）
SUB_PACKAGES = {
    "com.aryn.cloud.common.core": {"com.aryn.cloud.common.core.util", "com.aryn.cloud.common.core.constant"},
    "com.aryn.cloud.common.security": {"com.aryn.cloud.common.security.util", "com.aryn.cloud.common.security.service"},
    # 更多可继续补充
}

def resolve_package(import_pkg):
    """将 import 路径解析为模块包名（匹配 LAYER_MAP 中的 key）。"""
    if import_pkg in LAYER_MAP:
        return import_pkg

    # API 模块匹配: com.aryn.cloud.upms.api.xxx → com.aryn.cloud.upms.api
    parts = import_pkg.split(".")
    if len(parts) >= 7 and parts[5] == "api":
        base = ".".join(parts[:7])
        if base in LAYER_MAP:
            return base

    # 通用匹配: 逐步缩短路径
    for i in range(len(parts), 3, -1):
        candidate = ".".join(parts[:i])
        if candidate in LAYER_MAP:
            return candidate

    # 检查子包映射
    for parent, children in SUB_PACKAGES.items():
        for child in children:
            if import_pkg.startswith(child):
                return parent

    return None


def get_layer(pkg):
    """获取包的层级编号，未找到返回 -1。"""
    return LAYER_MAP.get(pkg, -1)


def get_module_name(pkg):
    """获取可读模块名。"""
    return MODULE_NAMES.get(pkg, pkg)


def categorize_java_file(filepath):
    """推断 Java 文件所属的模块 package（用于确定源层级）。"""
    try:
        content = filepath.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return None

    # 匹配 package 声明
    pkg_match = re.search(r"^package\s+(com\.aryn\.cloud\.[a-zA-Z0-9_.]+);", content, re.MULTILINE)
    if pkg_match:
        source_pkg = pkg_match.group(1)
        return resolve_package(source_pkg)
    return None


def scan_file(filepath):
    """扫描单个 Java 文件，返回 [(source_module, import_pkg, line_no), ...] 违规列表。"""
    violations = []
    source_module = categorize_java_file(filepath)
    if source_module is None:
        return violations

    source_layer = get_layer(source_module)
    if source_layer == -1:
        return violations  # 非核心 aryn 包，跳过

    try:
        lines = filepath.read_text(encoding="utf-8", errors="ignore").split("\n")
    except Exception:
        return violations

    for lineno, line in enumerate(lines, 1):
        match = ARN_IMPORT_RE.match(line.strip())
        if not match:
            continue

        import_pkg = resolve_package(match.group(1))
        if import_pkg is None:
            continue
        if import_pkg == source_module:
            continue  # 同模块内部 import，允许

        target_layer = get_layer(import_pkg)
        if target_layer == -1:
            continue

        # 检查 1: 层级方向 (source <= target 才是同层或依赖低层)
        if source_layer < target_layer:
            violations.append({
                "type": "layer_violation",
                "file": str(filepath),
                "line": lineno,
                "source_module": get_module_name(source_module),
                "source_layer": source_layer,
                "import_module": get_module_name(import_pkg),
                "import_layer": target_layer,
                "detail": f"Layer {source_layer} → import Layer {target_layer}",
            })
            continue

        # 检查 2: L4 biz 模块直接互相 import（index of biz modules）
        if source_layer == 4 and target_layer == 4 and source_module != import_pkg:
            # 彼此都是 biz 模块，禁止直接依赖
            violations.append({
                "type": "biz_to_biz",
                "file": str(filepath),
                "line": lineno,
                "source_module": get_module_name(source_module),
                "import_module": get_module_name(import_pkg),
                "detail": f"Biz module {get_module_name(source_module)} directly imports {get_module_name(import_pkg)}",
            })

    return violations


def collect_java_files(root, changed_only=False):
    """收集所有待扫描的 Java 文件。"""
    files = []
    for java_file in root.rglob("*.java"):
        # 跳过 target/ 编译输出
        if "target" in java_file.parts:
            continue
        # 跳过 test 代码
        if "test" in java_file.parts:
            continue
        files.append(java_file)
    return files


# ============================================================================
# 输出格式化
# ============================================================================

RED = "\033[91m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
RESET = "\033[0m"
BOLD = "\033[1m"


def format_layer_violation(v):
    return f"""{RED}{BOLD}✗ LAYER VIOLATION{RESET}
  File:    {v['file']}:{v['line']}
  Source:  {v['source_module']} (Layer {v['source_layer']})
  Imports: {v['import_module']} (Layer {v['import_layer']})
  Rule:    Layer {v['source_layer']} packages CANNOT import Layer {v['import_layer']} packages.
           Higher layers may depend on lower layers, not the reverse.
  Fix:     {_suggest_fix(v)}"""


def format_biz_violation(v):
    return f"""{RED}{BOLD}✗ BIZ-TO-BIZ VIOLATION{RESET}
  File:    {v['file']}:{v['line']}
  Source:  {v['source_module']}
  Imports: {v['import_module']}
  Rule:    Biz modules MUST NOT directly import each other.
           Communication should happen via Dubbo RPC using *-api interfaces.
  Fix:     Define a Remote Service interface in {v['import_module']}-api,
           then use @DubboReference in {v['source_module']} instead."""


def _suggest_fix(v):
    src = v["source_module"]
    imp = v["import_module"]
    if "core" in src:
        return f"Move {src} to a higher layer, or use dependency injection to avoid this import."
    if "api" in src:
        return f"API modules should only depend on common layers (L0-L2). Move the dependent logic to {imp} or restructure."
    return (
        f"Options:\n"
        f"  1. Move the dependent logic UP to {src} (consolidate)\n"
        f"  2. Move the shared logic DOWN to a lower common layer\n"
        f"  3. Use an interface/abstract class in a lower layer that {src} implements"
    )


# ============================================================================
# 主流程
# ============================================================================

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Java Architectural Dependency Linter")
    parser.add_argument("--changed-only", action="store_true", help="Only check changed files (git diff)")
    parser.add_argument("--module", type=str, help="Check only the specified module")
    args = parser.parse_args()

    if not JAVA_ROOT.exists():
        print(f"{RED}Error: {JAVA_ROOT} not found.{RESET}")
        sys.exit(1)

    print(f"{BOLD}=== Aryn Mall — Arch Dependency Check ==={RESET}\n")
    files = collect_java_files(JAVA_ROOT)

    if args.module:
        module_dir = JAVA_ROOT / args.module.replace("aryn-", "aryn-")
        files = [f for f in files if str(module_dir) in str(f)]
        print(f"Target: {args.module} ({len(files)} files)\n")

    layer_violations = []
    biz_violations = []
    checked = 0

    for fpath in files:
        violations = scan_file(fpath)
        checked += 1
        for v in violations:
            if v["type"] == "layer_violation":
                layer_violations.append(v)
            elif v["type"] == "biz_to_biz":
                biz_violations.append(v)

    # 输出结果
    all_violations = layer_violations + biz_violations

    if layer_violations:
        print(f"{RED}{BOLD}=== Layer Violations ({len(layer_violations)}) ==={RESET}\n")
        for v in layer_violations:
            print(format_layer_violation(v))
            print()

    if biz_violations:
        print(f"{RED}{BOLD}=== Biz-to-Biz Violations ({len(biz_violations)}) ==={RESET}\n")
        for v in biz_violations:
            print(format_biz_violation(v))
            print()

    # 统计
    print(f"{BOLD}--- Summary ---{RESET}")
    print(f"Files checked:          {checked}")
    print(f"Layer violations:       {len(layer_violations)}")
    print(f"Biz-to-Biz violations:  {len(biz_violations)}")

    if all_violations:
        print(f"\n{RED}{BOLD}✗ FAILED: {len(all_violations)} architecture violation(s) found.{RESET}")
        sys.exit(1)
    else:
        print(f"\n{GREEN}{BOLD}✓ PASSED: No architecture violations detected.{RESET}")


if __name__ == "__main__":
    main()
