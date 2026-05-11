#!/usr/bin/env python3
"""
失败记录器 (Harness Engineering)

在验证失败后，Agent 调用此脚本记录失败事件，
供 Critic 脚本后续分析。

每条记录保存为独立的 JSON 文件到 harness/trace/failures/

用法:
    python3 scripts/record_failure.py \\
        --check lint-deps \\
        --source "aryn-common-storage" --source-layer 1 \\
        --target "aryn-upms-api" --target-layer 3 \\
        --file "aryn-common-storage/.../ArynUploadFileHandler.java" \\
        --line 4 \\
        --message "Layer 1 packages CANNOT import Layer 3 packages"

    # 简便用法（记录当前 validate 管道的全部失败）：
    python3 scripts/record_failure.py --from-stdin
"""

import json
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FAILURES_DIR = ROOT / "harness" / "trace" / "failures"


def ensure_dir():
    FAILURES_DIR.mkdir(parents=True, exist_ok=True)


def record(check_type, source_module="", source_layer=-1, target_module="",
           target_layer=-1, file_path="", line=0, message="", issue_type="",
           severity="error", task_id="", resolution="not_fixed"):
    """记录一条失败事件。"""
    ensure_dir()

    timestamp = datetime.now().isoformat()
    record_id = f"{timestamp[:19].replace(':', '-')}_{check_type}_{Path(file_path).stem if file_path else 'unknown'}"
    record_id = record_id.replace("/", "_").replace("\\", "_")[:120]

    entry = {
        "id": record_id,
        "timestamp": timestamp,
        "task_id": task_id,
        "check_type": check_type,
        "severity": severity,
        "source_module": source_module,
        "source_layer": source_layer,
        "target_module": target_module,
        "target_layer": target_layer,
        "issue_type": issue_type,
        "file": file_path,
        "line": line,
        "message": message,
        "resolution": resolution,
        "resolution_time": None,
    }

    out_path = FAILURES_DIR / f"{record_id}.json"

    # 如果已存在同ID记录，追加序号
    counter = 1
    while out_path.exists():
        out_path = FAILURES_DIR / f"{record_id}_{counter}.json"
        counter += 1

    out_path.write_text(json.dumps(entry, indent=2, ensure_ascii=False), encoding="utf-8")
    return str(out_path)


def parse_and_record(line_text):
    """从 lint-deps 的标准输出中解析违规并记录。"""
    # 期望格式来自 lint-deps.py 的输出
    entry = {
        "check_type": "lint-deps",
        "severity": "error",
        "timestamp": datetime.now().isoformat(),
        "source_module": "",
        "source_layer": -1,
        "target_module": "",
        "target_layer": -1,
        "file": "",
        "line": 0,
        "message": line_text[:500],
        "issue_type": "",
        "task_id": "",
        "resolution": "not_fixed",
    }

    # 尝试解析 File: 行
    import re
    file_match = re.search(r'File:\s+(.+?):(\d+)', line_text)
    if file_match:
        entry["file"] = file_match.group(1)
        entry["line"] = int(file_match.group(2))

    src_match = re.search(r'Source:\s+(.+?)\s+\(Layer\s+(\d+)\)', line_text)
    if src_match:
        entry["source_module"] = src_match.group(1)
        entry["source_layer"] = int(src_match.group(2))

    tgt_match = re.search(r'Imports:\s+(.+?)\s+\(Layer\s+(\d+)\)', line_text)
    if tgt_match:
        entry["target_module"] = tgt_match.group(1)
        entry["target_layer"] = int(tgt_match.group(2))

    return entry


def main():
    import argparse

    parser = argparse.ArgumentParser(description="Record a Harness validation failure")
    parser.add_argument("--check", type=str, default="lint-deps", help="Check type (lint-deps, lint-quality, test, verify)")
    parser.add_argument("--source", type=str, default="", help="Source module name")
    parser.add_argument("--source-layer", type=int, default=-1, help="Source module layer")
    parser.add_argument("--target", type=str, default="", help="Target module name")
    parser.add_argument("--target-layer", type=int, default=-1, help="Target module layer")
    parser.add_argument("--file", type=str, default="", help="File path involved")
    parser.add_argument("--line", type=int, default=0, help="Line number")
    parser.add_argument("--message", type=str, default="", help="Failure message")
    parser.add_argument("--issue-type", type=str, default="", help="Specific issue type (e.g., file_size, forbidden_output)")
    parser.add_argument("--severity", type=str, default="error", help="error | warning")
    parser.add_argument("--task-id", type=str, default="", help="Associated task ID")
    parser.add_argument("--resolution", type=str, default="not_fixed", help="not_fixed | fixed | ignored | escalated")
    parser.add_argument("--batch", action="store_true", help="Read failures from stdin (piped from lint output)")
    args = parser.parse_args()

    ensure_dir()

    if args.batch:
        # 从 stdin 批量读取
        text = sys.stdin.read()
        # 按 ✗ LAYER VIOLATION 分割
        blocks = text.split("✗")
        recorded = 0
        for block in blocks:
            if not block.strip():
                continue
            entry = parse_and_record(block)
            if entry.get("source_module") or entry.get("target_module") or entry.get("file"):
                out = record(
                    check_type=entry["check_type"],
                    source_module=entry["source_module"],
                    source_layer=entry["source_layer"],
                    target_module=entry["target_module"],
                    target_layer=entry["target_layer"],
                    file_path=entry["file"],
                    line=entry["line"],
                    message=entry["message"],
                    issue_type=entry["issue_type"],
                    severity=entry["severity"],
                    task_id=entry["task_id"],
                    resolution=entry["resolution"],
                )
                recorded += 1

        print(f"Recorded {recorded} failure(s) to {FAILURES_DIR}")
    else:
        out = record(
            check_type=args.check,
            source_module=args.source,
            source_layer=args.source_layer,
            target_module=args.target,
            target_layer=args.target_layer,
            file_path=args.file,
            line=args.line,
            message=args.message,
            issue_type=args.issue_type,
            severity=args.severity,
            task_id=args.task_id,
            resolution=args.resolution,
        )
        print(f"Recorded: {out}")


if __name__ == "__main__":
    main()
