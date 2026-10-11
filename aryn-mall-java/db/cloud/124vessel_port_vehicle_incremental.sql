-- 悦航购港口报备车辆电话簿（Cloud 微服务模式）
-- 目标库：业务表 aryn_vessel；菜单/权限 aryn_upms；租户白名单配置 aryn_nacos
-- 与 boot/123vessel_port_vehicle_incremental.sql 对应，菜单 ID 段一致。
-- 背景：船舶靠港后船员外出需要打车，只有完成港口报备的车辆才能进入港区。
--       船务公司在管理端按港口维护车辆与司机电话，船员在 C 端按当前靠港查询并一键拨打。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p < 124vessel_port_vehicle_incremental.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、业务库 aryn_vessel：表
-- ============================================================================
CREATE DATABASE IF NOT EXISTS `aryn_vessel` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `aryn_vessel`;

CREATE TABLE IF NOT EXISTS `vessel_port_vehicle` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `port_name` varchar(128) NOT NULL COMMENT '港口名称（与vessel_call.port_name同口径）',
  `plate_no` varchar(32) NOT NULL COMMENT '车牌号',
  `driver_name` varchar(64) DEFAULT NULL COMMENT '司机姓名',
  `driver_phone` varchar(32) NOT NULL COMMENT '司机联系电话',
  `seat_count` int DEFAULT NULL COMMENT '座位数',
  `vehicle_desc` varchar(128) DEFAULT NULL COMMENT '车型描述（如7座商务）',
  `pickup_point` varchar(255) DEFAULT NULL COMMENT '港内等候点/上车位置',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注（服务时间等）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（越小越靠前）',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vessel_port_vehicle` (`tenant_id`,`port_name`,`plate_no`),
  KEY `idx_vessel_port_vehicle_port` (`tenant_id`,`port_name`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='港口报备车辆';

-- ============================================================================
-- 二、菜单库 aryn_upms：菜单 + 授权
-- ============================================================================
USE `aryn_upms`;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000730','港口报备车辆',NULL,'/vessel/port-vehicle',NULL,'2110000000000000000','lucide:car-taxi-front','vessel/port-vehicle/index',4,'0',NOW(),'0','0','app_base','system'),
('2110000000000000731','车辆列表','vessel:portvehicle:page',NULL,NULL,'2110000000000000730','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000732','车辆新增','vessel:portvehicle:save',NULL,NULL,'2110000000000000730','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000733','车辆修改','vessel:portvehicle:update',NULL,NULL,'2110000000000000730','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2110000000000000734','车辆删除','vessel:portvehicle:delete',NULL,NULL,'2110000000000000730','',NULL,4,'1',NOW(),'0','0','app_base','system');

-- 试点租户直开
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2114', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000730','2110000000000000731','2110000000000000732','2110000000000000733','2110000000000000734')
  AND m.`del_flag` = '0';

-- 推导式授权：能看「船供运营」目录的角色就应该能看本页（新页面父节点自身也是新建，
-- 不能按同父兄弟推导——64 号脚本踩过的坑）。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000730','2110000000000000731','2110000000000000732','2110000000000000733','2110000000000000734')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000730','2110000000000000731','2110000000000000732','2110000000000000733','2110000000000000734')
  AND m.`del_flag` = '0';

-- 自检：五条菜单应就位，页面菜单至少被一个角色授权
SELECT m.`id`, m.`name`, m.`permission`, m.`type`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS granted_roles
FROM `sys_menu` m
WHERE m.`id` BETWEEN '2110000000000000730' AND '2110000000000000734'
ORDER BY m.`id`;

-- ============================================================================
-- 三、Nacos 白名单补丁：aryn-vessel-biz-dev.yml 登记 vessel_port_vehicle
-- ============================================================================
-- TenantSchemaValidator 对「库中带 tenant_id 的表」与白名单做双向严格校验，
-- 漏配会抛 IllegalStateException("多租户 schema 校验失败") 导致 aryn-vessel-biz 启动失败并无限重启。
-- 锚点按 60 号脚本后的白名单内容逐级回退（bind_apply → call_change_log → call）。
USE `aryn_nacos`;

SET @vessel_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-vessel-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

SET @vessel_content = IF(
  @vessel_content IS NULL OR @vessel_content LIKE '%- vessel_port_vehicle%',
  @vessel_content,
  IF(
    @vessel_content LIKE '%- vessel_bind_apply%',
    REPLACE(@vessel_content,
      '      - vessel_bind_apply\n',
      '      - vessel_bind_apply\n      - vessel_port_vehicle\n'),
    IF(
      @vessel_content LIKE '%- vessel_call_change_log%',
      REPLACE(@vessel_content,
        '      - vessel_call_change_log\n',
        '      - vessel_call_change_log\n      - vessel_port_vehicle\n'),
      IF(
        @vessel_content LIKE '%- vessel_call\n%',
        REPLACE(@vessel_content,
          '      - vessel_call\n',
          '      - vessel_call\n      - vessel_port_vehicle\n'),
        @vessel_content
      )
    )
  )
);

UPDATE `config_info`
SET `content` = @vessel_content,
    `md5` = MD5(@vessel_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @vessel_content IS NOT NULL
  AND `content` <> @vessel_content;

-- 自检：白名单应已包含 vessel_port_vehicle（若查不到请人工检查 aryn-vessel-biz-dev.yml 的租户表白名单格式）
SELECT `data_id`, `group_id`
FROM `config_info`
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` LIKE '%- vessel_port_vehicle%';

SET FOREIGN_KEY_CHECKS = 1;
