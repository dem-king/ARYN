-- 会员管理模块菜单权限初始化SQL
-- 父菜单：会员管理(1779386898750439425)
-- 会员列表(1527471479688798209) 已存在

USE aryn_upms;

SET NAMES utf8mb4;

-- ----------------------------
-- 会员等级管理菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000001', '会员等级', NULL, '/user/member-level', NULL, '1779386898750439425', 'carbon:star', 'user/member-level/index', 2, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000002', '等级列表', 'user:memberlevel:page', NULL, NULL, '2050000000000000001', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000003', '等级查询', 'user:memberlevel:get', NULL, NULL, '2050000000000000001', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000004', '等级新增', 'user:memberlevel:add', NULL, NULL, '2050000000000000001', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000005', '等级编辑', 'user:memberlevel:edit', NULL, NULL, '2050000000000000001', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000006', '等级删除', 'user:memberlevel:del', NULL, NULL, '2050000000000000001', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 积分管理父菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000010', '积分管理', NULL, '/user/points', '/user/points/config', '1779386898750439425', 'carbon:point', '', 3, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');

-- 积分配置
INSERT INTO `sys_menu` VALUES ('2050000000000000011', '积分配置', NULL, '/user/points/config', NULL, '2050000000000000010', 'carbon:settings-adjust', 'user/points/config/index', 1, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000012', '积分配置列表', 'user:pointsconfig:page', NULL, NULL, '2050000000000000011', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000013', '积分配置查询', 'user:pointsconfig:get', NULL, NULL, '2050000000000000011', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000014', '积分配置新增', 'user:pointsconfig:add', NULL, NULL, '2050000000000000011', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000015', '积分配置编辑', 'user:pointsconfig:edit', NULL, NULL, '2050000000000000011', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000016', '积分配置删除', 'user:pointsconfig:del', NULL, NULL, '2050000000000000011', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- 积分记录
INSERT INTO `sys_menu` VALUES ('2050000000000000021', '积分记录', NULL, '/user/points/record', NULL, '2050000000000000010', 'carbon:document', 'user/points/record/index', 2, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000022', '积分记录查询', 'user:pointsrecord:page', NULL, NULL, '2050000000000000021', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 签到管理父菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000030', '签到管理', NULL, '/user/sign-in', '/user/sign-in/config', '1779386898750439425', 'carbon:calendar', '', 4, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');

-- 签到配置
INSERT INTO `sys_menu` VALUES ('2050000000000000031', '签到配置', NULL, '/user/sign-in/config', NULL, '2050000000000000030', 'carbon:settings-adjust', 'user/sign-in/config/index', 1, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000032', '签到配置列表', 'user:signinconfig:page', NULL, NULL, '2050000000000000031', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000033', '签到配置查询', 'user:signinconfig:get', NULL, NULL, '2050000000000000031', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000034', '签到配置新增', 'user:signinconfig:add', NULL, NULL, '2050000000000000031', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000035', '签到配置编辑', 'user:signinconfig:edit', NULL, NULL, '2050000000000000031', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000036', '签到配置删除', 'user:signinconfig:del', NULL, NULL, '2050000000000000031', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- 签到记录
INSERT INTO `sys_menu` VALUES ('2050000000000000041', '签到记录', NULL, '/user/sign-in/record', NULL, '2050000000000000030', 'carbon:document', 'user/sign-in/record/index', 2, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000042', '签到记录查询', 'user:signinrecord:page', NULL, NULL, '2050000000000000041', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 储值配置菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000051', '储值配置', NULL, '/user/recharge', NULL, '1779386898750439425', 'carbon:wallet', 'user/recharge/index', 5, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000052', '储值配置列表', 'user:rechargeconfig:page', NULL, NULL, '2050000000000000051', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000053', '储值配置查询', 'user:rechargeconfig:get', NULL, NULL, '2050000000000000051', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000054', '储值配置新增', 'user:rechargeconfig:add', NULL, NULL, '2050000000000000051', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000055', '储值配置编辑', 'user:rechargeconfig:edit', NULL, NULL, '2050000000000000051', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000056', '储值配置删除', 'user:rechargeconfig:del', NULL, NULL, '2050000000000000051', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 余额记录菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000100', '余额记录', NULL, '/user/balance-record', NULL, '2050000000000000010', 'carbon:currency', 'user/balance-record/index', 3, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000101', '余额记录查询', 'user:balancerecord:page', NULL, NULL, '2050000000000000100', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 充值订单菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000110', '充值订单', NULL, '/user/recharge-order', NULL, '1779386898750439425', 'carbon:receipt', 'user/recharge-order/index', 6, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000111', '充值订单列表', 'user:rechargeorder:page', NULL, NULL, '2050000000000000110', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000112', '充值订单查询', 'user:rechargeorder:get', NULL, NULL, '2050000000000000110', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 会员标签菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000120', '会员标签', NULL, '/user/member-tag', NULL, '1779386898750439425', 'carbon:tag', 'user/member-tag/index', 7, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000121', '标签列表', 'user:membertag:page', NULL, NULL, '2050000000000000120', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000122', '标签查询', 'user:membertag:get', NULL, NULL, '2050000000000000120', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000123', '标签新增', 'user:membertag:add', NULL, NULL, '2050000000000000120', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000124', '标签编辑', 'user:membertag:edit', NULL, NULL, '2050000000000000120', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000125', '标签删除', 'user:membertag:del', NULL, NULL, '2050000000000000120', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 会员权益菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000130', '会员权益', NULL, '/user/member-benefit', NULL, '1779386898750439425', 'carbon:gift', 'user/member-benefit/index', 8, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000131', '权益列表', 'user:memberbenefit:page', NULL, NULL, '2050000000000000130', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000132', '权益查询', 'user:memberbenefit:get', NULL, NULL, '2050000000000000130', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000133', '权益新增', 'user:memberbenefit:add', NULL, NULL, '2050000000000000130', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000134', '权益编辑', 'user:memberbenefit:edit', NULL, NULL, '2050000000000000130', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000135', '权益删除', 'user:memberbenefit:del', NULL, NULL, '2050000000000000130', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
