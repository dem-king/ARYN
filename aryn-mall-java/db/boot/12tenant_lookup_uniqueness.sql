USE aryn_boot;

-- 以下检查必须返回空集；若存在重复，后续唯一索引 DDL 会中止，需先人工确认归属租户。
SELECT username, COUNT(*) AS duplicate_count
FROM sys_user
WHERE del_flag = '0' AND username IS NOT NULL
GROUP BY username
HAVING COUNT(*) > 1;

SELECT phone, COUNT(*) AS duplicate_count
FROM sys_user
WHERE del_flag = '0' AND phone IS NOT NULL
GROUP BY phone
HAVING COUNT(*) > 1;

SELECT app_id, COUNT(*) AS duplicate_count
FROM social_account
WHERE del_flag = '0' AND app_id IS NOT NULL
GROUP BY app_id
HAVING COUNT(*) > 1;

SELECT app_id, COUNT(*) AS duplicate_count
FROM pay_config
WHERE del_flag = '0' AND app_id IS NOT NULL
GROUP BY app_id
HAVING COUNT(*) > 1;

ALTER TABLE sys_user
    ADD UNIQUE KEY uk_sys_user_username ((IF(`del_flag` = '0', CONCAT('active:', `username`), CONCAT('deleted:', `id`)))),
    ADD UNIQUE KEY uk_sys_user_phone ((IF(`del_flag` = '0', CONCAT('active:', `phone`), CONCAT('deleted:', `id`))));

ALTER TABLE social_account
    ADD UNIQUE KEY uk_social_account_app_id ((IF(`del_flag` = '0', CONCAT('active:', `app_id`), CONCAT('deleted:', `id`))));

ALTER TABLE pay_config
    ADD UNIQUE KEY uk_pay_config_app_id ((IF(`del_flag` = '0', CONCAT('active:', `app_id`), CONCAT('deleted:', `id`))));
