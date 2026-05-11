#!/usr/bin/env python3
"""
Harness Critic — 失败模式分析器 (Harness Engineering)

扫描 harness/trace/failures/ 和 harness/trace/validation_failures.jsonl
识别重复出现的失败模式，找到 Harness 基础设施的缺口。

分析维度:
  1. 高频违规模块 — 某个模块反复涉及违规 → 层级分配可能有误或缺失
  2. 重复跨层模式 — 同一对 (source→target) 频繁出现 → 需要新的lint规则
  3. 错误信息质量 — 修复率低的错误信息 → 措辞不够清晰
  4. 新兴模式 — 新型号的quality问题 → 需要添加到禁止列表

输出:
  - 终端报告 (供人阅读)
  - harness/trace/critic_report.json (供 Refiner 消费)

用法:
    python3 scripts/critic.py                    # 全量分析
    python3 scripts/critic.py --since 7d         # 近7天
    python3 scripts/critic.py --output report.json
"""

import json
import os
import sys
from pathlib import Path
from collections import Counter, defaultdict
from datetime import datetime, timedelta

ROOT = Path(__file__).resolve().parent.parent
FAILURES_DIR = ROOT / "harness" / "trace" / "failures"
MEMORY_DIR = ROOT / "harness" / "memory"
VALIDATION_LOG = ROOT / "harness" / "trace" / "validation_failures.jsonl"

# ============================================================================
# 数据加载
# ============================================================================

def load_failures(since=None):
    """加载所有失败记录。"""
    records = []

    # 加载目录中的 JSON 文件
    if FAILURES_DIR.exists():
        for f in sorted(FAILURES_DIR.glob("*.json")):
            try:
                record = json.loads(f.read_text(encoding="utf-8"))
                record["_source_file"] = str(f)
                records.append(record)
            except (json.JSONDecodeError, Exception):
                continue

    # 加载 JSONL 日志行
    if VALIDATION_LOG.exists():
        try:
            for line in VALIDATION_LOG.read_text(encoding="utf-8").strip().split("\n"):
                if not line.strip():
                    continue
                try:
                    record = json.loads(line)
                    record["_source_file"] = str(VALIDATION_LOG)
                    records.append(record)
                except json.JSONDecodeError:
                    continue
        except Exception:
            pass

    # 按时间过滤
    if since:
        since_dt = datetime.now() - timedelta(days=since)
        records = [r for r in records if _parse_time(r.get("timestamp")) >= since_dt]

    return sorted(records, key=lambda r: r.get("timestamp", ""), reverse=True)


def _parse_time(ts):
    try:
        return datetime.fromisoformat(ts)
    except (ValueError, TypeError):
        return datetime.min


# ============================================================================
# 分析器
# ============================================================================

def analyze_layer_patterns(records):
    """分析层级违规的模式。"""
    # (source_module, target_module) → count
    pair_counts = Counter()
    # source_module → list of targets
    source_offenders = defaultdict(list)
    # target_module → list of sources
    target_victims = defaultdict(list)

    for r in records:
        if r.get("check_type") != "lint-deps":
            continue
        src = r.get("source_module", "unknown")
        tgt = r.get("target_module", "unknown")
        pair_counts[(src, tgt)] += 1
        source_offenders[src].append(tgt)
        target_victims[tgt].append(src)

    findings = []

    # 模式 1: 重复的违规对 (≥2 次)
    for (src, tgt), count in pair_counts.items():
        if count >= 2:
            findings.append({
                "type": "repeated_pair",
                "severity": "high" if count >= 5 else "medium",
                "source": src,
                "target": tgt,
                "count": count,
                "analysis": f"{src} imported {tgt} {count} times in violation of layer rules.",
                "recommendation": _recommend_for_pair(src, tgt),
            })

    # 模式 2: 某个模块反复作为违规来源 (≥3 次)
    for mod, targets in source_offenders.items():
        if len(targets) >= 3:
            most_common = Counter(targets).most_common(3)
            findings.append({
                "type": "frequent_offender",
                "severity": "high",
                "module": mod,
                "violation_count": len(targets),
                "most_imported": [{"target": t, "count": c} for t, c in most_common],
                "analysis": f"{mod} has {len(targets)} layer violations, suggesting its layer assignment may be too low.",
                "recommendation": f"Consider moving {mod} to a higher layer, or splitting it into high-layer and low-layer parts.",
            })

    # 模式 3: 某个目标模块频繁被低层 import (≥3 次)
    for mod, sources in target_victims.items():
        if len(sources) >= 3:
            findings.append({
                "type": "frequent_target",
                "severity": "medium",
                "module": mod,
                "violation_count": len(sources),
                "analysis": f"{mod} is imported by {len(sources)} lower-layer modules, suggesting its layer assignment may be too high.",
                "recommendation": f"Check if {mod} contains utility/type code that could be moved to a lower common layer.",
            })

    return findings


