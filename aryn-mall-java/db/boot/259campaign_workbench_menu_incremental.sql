USE `aryn_boot`;
SET NAMES utf8mb4;
-- 只提供可分配入口；不向角色自动授予AI生成、模拟、发布或价格权限。
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by)
SELECT '2591100000000000000','采购营销工作台',NULL,'/promotion/campaign-workbench',NULL,p.parent_id,'lucide:clipboard-check','promotion/campaign-workbench/index',3,'0',NOW(),'0','0','app_base','system'
FROM sys_menu p WHERE p.component='promotion/ship-supply/index' AND p.del_flag='0' LIMIT 1;
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-workbench')),g.tenant_id,'2591100000000000000',NOW(),'system'
FROM sys_tenant_menu g JOIN sys_menu p ON p.id=g.menu_id WHERE p.component='promotion/ship-supply/index' AND p.del_flag='0';
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by) VALUES
('2591100000000000001','活动包编辑','promotion:campaign:edit',NULL,NULL,'2591100000000000000','',NULL,1,'1',NOW(),'0','0','app_base','system');
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-workbench:edit')),g.tenant_id,'2591100000000000001',NOW(),'system'
FROM sys_tenant_menu g WHERE g.menu_id='2591100000000000000';
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by) VALUES
('2591100000000000002','活动包生成','promotion:campaign:generate',NULL,NULL,'2591100000000000000','',NULL,2,'1',NOW(),'0','0','app_base','system');
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-workbench:generate')),g.tenant_id,'2591100000000000002',NOW(),'system'
FROM sys_tenant_menu g WHERE g.menu_id='2591100000000000000';
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by) VALUES
('2591100000000000003','活动包模拟','promotion:campaign:simulate',NULL,NULL,'2591100000000000000','',NULL,3,'1',NOW(),'0','0','app_base','system');
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-workbench:simulate')),g.tenant_id,'2591100000000000003',NOW(),'system'
FROM sys_tenant_menu g WHERE g.menu_id='2591100000000000000';
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by) VALUES
('2591100000000000004','活动包发布','promotion:campaign:publish',NULL,NULL,'2591100000000000000','',NULL,4,'1',NOW(),'0','0','app_base','system');
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-workbench:publish')),g.tenant_id,'2591100000000000004',NOW(),'system'
FROM sys_tenant_menu g WHERE g.menu_id='2591100000000000000';
SET FOREIGN_KEY_CHECKS = 1;
