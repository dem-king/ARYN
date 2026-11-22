#!/usr/bin/env python3
"""真实隔离MySQL留存行为验证；先在两个aryn_ai_verify_*库应用本次双模式增量。
只写合成fixture并保留验证记录，禁止把变量指向业务库。
"""
import argparse,importlib.util,json,re,subprocess,uuid
from pathlib import Path
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('retention',root/'db/maintenance/ai-retention.py');r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
# 只接受独立验证schema；不提供变量时跳过，不能以跳过称为通过。
import os,sys
config=os.environ.get('ARYN_AI_RETENTION_TEST_SCHEMAS')
if not config:
 print('SKIPPED: set ARYN_AI_RETENTION_TEST_SCHEMAS to isolated boot/cloud schema JSON')
 sys.exit(0)
dbs=json.loads(config)
assert set(dbs)=={'boot','cloud'} and all(re.fullmatch(r'aryn_ai_verify_[A-Za-z0-9_]+',v) for v in dbs.values())
cmd=['mysql','--batch','--skip-column-names','--raw','--default-character-set=utf8mb4']
# 连接选项使用MYSQL_*环境或受保护的defaults-extra-file，不在参数中传密码。
client_file=os.environ.get('ARYN_AI_RETENTION_TEST_DEFAULTS')
if client_file:cmd.insert(1,'--defaults-extra-file='+client_file)
def run(sql,ok=True):
 p=subprocess.run(cmd,input=sql,text=True,capture_output=True)
 if ok and p.returncode:raise RuntimeError('隔离SQL执行失败；不输出可能包含材料/认证信息的原始错误')
 if not ok:assert p.returncode!=0
 return p.stdout.strip()
