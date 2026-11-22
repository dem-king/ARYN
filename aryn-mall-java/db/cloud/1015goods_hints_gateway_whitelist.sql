USE aryn_nacos;

-- 公开读接口仍可接收商城会话。船舶和靠港资格在活动服务内验证。
UPDATE config_info
SET content = REPLACE(content, '    - /promotion/app/ladder/prices\n',
  '    - /promotion/app/ladder/prices\n    - /promotion/app/goods-hints/*\n'),
  md5 = MD5(content)
WHERE data_id = 'aryn-gateway-dev.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = 'public'
  AND INSTR(content, '    - /promotion/app/ladder/prices\n') > 0
  AND INSTR(content, '    - /promotion/app/goods-hints/*\n') = 0;
