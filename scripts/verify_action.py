#!/usr/bin/env python3
"""
操作前预验证器 (Harness Engineering)

在 Agent 执行结构性操作前（创建文件、添加跨包 import），
快速验证该操作是否违反架构约束。避免"写完再修"的高成本循环。

规则来源: scripts/lint-deps.py 中的 LAYER_MAP

用法:
    python3 scripts/verify_action.py --action "create file aryn-user-biz/.../XxxService.java"
    python3 scripts/verify_action.py --action "import com.aryn.cloud.upms from com.aryn.cloud.order"
    python3 scripts/verify_action.py --action "add dependency aryn-upms-biz → aryn-order-biz"
"""

import re
import sys
from pathlib import Path

# ============================================================================
# 层级映射表（与 lint-deps.py 保持一致）
# ============================================================================

LAYER_MAP = {
    "com.aryn.cloud.common.core": {"layer": 0, "name": "aryn-common-core"},
    "com.aryn.cloud.common.log": {"layer": 1, "name": "aryn-common-log"},
    "com.aryn.cloud.common.redis": {"layer": 1, "name": "aryn-common-redis"},
    "com.aryn.cloud.common.mybatis": {"layer": 1, "name": "aryn-common-mybatis"},
    "com.aryn.cloud.common.storage": {"layer": 1, "name": "aryn-common-storage"},
    "com.aryn.cloud.common.sms": {"layer": 1, "name": "aryn-common-sms"},
    "com.aryn.cloud.common.swagger": {"layer": 1, "name": "aryn-common-swagger"},
    "com.aryn.cloud.common.job": {"layer": 1, "name": "aryn-common-job"},
    "com.aryn.cloud.common.dubbo": {"layer": 1, "name": "aryn-common-dubbo"},
    "com.aryn.cloud.common.ds": {"layer": 1, "name": "aryn-common-datasource"},
    "com.aryn.cloud.common.security": {"layer": 2, "name": "aryn-common-security"},
    "com.aryn.cloud.common.sentinel": {"layer": 2, "name": "aryn-common-sentinel"},
    "com.aryn.cloud.common.seata": {"layer": 2, "name": "aryn-common-seata"},
    "com.aryn.cloud.common.logistics": {"layer": 2, "name": "aryn-common-logistics"},
    "com.aryn.cloud.upms.api": {"layer": 3, "name": "aryn-upms-api"},
    "com.aryn.cloud.user.api": {"layer": 3, "name": "aryn-user-api"},
    "com.aryn.cloud.order.api": {"layer": 3, "name": "aryn-order-api"},
    "com.aryn.cloud.pay.api": {"layer": 3, "name": "aryn-pay-api"},
    "com.aryn.cloud.product.api": {"layer": 3, "name": "aryn-product-api"},
    "com.aryn.cloud.promotion.api": {"layer": 3, "name": "aryn-promotion-api"},
    "com.aryn.cloud.upms": {"layer": 4, "name": "aryn-upms-biz"},
    "com.aryn.cloud.user": {"layer": 4, "name": "aryn-user-biz"},
    "com.aryn.cloud.order": {"layer": 4, "name": "aryn-order-biz"},
    "com.aryn.cloud.pay": {"layer": 4, "name": "aryn-pay-biz"},
    "com.aryn.cloud.product": {"layer": 4, "name": "aryn-product-biz"},
    "com.aryn.cloud.promotion": {"layer": 4, "name": "aryn-promotion-biz"},
    "com.aryn.cloud.gateway": {"layer": 5, "name": "aryn-gateway"},
    "com.aryn.cloud.auth": {"layer": 5, "name": "aryn-auth"},
    "com.aryn.cloud.boot": {"layer": 5, "name": "aryn-boot"},
    "com.aryn.cloud.monitor": {"layer": 5, "name": "aryn-monitor"},
    "com.aryn.cloud.generator": {"layer": 5, "name": "aryn-generator"},
}

# 模块目录 → 包名映射
MODULE_DIR_MAP = {
    "aryn-common-core": "com.aryn.cloud.common.core",
    "aryn-common-log": "com.aryn.cloud.common.log",
    "aryn-common-redis": "com.aryn.cloud.common.redis",
    "aryn-common-security": "com.aryn.cloud.common.security",
    "aryn-common-mybatis": "com.aryn.cloud.common.mybatis",
    "aryn-common-sms": "com.aryn.cloud.common.sms",
    "aryn-common-storage": "com.aryn.cloud.common.storage",
    "aryn-common-swagger": "com.aryn.cloud.common.swagger",
    "aryn-common-job": "com.aryn.cloud.common.job",
    "aryn-common-dubbo": "com.aryn.cloud.common.dubbo",
    "aryn-common-seata": "com.aryn.cloud.common.seata",
    "aryn-common-sentinel": "com.aryn.cloud.common.sentinel",
    "aryn-common-logistics": "com.aryn.cloud.common.logistics",
    "aryn-upms-api": "com.aryn.cloud.upms.api",
    "aryn-upms-biz": "com.aryn.cloud.upms",
    "aryn-user-api": "com.aryn.cloud.user.api",
    "aryn-user-biz": "com.aryn.cloud.user",
    "aryn-order-api": "com.aryn.cloud.order.api",
    "aryn-order-biz": "com.aryn.cloud.order",
    "aryn-pay-api": "com.aryn.cloud.pay.api",
    "aryn-pay-biz": "com.aryn.cloud.pay",
    "aryn-product-api": "com.aryn.cloud.product.api",
    "aryn-product-biz": "com.aryn.cloud.product",
    "aryn-promotion-api": "com.aryn.cloud.promotion.api",
    "aryn-promotion-biz": "com.aryn.cloud.promotion",
    "aryn-gateway": "com.aryn.cloud.gateway",
    "aryn-auth": "com.aryn.cloud.auth",
    "aryn-boot": "com.aryn.cloud.boot",
}