def count(db,sql):return int(run('USE `'+db+'`;'+sql))
for mode in ['boot','cloud']:
 db=dbs[mode];tenant='ret-'+uuid.uuid4().hex[:12];platform='ret-p-'+uuid.uuid4().hex[:12]
 args=argparse.Namespace(kind='sources',upms=db,order=db,product=db,promotion=db,tenant=tenant,platform_tenant=platform,before='2001-01-01',actor='retention-fixture',limit=20,apply=False,writers_stopped=True,id=None)
 # Fixture只含受守卫库，字段由真实增量DDL定义。
 run(f'''USE `{db}`;
 INSERT INTO procurement_draft(id,owner_user_id,purchase_scene,client_request_id,context_fingerprint,status,revision,tenant_id) VALUES('{tenant}-d','u','1','r','fp','OPEN',0,'{tenant}');
 INSERT INTO procurement_source_batch(id,draft_id,operator_user_id,input_type,client_request_id,input_digest,explicit_new,status,input_text,input_name,tenant_id,create_time,update_time)
 VALUES('{tenant}-b','{tenant}-d','u','TEXT','r','digest',0,'CONFIRMED','fixture original','fixture.txt','{tenant}','2000-01-01','2000-01-01'),
 ('{tenant}-live','{tenant}-d','u','TEXT','live','live-digest',0,'FAILED','must retain','retry.txt','{tenant}',NOW(),NOW()),
 ('{tenant}-expired','{tenant}-d','u','TEXT','expired','expired-digest',0,'READY','old undecided','old.txt','{tenant}','2000-01-01','2000-01-01');
 INSERT INTO procurement_draft_row(id,draft_id,batch_id,source_position,parse_revision,raw_text,raw_name,raw_owner,selected_sku_id,confirmed_sales_quantity,tenant_id)
 VALUES('{tenant}-row','{tenant}-d','{tenant}-b','1',1,'fixture row','fixture name','fixture owner','sku',3,'{tenant}');
 ''')
 run(r.build_sql(args,str(uuid.uuid4())))
 assert count(db,f"SELECT COUNT(*) FROM procurement_source_batch WHERE id='{tenant}-b' AND input_text IS NOT NULL;")==1
 args.apply=True;run(r.build_sql(args,str(uuid.uuid4())))
 assert count(db,f"SELECT COUNT(*) FROM procurement_draft_row WHERE id='{tenant}-row' AND raw_text IS NULL AND selected_sku_id='sku' AND confirmed_sales_quantity=3;")==1
 assert count(db,f"SELECT COUNT(*) FROM procurement_source_batch WHERE id='{tenant}-live' AND input_text='must retain';")==1
 assert count(db,f"SELECT COUNT(*) FROM procurement_source_batch WHERE id='{tenant}-expired' AND status='CANCELLED' AND error_code='MATERIAL_EXPIRED' AND input_text IS NULL;")==1
 run(r.build_sql(args,str(uuid.uuid4())))
 print(mode,'sources: dry-run unchanged; terminal raw scrubbed; quantities/retry preserved; repeat safe')
 config=tenant+'-cfg';cred=tenant+'-key';gen=tenant+'-idx'
 doc=json.dumps({'providers':[{'id':'fixture','credentialVersion':cred}],'models':[],'bindings':{}},separators=(',',':'))
 run(f'''USE `{db}`;
 INSERT INTO ai_config_version(id,document,digest,revision,tenant_id,create_time) VALUES('{config}','{doc}','digest',0,'{platform}','2000-01-01');
 INSERT INTO ai_credential_version(id,provider_id,sealed_value,tenant_id,create_time) VALUES('{cred}','fixture','synthetic ciphertext','{platform}','2000-01-01');
 INSERT INTO ai_runtime_state(id,draft_version,active_version,revision,emergency_epoch,disabled,disabled_providers,tenant_id) VALUES('{platform}','{config}','{config}',0,0,0,'[]','{platform}');
 INSERT INTO catalog_vector_generation(id,config_version,model_fingerprint,dimension,status,tenant_id,create_time,update_time) VALUES('{gen}','{config}','fingerprint',2,'READY','{tenant}','2000-01-01','2000-01-01');
 INSERT INTO catalog_vector_item(id,generation_id,sku_id,source_name,vector_json,tenant_id) VALUES('{gen}-i','{gen}','sku','fixture','[0,1]','{tenant}');
 INSERT INTO catalog_vector_state(id,generation_id,revision,tenant_id) VALUES('{tenant}','{gen}',0,'{tenant}');
 ''')
 args.kind='credential';args.id=cred;run(r.build_sql(args,str(uuid.uuid4())),False)
 args.kind='config';args.id=config;run(r.build_sql(args,str(uuid.uuid4())),False)
 args.kind='index';args.id=gen;run(r.build_sql(args,str(uuid.uuid4())),False)
 run(f"USE `{db}`;UPDATE catalog_vector_state SET generation_id=NULL WHERE id='{tenant}';")
 run(r.build_sql(args,str(uuid.uuid4())))
 assert count(db,f"SELECT COUNT(*) FROM catalog_vector_generation WHERE id='{gen}' AND del_flag='1';")==1
 assert count(db,f"SELECT COUNT(*) FROM catalog_vector_item WHERE generation_id='{gen}' AND del_flag='1' AND vector_json='[]';")==1
 args.kind='config';args.id=config;run(r.build_sql(args,str(uuid.uuid4())),False)
 run(f"USE `{db}`;UPDATE ai_runtime_state SET active_version=NULL,draft_version=NULL WHERE id='{platform}'; UPDATE procurement_source_batch SET config_version='{config}' WHERE id='{tenant}-live';")
 run(r.build_sql(args,str(uuid.uuid4())),False)
 run(f"USE `{db}`;UPDATE procurement_source_batch SET status='CANCELLED' WHERE id='{tenant}-live';")
 run(r.build_sql(args,str(uuid.uuid4())))
 args.kind='credential';args.id=cred;run(r.build_sql(args,str(uuid.uuid4())))
 assert count(db,f"SELECT COUNT(*) FROM ai_credential_version WHERE id='{cred}' AND del_flag='1' AND sealed_value='';")==1
 print(mode,'dependencies: active index/active config/credential/retry guarded; explicit retirement scrubbed; durable audit written')
 args.kind='calls';args.id=None
 run(f'''USE `{db}`;INSERT INTO ai_call_log(id,usage_code,task_id,version_id,provider_id,model_id,result,reserved_tokens,tenant_id,create_time)
 VALUES('{tenant}-log','PROCUREMENT_TEXT','closed','old','p','m','SUCCESS',10,'{tenant}','2000-01-01'),
 ('{tenant}-running','PROCUREMENT_TEXT','running','old','p','m','RUNNING',10,'{tenant}','2000-01-01');''')
 run(r.build_sql(args,str(uuid.uuid4())))
 assert count(db,f"SELECT COUNT(*) FROM ai_call_log WHERE id='{tenant}-log' AND del_flag='1' AND task_id='REDACTED';")==1
 assert count(db,f"SELECT COUNT(*) FROM ai_call_log WHERE id='{tenant}-running' AND del_flag='0';")==1
 assert count(db,f"SELECT COUNT(*) FROM ai_config_operation WHERE tenant_id='{platform}' AND operation LIKE 'RETENTION_%';")>=5
 print(mode,'logs: completed old calls logically deleted; in-flight preserved')
 args.before='2099-01-01'
 try:r.build_sql(args,str(uuid.uuid4()));raise AssertionError('future cutoff accepted')
 except ValueError:pass
print('All retention checks passed; only isolated schemas used')
