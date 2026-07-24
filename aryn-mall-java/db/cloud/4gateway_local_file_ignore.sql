USE `aryn_nacos`;

SET @gateway_content = (
  SELECT content
  FROM config_info
  WHERE data_id = 'aryn-gateway-dev.yml'
    AND group_id = 'DEFAULT_GROUP'
    AND tenant_id = 'public'
  LIMIT 1
);

SET @gateway_content = IF(
  @gateway_content IS NOT NULL
    AND LOCATE('    - /upms/file/local/**', @gateway_content) = 0,
  REPLACE(
    @gateway_content,
    '    - /upms/app/tenant/shop-info\n',
    '    - /upms/app/tenant/shop-info\n    - /upms/file/local/**\n'
  ),
  @gateway_content
);

UPDATE config_info
SET content = @gateway_content,
    md5 = MD5(@gateway_content),
    gmt_modified = NOW()
WHERE data_id = 'aryn-gateway-dev.yml'
  AND group_id = 'DEFAULT_GROUP'
  AND tenant_id = 'public'
  AND @gateway_content IS NOT NULL;
