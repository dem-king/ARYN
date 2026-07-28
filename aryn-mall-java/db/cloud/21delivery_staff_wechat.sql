-- 商城配送履约：员工微信与素材绑定
USE `aryn_upms`;

CREATE TABLE `sys_user_wechat_binding` (
  `id` varchar(32) NOT NULL COMMENT '主键', `user_id` varchar(32) NOT NULL COMMENT '员工ID',
  `app_id` varchar(64) NOT NULL COMMENT '微信小程序AppID', `openid` varchar(128) NOT NULL COMMENT '微信OpenID',
  `status` varchar(16) NOT NULL DEFAULT 'BOUND' COMMENT '绑定状态', `bound_at` datetime NOT NULL COMMENT '绑定时间',
  `unbound_at` datetime DEFAULT NULL COMMENT '解绑时间', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_wechat_user` (`tenant_id`, `user_id`, `app_id`),
  UNIQUE KEY `uk_staff_wechat_openid` (`tenant_id`, `app_id`, `openid`),
  KEY `idx_staff_wechat_status` (`tenant_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工微信绑定';

ALTER TABLE `sys_material`
  ADD COLUMN `object_key` varchar(1024) DEFAULT NULL COMMENT '存储对象键' AFTER `material_path`,
  ADD COLUMN `business_type` varchar(64) DEFAULT NULL COMMENT '业务类型' AFTER `object_key`,
  ADD COLUMN `business_id` varchar(64) DEFAULT NULL COMMENT '业务ID' AFTER `business_type`,
  ADD COLUMN `binding_status` varchar(16) NOT NULL DEFAULT 'UNBOUND' COMMENT '绑定状态' AFTER `business_id`,
  ADD COLUMN `reservation_id` varchar(64) DEFAULT NULL COMMENT '素材预占ID' AFTER `binding_status`,
  ADD COLUMN `reservation_expire_time` datetime DEFAULT NULL COMMENT '预占过期时间' AFTER `reservation_id`,
  ADD COLUMN `bound_time` datetime DEFAULT NULL COMMENT '绑定时间' AFTER `reservation_expire_time`,
  ADD KEY `idx_sys_material_binding` (`tenant_id`, `business_type`, `binding_status`, `reservation_expire_time`);
