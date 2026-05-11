#!/usr/bin/env python3
"""
Harness 统一验证管道 (Harness Engineering)

串联所有验证步骤，Agent 只需跑这一条命令：
    python3 scripts/validate.py --backend

验证流程:
    build → lint-arch → lint-quality → test → [verify]

原则:
  - 每一步失败即停止（fail-fast），避免无效修复消耗上下文
  - 错误信息包含：规则违反描述 + 原因 + 修复建议
  - 支持跳过耗时步骤（--skip-test, --skip-verify）

用法:
    python3 scripts/validate.py --backend        # 后端全量
    python3 scripts/validate.py --frontend       # 前端全量
    python3 scripts/validate.py --backend --skip-test --skip-verify  # 快速检查
"""

import sys
import subprocess
import time
from pathlib import Path
from datetime import datetime

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"

# ============================================================================
# 输出
# ============================================================================

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
RESET = "\033[0m"
BOLD = "\033[1m"


def step_header(name, description):
    print(f"\n{BOLD}{BLUE}━━━ {name} ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}")
    print(f"  {description}")


def step_success(name, duration):
    print(f"  {GREEN}✓ {name} passed ({duration:.1f}s){RESET}")


def step_failure(name, duration, exit_code):
    print(f"  {RED}✗ {name} FAILED ({duration:.1f}s, exit={exit_code}){RESET}")


def run_step(cmd, cwd=None, env=None):
    """执行验证步骤，返回 (success, duration, output)。"""
    start = time.time()
    try:
        result = subprocess.run(
            cmd,
            cwd=str(cwd) if cwd else str(ROOT),
            capture_output=True,
            text=True,
            timeout=300,
            env=env,
            shell=False,
        )
        duration = time.time() - start
        stdout = result.stdout[:5000] if result.stdout else ""
        stderr = result.stderr[:3000] if result.stderr else ""
        return result.returncode == 0, duration, stdout, stderr, result.returncode
    except subprocess.TimeoutExpired:
        duration = time.time() - start
        return False, duration, "", "Step timed out (> 5 minutes)", -1
    except FileNotFoundError as e:
        duration = time.time() - start
        return False, duration, "", f"Command not found: {e}", -1


# ============================================================================
# 后端验证
# ============================================================================

def validate_backend(skip_test=False, skip_verify=False):
    """执行后端完整验证管道。"""
    java_root = ROOT / "aryn-mall-java"
    if not java_root.exists():
        print(f"{RED}Error: {java_root} not found.{RESET}")
        return False

    steps = [
        {
            "name": "1. BUILD (mvn compile)",
            "cmd": ["mvn", "clean", "compile", "-pl", "aryn-boot", "-am", "-q"],
            "cwd": java_root,
        },
        {
            "name": "2. LINT-ARCH (dependency check)",
            "cmd": [sys.executable, str(SCRIPTS / "lint-deps.py")],
            "cwd": ROOT,
        },
        {
            "name": "3. LINT-QUALITY (code quality)",
            "cmd": [sys.executable, str(SCRIPTS / "lint-quality.py")],
            "cwd": ROOT,
        },
    ]

    if not skip_test:
        steps.append({
            "name": "4. TEST (mvn test)",
            "cmd": ["mvn", "clean", "test", "-pl", "aryn-boot", "-am", "-q"],
            "cwd": java_root,
        })

    if not skip_verify:
        verify_script = SCRIPTS / "verify" / "run.py"
        if verify_script.exists():
            steps.append({
                "name": "5. VERIFY (e2e functional)",
                "cmd": [sys.executable, str(verify_script)],
                "cwd": ROOT,
            })
        else:
            print(f"  {YELLOW}⚠ verify/run.py not found — skipping e2e verify.{RESET}")

    return execute_steps(steps)

# ============================================================================
# 前端验证
# ============================================================================