def analyze_quality_patterns(records):
    """分析质量问题的重复模式。"""
    type_counts = Counter()
    message_counts = Counter()
    file_offenders = Counter()

    for r in records:
        if r.get("check_type") != "lint-quality":
            continue
        issue_type = r.get("issue_type", "unknown")
        type_counts[issue_type] += 1
        msg = r.get("message", "")[:80]
        if msg:
            message_counts[msg] += 1
        fpath = Path(r.get("file", "")).name if r.get("file") else ""
        if fpath:
            file_offenders[fpath] += 1

    findings = []
    for issue_type, count in type_counts.items():
        if count >= 3:
            findings.append({
                "type": "repeated_quality_issue",
                "severity": "medium" if count >= 5 else "low",
                "issue_type": issue_type,
                "count": count,
                "analysis": f"Quality issue '{issue_type}' occurred {count} times.",
                "recommendation": f"Consider adding an automated pre-commit check for '{issue_type}' patterns.",
            })

    for fname, count in file_offenders.items():
        if count >= 3:
            findings.append({
                "type": "problem_file",
                "severity": "medium",
                "file": fname,
                "count": count,
                "analysis": f"File '{fname}' has {count} quality issues. May need refactoring.",
                "recommendation": f"Consider refactoring {fname} or adding a dedicated linter exception if justified.",
            })

    return findings


def analyze_resolution_rates(records):
    """分析修复成功率，检测措辞不清的错误信息。"""
    by_message = defaultdict(lambda: {"total": 0, "resolved": 0})

    for r in records:
        msg = r.get("message", "")[:100]
        if not msg:
            continue
        by_message[msg]["total"] += 1
        if r.get("resolution") in ("fixed", "resolved"):
            by_message[msg]["resolved"] += 1

    findings = []
    for msg, stats in by_message.items():
        if stats["total"] >= 3:
            rate = stats["resolved"] / stats["total"] if stats["total"] > 0 else 0
            if rate < 0.3:
                findings.append({
                    "type": "unclear_message",
                    "severity": "medium",
                    "message": msg,
                    "total": stats["total"],
                    "resolved": stats["resolved"],
                    "resolution_rate": f"{rate:.0%}",
                    "analysis": f"Only {rate:.0%} of agents resolved this error. The message may be unclear.",
                    "recommendation": "Rewrite this error message to include: 1) what rule was violated, 2) why it is a problem, 3) concrete fix.",
                })

    return findings


def analyze_missing_modules(records):
    """检测可能导致违规的缺失模块映射。"""
    # 内联 LAYER_MAP（避免跨文件 import 问题）
    LAYER_MAP = {
        "com.aryn.cloud.common.core": 0,
        "com.aryn.cloud.common.log": 1, "com.aryn.cloud.common.redis": 1,
        "com.aryn.cloud.common.mybatis": 1, "com.aryn.cloud.common.storage": 1,
        "com.aryn.cloud.common.sms": 1, "com.aryn.cloud.common.swagger": 1,
        "com.aryn.cloud.common.job": 1, "com.aryn.cloud.common.dubbo": 1,
        "com.aryn.cloud.common.ds": 1,
        "com.aryn.cloud.common.security": 2, "com.aryn.cloud.common.sentinel": 2,
        "com.aryn.cloud.common.seata": 2, "com.aryn.cloud.common.logistics": 2,
        "com.aryn.cloud.upms.api": 3, "com.aryn.cloud.user.api": 3,
        "com.aryn.cloud.order.api": 3, "com.aryn.cloud.pay.api": 3,
        "com.aryn.cloud.product.api": 3, "com.aryn.cloud.promotion.api": 3,
        "com.aryn.cloud.upms": 4, "com.aryn.cloud.user": 4,
        "com.aryn.cloud.order": 4, "com.aryn.cloud.pay": 4,
        "com.aryn.cloud.product": 4, "com.aryn.cloud.promotion": 4,
        "com.aryn.cloud.gateway": 5, "com.aryn.cloud.auth": 5,
        "com.aryn.cloud.boot": 5, "com.aryn.cloud.monitor": 5,
        "com.aryn.cloud.generator": 5,
    }

    unknown_modules = set()
    for r in records:
        src = r.get("source_module", "")
        tgt = r.get("target_module", "")
        if src and "." in src and src not in LAYER_MAP and not src.startswith("unknown"):
            unknown_modules.add(src)
        if tgt and "." in tgt and tgt not in LAYER_MAP and not tgt.startswith("unknown"):
            unknown_modules.add(tgt)

    findings = []
    for mod in sorted(unknown_modules):
        findings.append({
            "type": "missing_from_map",
            "severity": "high",
            "module": mod,
            "analysis": f"Module '{mod}' appears in violations but is not in LAYER_MAP.",
            "recommendation": f"Add '{mod}' to LAYER_MAP with appropriate layer number.",
        })

    return findings


