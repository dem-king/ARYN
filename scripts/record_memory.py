#!/usr/bin/env python3
"""
Harness Memory — 经验记录器 (Harness Engineering)

维护三种记忆，让每次任务的知识得以积累和复用：

1. 情景记忆 (episodic): 具体事件和教训
   例: "macOS下 /var 是 /private/var 的符号链接，导致工作区路径比较失败"
   这类记忆加载成本极低（~10秒），却可能省下一整个重试循环。

2. 程序记忆 (procedural): 成功执行的步骤模式
   例: "添加 API 端点: 创建类型文件 → 写 Service → 加 Controller → 注册路由"
   新 Agent 执行同类任务时会从这里查询。

3. 宏记忆 (macro): 轨迹编译 — 同一成功模式执行3次以上，编译为脚本
   例: make add-endpoint NAME=foo

用法:
    # 记录情景记忆（教训）
    python3 scripts/record_memory.py --type episodic \\
        --title "Windows路径分隔符注意事项" \\
        --content "在Windows下subprocess需要shell=True来正确处理路径中的反斜杠"

    # 记录程序记忆（成功模式）
    python3 scripts/record_memory.py --type procedural \\
        --title "添加API端点标准步骤" \\
        --content "1.创建DTO文件 2.写Service方法 3.加Controller 4.注册路由 5.写测试"

    # 查询相关记忆
    python3 scripts/record_memory.py --query "api endpoint"
"""

import json
import sys
from datetime import datetime
from difflib import get_close_matches
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MEMORY_DIR = ROOT / "harness" / "memory"
INDEX_FILE = MEMORY_DIR / "memory_index.json"


def ensure_dir():
    MEMORY_DIR.mkdir(parents=True, exist_ok=True)


def load_index():
    if INDEX_FILE.exists():
        try:
            return json.loads(INDEX_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, Exception):
            pass
    return {"episodic": [], "procedural": [], "macro": [], "version": 1}


def save_index(index):
    ensure_dir()
    # 按时间排序
    for key in ["episodic", "procedural", "macro"]:
        if key in index:
            index[key].sort(key=lambda x: x.get("created_at", ""), reverse=True)
    INDEX_FILE.write_text(json.dumps(index, indent=2, ensure_ascii=False), encoding="utf-8")


def record_memory(memory_type, title, content, tags=None):
    """记录一条记忆。"""
    index = load_index()

    if memory_type not in ["episodic", "procedural", "macro"]:
        print(f"Unknown memory type: {memory_type}. Use: episodic, procedural, macro")
        return None

    entry = {
        "id": f"{memory_type}_{datetime.now():%Y%m%d_%H%M%S}",
        "title": title,
        "content": content,
        "tags": tags or [],
        "created_at": datetime.now().isoformat(),
        "hits": 0,
    }

    index[memory_type].append(entry)
    save_index(index)

    return entry


def query_memory(query_text, memory_type=None):
    """查询相关记忆。"""
    index = load_index()

    results = []
    types_to_search = [memory_type] if memory_type else ["episodic", "procedural", "macro"]

    for mtype in types_to_search:
        if mtype not in index:
            continue
        for entry in index[mtype]:
            score = 0
            combined = f"{entry.get('title', '')} {entry.get('content', '')} {' '.join(entry.get('tags', []))}".lower()
            query_lower = query_text.lower()

            # 关键词匹配打分
            for word in query_lower.split():
                if word in combined:
                    score += 1
            # 标签精确匹配加分
            for tag in entry.get("tags", []):
                if tag.lower() in query_lower:
                    score += 2

            if score > 0:
                entry["_score"] = score
                entry["_type"] = mtype
                results.append(entry)

    results.sort(key=lambda x: x.get("_score", 0), reverse=True)

    # 更新 hit 计数
    for r in results[:5]:
        for entry_list in index.get(r.get("_type", ""), []):
            if entry_list.get("id") == r.get("id"):
                entry_list["hits"] = entry_list.get("hits", 0) + 1
    save_index(index)

    return results


GREEN = "\033[92m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
RESET = "\033[0m"
BOLD = "\033[1m"


def main():
    import argparse

    parser = argparse.ArgumentParser(description="Harness Memory — Knowledge Recorder")
    parser.add_argument("--type", type=str, choices=["episodic", "procedural", "macro"],
                        help="Memory type")
    parser.add_argument("--title", type=str, help="Memory title")
    parser.add_argument("--content", type=str, help="Memory content")
    parser.add_argument("--tags", type=str, default="", help="Comma-separated tags")
    parser.add_argument("--query", type=str, help="Search query to find relevant memories")
    parser.add_argument("--list", type=str, choices=["all", "episodic", "procedural", "macro"],
                        help="List memories of a specific type")
    parser.add_argument("--stats", action="store_true", help="Show memory statistics")
    args = parser.parse_args()

    if args.query:
        results = query_memory(args.query)
        if results:
            print(f"\n  {BOLD}Found {len(results)} relevant memories:{RESET}\n")
            for r in results[:10]:
                tag_str = f"[{', '.join(r.get('tags', []))}]" if r.get('tags') else ""
                print(f"  {BLUE}[{r['_type']}] [{r.get('id', '')}]{RESET}")
                print(f"  {BOLD}{r['title']}{RESET}  {tag_str}")
                print(f"  {r.get('content', '')[:150]}...")
                print(f"  {YELLOW}Score: {r['_score']}  |  Hits: {r.get('hits', 0)}{RESET}\n")
        else:
            print(f"\n  No memories found for '{args.query}'. Record new ones with --type and --title.")
        return

    if args.stats:
        index = load_index()
        print(f"\n  {BOLD}Harness Memory Statistics:{RESET}\n")
        for mtype in ["episodic", "procedural", "macro"]:
            entries = index.get(mtype, [])
            total_hits = sum(e.get("hits", 0) for e in entries)
            print(f"  {mtype:15s}: {len(entries):3d} entries, {total_hits:3d} total retrievals")
        print()
        return

    if args.list:
        index = load_index()
        types_to_list = ["episodic", "procedural", "macro"] if args.list == "all" else [args.list]
        for mtype in types_to_list:
            entries = index.get(mtype, [])
            if entries:
                print(f"\n  {BOLD}{mtype.upper()} ({len(entries)}){RESET}")
                for e in entries[:20]:
                    tag_str = f"[{', '.join(e.get('tags', []))}]" if e.get('tags') else ""
                    print(f"  • {e['title'][:60]}  {tag_str}  (hits: {e.get('hits', 0)})")
        print()
        return

    if args.type and args.title and args.content:
        tags = [t.strip() for t in args.tags.split(",")] if args.tags else []
        entry = record_memory(args.type, args.title, args.content, tags)
        if entry:
            print(f"\n  {GREEN}✓ Memory recorded:{RESET}")
            print(f"  Type:    {entry['type'] if 'type' in entry else args.type}")
            print(f"  Title:   {entry['title']}")
            print(f"  Content: {entry['content'][:120]}...")
            print(f"  ID:      {entry['id']}")
        return

    parser.print_help()


if __name__ == "__main__":
    main()
