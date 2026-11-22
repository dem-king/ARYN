USE `aryn_upms`;
SET NAMES utf8mb4;
-- 合送排程复用原波次权限；只向原有同项权限的租户/角色继承，不扩大职责。
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by) VALUES
('2521100000000000000','合送排程',NULL,'/order/delivery-group',NULL,'2110000000000000000','lucide:calendar-clock','order/delivery-group/index',8,'0',NOW(),'0','0','app_base','system'),
('2521100000000000001','排程查询','fulfillment:wave:page',NULL,NULL,'2521100000000000000','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2521100000000000002','排程管理','fulfillment:wave:save',NULL,NULL,'2521100000000000000','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2521100000000000003','开始拣货','fulfillment:wave:pick',NULL,NULL,'2521100000000000000','',NULL,3,'1',NOW(),'0','0','app_base','system');
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(gr.tenant_id,':delivery-group:',m.id)),gr.tenant_id,m.id,NOW(),'system'
FROM sys_menu m JOIN sys_menu peer ON (m.permission=peer.permission OR (m.id='2521100000000000000' AND peer.permission='fulfillment:wave:page'))
JOIN sys_tenant_menu gr ON gr.menu_id=peer.id
WHERE m.id IN ('2521100000000000000','2521100000000000001','2521100000000000002','2521100000000000003') AND peer.id NOT LIKE '252110000000000000%';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id,create_time,tenant_id)
SELECT MD5(CONCAT(gr.tenant_id,':',gr.role_id,':delivery-group:',m.id)),gr.role_id,m.id,NOW(),gr.tenant_id
FROM sys_menu m JOIN sys_menu peer ON (m.permission=peer.permission OR (m.id='2521100000000000000' AND peer.permission='fulfillment:wave:page'))
JOIN sys_role_menu gr ON gr.menu_id=peer.id
WHERE m.id IN ('2521100000000000000','2521100000000000001','2521100000000000002','2521100000000000003') AND peer.id NOT LIKE '252110000000000000%';
SET FOREIGN_KEY_CHECKS = 1;