def validate_frontend():
    """执行前端 (aryn-mall-ui) 验证。"""
    ui_root = ROOT / "aryn-mall-ui"
    if not ui_root.exists():
        print(f"{RED}Error: {ui_root} not found.{RESET}")
        return False

    steps = [
        {
            "name": "1. LINT (ESLint)",
            "cmd": ["pnpm", "lint"],
            "cwd": ui_root,
        },
        {
            "name": "2. LINT-DEPS (dependency check)",
            "cmd": ["node", str(SCRIPTS / "lint-deps.mjs")],
            "cwd": ROOT,
        },
        {
            "name": "3. TYPECHECK (vue-tsc)",
            "cmd": ["pnpm", "typecheck"],
            "cwd": ui_root,
        },
    ]
    return execute_steps(steps)


def execute_steps(steps):
    """执行验证步骤序列，失败即停止。"""
    failed = False

    for step in steps:
        step_header(step["name"], " ".join(step["cmd"]))
        success, duration, stdout, stderr, exit_code = run_step(
            step["cmd"],
            cwd=step.get("cwd"),
        )

        if success:
            step_success(step["name"], duration)
            if stdout.strip():
                # 只显示最后几行关键信息
                lines = [l for l in stdout.strip().split("\n") if l.strip()][-5:]
                for line in lines:
                    print(f"    {line[:120]}")
        else:
            step_failure(step["name"], duration, exit_code)
            failed = True
            # 输出错误信息（帮助 Agent 诊断）
            if stderr.strip():
                print(f"\n  {RED}Error output:{RESET}")
                for line in stderr.strip().split("\n")[-20:]:
                    print(f"    {line[:150]}")
            if stdout.strip():
                print(f"\n  {YELLOW}Last output:{RESET}")
                for line in stdout.strip().split("\n")[-10:]:
                    print(f"    {line[:150]}")
            break  # fail-fast

    return not failed


# ============================================================================
# 主入口
# ============================================================================

def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="Harness Unified Validation Pipeline",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 scripts/validate.py --backend                  # Full backend pipeline
  python3 scripts/validate.py --backend --skip-test      # Quick architecture check
  python3 scripts/validate.py --frontend                 # Full frontend pipeline
""",
    )
    parser.add_argument("--backend", action="store_true", help="Validate Java backend")
    parser.add_argument("--frontend", action="store_true", help="Validate frontend UI")
    parser.add_argument("--skip-test", action="store_true", help="Skip unit/integration tests")
    parser.add_argument("--skip-verify", action="store_true", help="Skip e2e functional verify")

    args = parser.parse_args()

    if not args.backend and not args.frontend:
        # 默认运行后端验证
        args.backend = True

    print(f"{BOLD}{BLUE}╔══════════════════════════════════════════════╗{RESET}")
    print(f"{BOLD}{BLUE}║  Aryn Mall — Harness Validation Pipeline     ║{RESET}")
    print(f"{BOLD}{BLUE}║  {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}                              ║{RESET}")
    print(f"{BOLD}{BLUE}╚══════════════════════════════════════════════╝{RESET}")

    if args.skip_test:
        print(f"  {YELLOW}⚠ Skipping tests{RESET}")
    if args.skip_verify:
        print(f"  {YELLOW}⚠ Skipping e2e verify{RESET}")

    success = True
    if args.backend:
        success = validate_backend(skip_test=args.skip_test, skip_verify=args.skip_verify)
    if args.frontend and success:  # 只有后端通过才跑前端
        success = validate_frontend()

    # 最终结果
    print(f"\n{BOLD}{'═' * 50}{RESET}")
    if success:
        print(f"{GREEN}{BOLD}✓ ALL CHECKS PASSED — Ready to proceed.{RESET}")
        sys.exit(0)
    else:
        print(f"{RED}{BOLD}✗ VALIDATION FAILED — Fix issues before continuing.{RESET}")
        print(f"\n  Tip: The error messages above include what rule was violated,")
        print(f"       why it's a problem, and how to fix it. Use them as guidance.")
        print(f"       If the same error persists after 3 fix attempts, escalate to human.")
        sys.exit(1)


if __name__ == "__main__":
    main()
