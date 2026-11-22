#!/usr/bin/env python3
"""AI 数据留存维护。默认预览；停写窗口显式 apply，原件脱敏/逻辑删除，禁止物理删除。

仅依赖 Python3 与 mysql CLI。凭证使用 mysql option file 或 MYSQL_PWD，禁止通过参数传密码。
Boot 四个 schema 参数相同；Cloud 必须提供同一 MySQL 实例上的四个实际 schema。
跨服务器配置/凭证退役需要一致的离线依赖快照，本工具不会猜测缺失引用。
"""
import argparse
import datetime as dt
import json
import re
import subprocess
import sys
import uuid


def identifier(value):
    if not re.fullmatch(r"[A-Za-z0-9_]{1,64}", value):
        raise ValueError("非法数据库标识")
    return "`" + value + "`"


def literal(value):
    return "(CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4) COLLATE utf8mb4_general_ci)"


def build_sql(args, run_id):
    cutoff = dt.date.fromisoformat(args.before)
    if cutoff > dt.date.today() - dt.timedelta(days=90):
        raise ValueError("留存窗口至少90天；before 为部署数据库时区的日期")
    if not 1 <= args.limit <= 100:
        raise ValueError("limit 必须为1至100")
    if args.apply and not args.writers_stopped:
        raise ValueError("apply 必须先停用相关入口、worker与重试，并声明 writers-stopped")
    if not args.tenant or not args.platform_tenant:
        raise ValueError("必须明确业务租户与平台审计租户")
    upms, order, product, promotion = [identifier(getattr(args, key)) for key in
                                      ("upms", "order", "product", "promotion")]
    tenant, platform = literal(args.tenant), literal(args.platform_tenant)
    before, marker = literal(args.before), literal("retention:" + run_id)
    enabled = "1" if args.apply else "0"
    limit = args.limit
    sql = [f"USE {upms}; SET NAMES utf8mb4; SET SESSION innodb_lock_wait_timeout=10; START TRANSACTION;",
           "CREATE TEMPORARY TABLE retention_ids(id varchar(64) PRIMARY KEY) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;",
           "CREATE TEMPORARY TABLE retention_guard(ok int NOT NULL);"]

    def guard(condition):
        # 无效引用导致整连接失败、事务回滚；不允许忽略 SQL 错误后继续执行。
        sql.append(f"INSERT INTO retention_guard VALUES(IF(({condition}),1,NULL));")

    if args.kind == "sources":
        batch = f"{order}.procurement_source_batch"
        rows = f"{order}.procurement_draft_row"
        sql.append(f"""INSERT INTO retention_ids SELECT b.id FROM {batch} b
            WHERE b.tenant_id={tenant} AND b.del_flag='0' AND b.update_time<{before}
            AND b.status IN ('CONFIRMED','SUPERSEDED','CANCELLED','QUEUED','RUNNING','FAILED','READY','REVIEW_REQUIRED')
            AND (b.lease_until IS NULL OR b.lease_until<NOW())
            AND (b.input_text IS NOT NULL OR b.input_bytes IS NOT NULL OR b.input_name IS NOT NULL
                 OR EXISTS(SELECT 1 FROM {rows} r WHERE r.tenant_id={tenant} AND r.batch_id=b.id
                    AND (r.raw_text IS NOT NULL OR r.raw_name IS NOT NULL OR r.raw_owner IS NOT NULL
                         OR r.raw_spec IS NOT NULL OR r.raw_code IS NOT NULL OR r.candidates_json IS NOT NULL
                         OR r.decision_json IS NOT NULL OR r.review_reason IS NOT NULL)))
            ORDER BY b.update_time,b.id LIMIT {limit} FOR UPDATE;""")
        sql.append(f"""UPDATE {batch} b JOIN retention_ids x ON x.id=b.id
            SET b.input_text=NULL,b.input_bytes=NULL,b.input_name=NULL,b.update_by={marker},
                b.error_code=IF(b.status IN ('CONFIRMED','SUPERSEDED','CANCELLED'),b.error_code,'MATERIAL_EXPIRED'),
                b.status=IF(b.status IN ('CONFIRMED','SUPERSEDED'),b.status,'CANCELLED'),
                b.lease_token=NULL,b.lease_until=NULL,b.next_retry_at=NULL,b.revision=b.revision+1
            WHERE b.tenant_id={tenant} AND {enabled}=1;""")
        # 保留订单/贡献所需的数量、SKU、修订、位置及确认结果；不重算历史贡献。
        sql.append(f"""UPDATE {rows} r JOIN retention_ids x ON x.id=r.batch_id
            SET r.raw_text=NULL,r.raw_name=NULL,r.raw_spec=NULL,r.raw_code=NULL,r.raw_owner=NULL,
                r.candidates_json=NULL,r.decision_json=NULL,r.review_reason=NULL,r.update_by={marker}
            WHERE r.tenant_id={tenant} AND {enabled}=1;""")
    elif args.kind == "calls":
        calls = f"{upms}.ai_call_log"
        sql.append(f"""INSERT INTO retention_ids SELECT l.id FROM {calls} l
            WHERE l.tenant_id={tenant} AND l.del_flag='0' AND l.create_time<{before}
            AND l.result<>'RUNNING'
            AND NOT EXISTS(SELECT 1 FROM {order}.procurement_source_batch b WHERE b.tenant_id=l.tenant_id
                AND b.id=l.task_id AND b.del_flag='0' AND b.status NOT IN ('CONFIRMED','SUPERSEDED','CANCELLED'))
            AND NOT EXISTS(SELECT 1 FROM {promotion}.campaign_generation_job j WHERE j.tenant_id=l.tenant_id
                AND j.id=l.task_id AND j.del_flag='0' AND j.status NOT IN ('SUCCEEDED','STALE','CANCELLED'))
            AND NOT EXISTS(SELECT 1 FROM {product}.catalog_vector_generation g WHERE g.tenant_id=l.tenant_id
                AND g.id=l.task_id AND g.del_flag='0')
            ORDER BY l.create_time,l.id LIMIT {limit} FOR UPDATE;""")
        sql.append(f"UPDATE {calls} l JOIN retention_ids x ON x.id=l.id "
                   f"SET l.del_flag='1',l.task_id='REDACTED',l.update_by={marker},l.update_time=NOW() "
                   f"WHERE l.tenant_id={tenant} AND {enabled}=1;")
    elif args.kind == "index":
        if not args.id:
            raise ValueError("索引退役必须明确 id；当前及回滚候选不会自动退役")
        generations = f"{product}.catalog_vector_generation"
        items = f"{product}.catalog_vector_item"
        selected = literal(args.id)
        guard(f"SELECT COUNT(*)=1 FROM {generations} WHERE id={selected} AND tenant_id={tenant} "
              f"AND del_flag='0' AND update_time<{before} AND status IN ('READY','FAILED','RETIRING')")
        guard(f"SELECT COUNT(*)=0 FROM {product}.catalog_vector_state WHERE generation_id={selected} AND del_flag='0'")
        # 活跃引用检查和更新在同一事务；维护窗口所有读/写worker必须已停止。
        sql.append(f"SELECT id FROM {generations} WHERE id={selected} AND tenant_id={tenant} FOR UPDATE;")
        sql.append(f"UPDATE {generations} SET status='RETIRING',update_by={marker} "
                   f"WHERE id={selected} AND tenant_id={tenant} AND {enabled}=1;")
        sql.append(f"INSERT INTO retention_ids SELECT id FROM {items} WHERE generation_id={selected} "
                   f"AND tenant_id={tenant} AND del_flag='0' ORDER BY id LIMIT 500 FOR UPDATE;")
        sql.append(f"UPDATE {items} i JOIN retention_ids x ON x.id=i.id SET i.del_flag='1',"
                   f"i.vector_json='[]',i.source_name='',i.update_by={marker},i.update_time=NOW() "
                   f"WHERE i.tenant_id={tenant} AND {enabled}=1;")
        sql.append(f"UPDATE {generations} g SET g.del_flag='1',g.status='RETIRED',g.update_by={marker} "
                   f"WHERE g.id={selected} AND g.tenant_id={tenant} AND {enabled}=1 AND NOT EXISTS"
                   f"(SELECT 1 FROM {items} i WHERE i.generation_id=g.id AND i.tenant_id=g.tenant_id AND i.del_flag='0');")
    elif args.kind == "config":
        if not args.id:
            raise ValueError("配置退役必须明确 id，回滚历史不会自动清理")
        selected = literal(args.id)
        versions = f"{upms}.ai_config_version"
        guard(f"SELECT COUNT(*)=1 FROM {versions} WHERE id={selected} AND tenant_id={platform} "
              f"AND del_flag='0' AND create_time<{before}")
        guard(f"SELECT COUNT(*)=0 FROM {upms}.ai_runtime_state WHERE del_flag='0' "
              f"AND (active_version={selected} OR draft_version={selected})")
        for schema, table, condition in [
            (product, "catalog_vector_generation", "1=1"),
            (order, "procurement_source_batch", "status NOT IN ('CONFIRMED','SUPERSEDED','CANCELLED')"),
            (promotion, "campaign_generation_job", "status NOT IN ('SUCCEEDED','STALE','CANCELLED')"),
            (upms, "ai_call_log", "result='RUNNING' AND lease_until>NOW()"),
        ]:
            column = "version_id" if table == "ai_call_log" else "config_version"
            guard(f"SELECT COUNT(*)=0 FROM {schema}.{table} WHERE {column}={selected} AND del_flag='0' AND ({condition})")
        sql.append(f"INSERT INTO retention_ids SELECT id FROM {versions} WHERE id={selected} AND tenant_id={platform} FOR UPDATE;")
        sql.append(f"UPDATE {versions} SET del_flag='1',document='{{}}',update_by={marker},update_time=NOW() "
                   f"WHERE id={selected} AND tenant_id={platform} AND {enabled}=1;")
    elif args.kind == "credential":
        if not args.id:
            raise ValueError("凭证退役必须明确 id；先退役无引用配置，再处理凭证")
        selected = literal(args.id)
        credentials = f"{upms}.ai_credential_version"
        guard(f"SELECT COUNT(*)=1 FROM {credentials} WHERE id={selected} AND tenant_id={platform} "
              f"AND del_flag='0' AND create_time<{before}")
        guard(f"SELECT COUNT(*)=0 FROM {upms}.ai_config_version WHERE del_flag='0' AND "
              f"JSON_SEARCH(document,'one',{selected},NULL,'$.providers[*].credentialVersion') IS NOT NULL")
        sql.append(f"INSERT INTO retention_ids SELECT id FROM {credentials} WHERE id={selected} AND tenant_id={platform} FOR UPDATE;")
        sql.append(f"UPDATE {credentials} SET del_flag='1',sealed_value='',update_by={marker},update_time=NOW() "
                   f"WHERE id={selected} AND tenant_id={platform} AND {enabled}=1;")
    else:
        raise ValueError("未知留存类型")
    # 只写元数据审计，失败时审计与修改一并回滚；审计不含Key、原文或模型返回。
    sql.append("SET @retention_count=(SELECT COUNT(*) FROM retention_ids);")
    sql.append(f"""INSERT INTO {upms}.ai_config_operation
        (id,operation,version_id,previous_version,tenant_id,del_flag,create_by,create_time,update_time)
        SELECT {literal(run_id)},{literal('RETENTION_' + args.kind.upper())},{literal(args.id or run_id)},
            CONCAT('rows=',@retention_count),{platform},'0',{literal(args.actor)},NOW(),NOW()
        WHERE {enabled}=1 AND (@retention_count>0 OR {literal(args.kind)}='index');""")
    sql.append(f"SELECT JSON_OBJECT('runId',{literal(run_id)},'kind',{literal(args.kind)},"
               f"'selected',@retention_count,'applied',{enabled}) AS retention_report;")
    sql.append("COMMIT;" if args.apply else "ROLLBACK;")
    # 旧库/新增表可能分别使用 general_ci 与 MySQL8 默认 0900_ai_ci。
    # 明确字符串关联排序规则，避免跨 schema 引用检查因隐式 collation 冲突失效。
    return re.sub(r"(\b[a-z]\.(?:id|tenant_id|batch_id|task_id|generation_id))=(\b[a-z]\.(?:id|tenant_id|batch_id|task_id|generation_id))",
                  r"\1 COLLATE utf8mb4_general_ci=\2 COLLATE utf8mb4_general_ci", "\n".join(sql))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=["sources", "calls", "index", "config", "credential"], required=True)
    for key in ("upms", "order", "product", "promotion", "tenant", "platform-tenant", "before", "actor"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--id")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--writers-stopped", action="store_true")
    parser.add_argument("--defaults-extra-file", help="受保护的mysql客户端配置；密码不得出现在命令参数")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_@.-]{1,64}", args.actor):
        parser.error("actor 必须为可审计的运维账号标识")
    try:
        sql = build_sql(args, str(uuid.uuid4()))
        cmd = ["mysql"]
        if args.defaults_extra_file:
            cmd.append("--defaults-extra-file=" + args.defaults_extra_file)
        cmd += ["--batch", "--skip-column-names", "--raw", "--default-character-set=utf8mb4"]
        result = subprocess.run(cmd, input=sql, text=True, capture_output=True)
        if result.returncode:
            # mysql错误可能包含SQL片段，不将其或连接认证信息输出到任务日志。
            raise RuntimeError("维护事务失败并回滚；核对引用、schema、停写窗口与数据库权限")
        reports = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        for report in reports:
            print(json.dumps(report, ensure_ascii=False))
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