def _recommend_for_pair(src, tgt):
    """为跨层违规对生成修复建议。"""
    # biz → biz
    if "biz" in src and "biz" in tgt:
        return f"Biz-to-biz direct import. Define a RemoteService interface in {tgt}-api and use @DubboReference instead."
    # common → api (低层依赖高层)
    if "common" in src and "api" in tgt:
        return f"Common module imports API module. Move the API-level type/DTO to {src} or create a shared interface in a lower layer."
    # api → biz
    if "api" in src and "biz" in tgt:
        return f"API module imports biz implementation. API should only define interfaces; implementation belongs in biz layer."
    # common → common (跨子层)
    if "common" in src and "common" in tgt:
        return f"Cross-layer common dependency. Reclassify {src} or {tgt} layers, or extract shared logic to a base common module."
    return f"Restructure to enforce unidirectional dependency: {src} should not depend on {tgt}."


# ============================================================================
# 报告生成
# ============================================================================

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
RESET = "\033[0m"
BOLD = "\033[1m"


def print_report(findings_by_category, total_records):
    """输出终端可读的分析报告。"""
    print(f"\n{BOLD}{BLUE}{'═' * 60}{RESET}")
    print(f"{BOLD}{BLUE}  Harness Critic — Failure Pattern Analysis{RESET}")
    print(f"{BOLD}{BLUE}{'═' * 60}{RESET}")
    print(f"\n  Records analyzed: {total_records}")

    for category, findings in findings_by_category.items():
        if not findings:
            continue
        print(f"\n  {BOLD}{YELLOW}┌─ {category.upper()} ({len(findings)} finding(s)){'─' * (50 - len(category))}┐{RESET}")

        for i, f in enumerate(findings):
            sev = f.get("severity", "low")
            sev_icon = f"{RED}●{RESET}" if sev == "high" else f"{YELLOW}●{RESET}"
            print(f"  │ {sev_icon} [{sev.upper()}] {_summarize(f)}")
            print(f"  │   → {f.get('recommendation', 'N/A')}")
            if i < len(findings) - 1:
                print(f"  │")

        print(f"  {YELLOW}{'└' + '─' * 58}┘{RESET}")

    print(f"\n  {BOLD}Total findings: {sum(len(f) for f in findings_by_category.values())}{RESET}")

    # 总结
    high_findings = sum(1 for f_list in findings_by_category.values() for f in f_list if f.get("severity") == "high")
    if high_findings > 0:
        print(f"\n  {RED}{BOLD}⚠ {high_findings} HIGH severity finding(s). Run refiner to address.{RESET}")
    else:
        print(f"\n  {GREEN}No high-severity findings. Harness is healthy.{RESET}")


def _summarize(finding):
    ftype = finding["type"]
    if ftype == "repeated_pair":
        return f"{finding['source']} → {finding['target']} ({finding['count']}x)"
    elif ftype == "frequent_offender":
        return f"{finding['module']}: {finding['violation_count']} violations"
    elif ftype == "frequent_target":
        return f"{finding['module']}: targeted by {finding['violation_count']} modules"
    elif ftype == "repeated_quality_issue":
        return f"{finding['issue_type']}: {finding['count']} occurrences"
    elif ftype == "problem_file":
        return f"{finding['file']}: {finding['count']} issues"
    elif ftype == "unclear_message":
        return f"Resolution rate {finding['resolution_rate']}: \"{finding['message'][:50]}...\""
    elif ftype == "missing_from_map":
        return f"Module not in LAYER_MAP: {finding['module']}"
    return str(finding)[:100]


# ============================================================================
# 主流程
# ============================================================================

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Harness Critic — Failure Pattern Analyzer")
    parser.add_argument("--since", type=int, default=None, help="Only analyze failures from the last N days")
    parser.add_argument("--output", type=str, default=None, help="Save JSON report to file")
    parser.add_argument("--summary-only", action="store_true", help="Print only a summary, no details")
    args = parser.parse_args()

    records = load_failures(since=args.since)

    if not records:
        print(f"\n  {GREEN}No failure records found. Harness is clean!{RESET}")
        sys.exit(0)

    findings = {
        "layer_patterns": analyze_layer_patterns(records),
        "quality_patterns": analyze_quality_patterns(records),
        "resolution_rates": analyze_resolution_rates(records),
        "missing_modules": analyze_missing_modules(records),
    }

    if not args.summary_only:
        print_report(findings, len(records))

    # 输出 JSON 报告（供 Refiner 使用）
    if args.output:
        report = {
            "generated_at": datetime.now().isoformat(),
            "total_records": len(records),
            "since_days": args.since,
            "findings": findings,
        }
        out_path = Path(args.output)
        out_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"\n  Report saved to: {out_path}")

    # 默认输出到 harness/trace/critic_report.json
    default_report = ROOT / "harness" / "trace" / "critic_report.json"
    report = {
        "generated_at": datetime.now().isoformat(),
        "total_records": len(records),
        "since_days": args.since,
        "findings": findings,
    }
    default_report.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
