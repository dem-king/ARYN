-- 通用状态字典补齐回显样式：正常=绿、停用=灰
-- 背景：status 字典的 show_class 为空时，DictTag 会退化成纯文本，
-- 导致同为状态字段，有的页面显示标签、有的页面显示文字。
-- 本脚本可重复执行，只更新 status 字典的 0/1 两个值。
UPDATE `sys_dict_value` v
JOIN `sys_dict` d ON d.`id` = v.`dict_id`
SET v.`show_class` = CASE v.`dict_value`
    WHEN '0' THEN 'success'
    WHEN '1' THEN 'info'
  END
WHERE d.`type` = 'status'
  AND d.`del_flag` = '0'
  AND v.`del_flag` = '0'
  AND v.`dict_value` IN ('0', '1');

SET FOREIGN_KEY_CHECKS = 1;