def resolve_package(pkg_path):
    """解析包路径到层级映射的 key。"""
    if pkg_path in LAYER_MAP:
        return pkg_path
    # 尝试匹配前缀
    parts = pkg_path.split(".")
    for i in range(len(parts), 4, -1):
        candidate = ".".join(parts[:i])
        if candidate in LAYER_MAP:
            return candidate
    return None


def resolve_module_from_path(path):
    """从文件路径推断所属模块。"""
    for mod_dir, pkg in MODULE_DIR_MAP.items():
        if mod_dir in path:
            return pkg
    return None


def verify_create_file(action_str):
    """验证创建文件操作。"""
    # 尝试从路径中提取模块信息
    for mod_dir, pkg in MODULE_DIR_MAP.items():
        if mod_dir in action_str:
            info = LAYER_MAP.get(pkg)
            if info:
                return True, f"File in {mod_dir} → Layer {info['layer']} ({info['name']})\n  ✓ VALID: {info['name']} follows naming convention."
    return None, None


def verify_import(action_str):
    """验证跨包 import 操作。"""
    match = re.search(r"import\s+(com\.aryn\.cloud\.[a-zA-Z0-9_.]+)\s+from\s+(com\.aryn\.cloud\.[a-zA-Z0-9_.]+)", action_str)
    if not match:
        # 尝试简化格式: "xxx from yyy"
        match = re.search(r"(com\.aryn\.cloud\.[a-zA-Z0-9_.]+)\s+from\s+(com\.aryn\.cloud\.[a-zA-Z0-9_.]+)", action_str)
    if not match:
        return None, None

    import_target = resolve_package(match.group(1))
    source = resolve_package(match.group(2))

    if not import_target or not source:
        return None, None

    target_info = LAYER_MAP[import_target]
    source_info = LAYER_MAP[source]

    target_layer = target_info["layer"]
    source_layer = source_info["layer"]

    # 规则: source_layer >= target_layer (同层或低层)
    if source_layer < target_layer:
        return False, (
            f"✗ INVALID: {source_info['name']} (Layer {source_layer}) → {target_info['name']} (Layer {target_layer})\n"
            f"  Rule: Layer {source_layer} packages cannot import Layer {target_layer} packages.\n"
            f"  Fix: Restructure — move shared logic to a lower layer, or pass data through a common interface."
        )

    # 额外的 biz-to-biz 检查
    if source_layer == 4 and target_layer == 4 and import_target != source:
        return False, (
            f"✗ INVALID: Biz module {source_info['name']} (L4) → Biz module {target_info['name']} (L4)\n"
            f"  Rule: Biz modules must communicate via Dubbo RPC, not direct import.\n"
            f"  Fix: Use @DubboReference to call Remote Service defined in {target_info['name']}-api."
        )

    return True, (
        f"✓ VALID: {source_info['name']} (L{source_layer}) imports {target_info['name']} (L{target_layer})\n"
        f"  Direction: L{source_layer} → L{target_layer} (dependency direction OK)"
    )


def verify_dependency(action_str):
    """验证模块依赖关系。"""
    match = re.search(r"(\w+[\w-]+)\s*→\s*(\w+[\w-]+)", action_str)
    if not match:
        return None, None

    dep_source = resolve_module_from_path(match.group(1))
    dep_target = resolve_module_from_path(match.group(2))

    if not dep_source or not dep_target:
        return None, None

    source_info = LAYER_MAP.get(dep_source)
    target_info = LAYER_MAP.get(dep_target)
    if not source_info or not target_info:
        return None, None

    if source_info["layer"] < target_info["layer"]:
        return False, f"✗ INVALID: {source_info['name']} (L{source_info['layer']}) must not depend on {target_info['name']} (L{target_info['layer']})"
    return True, f"✓ VALID: {source_info['name']} → {target_info['name']}"


GREEN = "\033[92m"
RED = "\033[91m"
RESET = "\033[0m"
BOLD = "\033[1m"


def main():
    import argparse

    parser = argparse.ArgumentParser(description="Pre-Action Architecture Validator")
    parser.add_argument("--action", type=str, required=True, help='Action description, e.g. "create file ..." or "import X from Y"')
    args = parser.parse_args()

    action = args.action.lower()
    valid = None
    message = None

    # 按操作类型分发
    if "create file" in action or "create " in action and "file" in action:
        valid, message = verify_create_file(action)
    elif "import" in action and "from" in action:
        valid, message = verify_import(action)
    elif "dependency" in action and "→" in action:
        valid, message = verify_dependency(action)
    elif "add dependency" in action:
        valid, message = verify_dependency(action)
    else:
        print(f"Unknown action format. Supported patterns:")
        print(f"  'create file arya-user-biz/.../Foo.java'")
        print(f"  'import com.aryn.cloud.upms from com.aryn.cloud.order'")
        print(f"  'add dependency arya-order-biz → arya-user-api'")
        sys.exit(0)

    if valid is None:
        print(f"Cannot determine validity for this action. Proceed with caution.")
        print(f"This usually means the action doesn't involve layer boundaries.")
        sys.exit(0)

    msg_colored = f"{GREEN}{message}{RESET}" if valid else f"{RED}{message}{RESET}"
    print(msg_colored)
    sys.exit(0 if valid else 1)


if __name__ == "__main__":
    main()
