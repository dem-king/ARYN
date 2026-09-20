-- 悦航购船舶自助绑定增量迁移（Cloud 微服务模式）
--
-- 目标库：aryn_vessel（业务表）、aryn_nacos（租户白名单）
-- 特性：幂等执行，不删除或重建数据。
--
-- 背景：C 端此前**没有任何自助绑定入口**——VesselAppController 只有 3 个 GET，
--       唯一的绑定接口是管理端 POST /admin/{id}/members。未绑定用户进不了船供链路，
--       且没有任何出路。本脚本为「邀请码 + 申请审核」两条自助通道提供数据模型。
--
-- 两条通道的分工：
--   · 邀请码 vessel_invite_code：已在船成员生成 6 位码，新同事输入即绑定。
--     零运营成本，适合规模化；天然由「已在船的同事」背书。
--   · 申请审核 vessel_bind_apply：用户自填船名提交申请，运营审核。
--     解决两个邀请码覆盖不了的场景：① 全船都是新用户（没人能生成码）
--     ② **船还没录入系统**（审核通过时由运营创建船舶再绑定）
--
-- 执行：mysql -u root -p < 63vessel_bind_incremental.sql

USE `aryn_vessel`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 船舶邀请码
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `vessel_invite_code` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `vessel_id` varchar(32) NOT NULL COMMENT '船舶ID',
  `code` varchar(12) NOT NULL COMMENT '邀请码（6 位大写字母数字，已去除易混字符）',
  `owner_user_id` varchar(32) NOT NULL COMMENT '生成人商城用户ID（命名刻意区别于审计字段 create_by）',
  `max_uses` int NOT NULL DEFAULT 0 COMMENT '最大使用次数，0 表示不限',
  `used_count` int NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1有效 0已撤销',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL, `update_by` varchar(60) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL, `update_time` datetime DEFAULT NULL,
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vessel_invite_code` (`tenant_id`, `code`),
  KEY `idx_vessel_invite_code_vessel` (`tenant_id`, `vessel_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='船舶邀请码';

-- ===========================================================================
-- 2. 船舶绑定申请
--    承载两类申请，由 apply_role 区分：
--      · 2 普通船员   —— 船员自助申请（业务员不在场时的兜底）
--      · 4 业务员     —— **销售业务员认领船舶**（冷启动主路径）
--    matched_vessel_id 在审核通过时写入：匹配到已有船舶则直接绑定，
--    否则运营先建船再绑定——这样「船还没录入系统」也能走通。
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `vessel_bind_apply` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `apply_no` varchar(40) NOT NULL COMMENT '申请单号',
  `user_id` varchar(32) NOT NULL COMMENT '申请人商城用户ID',
  `apply_role` char(2) NOT NULL DEFAULT '2' COMMENT '申请角色：2普通船员 4业务员',
  `apply_vessel_name` varchar(128) NOT NULL COMMENT '申请人填写的船名',
  `apply_vessel_imo` varchar(32) DEFAULT NULL COMMENT 'IMO 或呼号（选填）',
  `apply_port_name` varchar(128) DEFAULT NULL COMMENT '常靠港口（选填）',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `position` varchar(64) DEFAULT NULL COMMENT '船上职务 / 业务员工号',
  `remark` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1待审核 2已通过 3已驳回 4已取消',
  `matched_vessel_id` varchar(32) DEFAULT NULL COMMENT '审核通过后实际绑定的船舶ID',
  `audit_by` varchar(32) DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审核意见（驳回原因）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL, `update_by` varchar(60) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL, `update_time` datetime DEFAULT NULL,
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_vessel_bind_apply_user` (`tenant_id`, `user_id`, `status`),
  KEY `idx_vessel_bind_apply_audit` (`tenant_id`, `status`, `create_time`),
  KEY `idx_vessel_bind_apply_name` (`tenant_id`, `apply_vessel_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='船舶绑定申请';

-- ===========================================================================
-- 2b. 成员角色说明（vessel_member.member_role，本脚本不新增列，仅补充取值语义）
--     1 发起人/船长   —— 可管理成员、可提交整船订单
--     2 普通船员     —— 只能下单与维护自己的明细
--     3 采购确认人   —— 可核定数量并提交整船订单
--     4 业务员       —— **新增**：公司销售，可添加成员、可代船员下单
--
--     可添加成员的角色 = 1 / 3 / 4；普通船员(2)不可添加。
-- ===========================================================================

SET FOREIGN_KEY_CHECKS = 1;

-- ===========================================================================
-- 3. 租户白名单（关键！漏登记会导致 aryn-vessel-biz 启动即失败并无限重启）
--
--    TenantSchemaValidator 对「库中带 tenant_id 的表」与白名单做**双向严格校验**，
--    漏配会抛 IllegalStateException("多租户 schema 校验失败: 未配置租户表: [...]")。
-- ===========================================================================
USE `aryn_nacos`;

SET @vessel_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-vessel-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

-- 逐个追加，每个都先判断是否已存在，保证可重复执行
SET @vessel_content = IF(
  @vessel_content IS NULL OR @vessel_content LIKE '%- vessel_bind_apply%',
  @vessel_content,
  REPLACE(@vessel_content,
    '      - vessel_call_change_log\n',
    '      - vessel_call_change_log\n      - vessel_bind_apply\n')
);

SET @vessel_content = IF(
  @vessel_content IS NULL OR @vessel_content LIKE '%- vessel_invite_code%',
  @vessel_content,
  REPLACE(@vessel_content,
    '      - vessel_bind_apply\n',
    '      - vessel_bind_apply\n      - vessel_invite_code\n')
);

UPDATE `config_info`
SET `content` = @vessel_content,
    `md5` = MD5(@vessel_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @vessel_content IS NOT NULL
  AND `content` <> @vessel_content;

-- 自检：两张表应出现在白名单中
SELECT '白名单含 vessel_bind_apply' AS item,
       IF(`content` LIKE '%- vessel_bind_apply%', 'OK', '缺失') AS result
FROM `config_info` WHERE `data_id` = 'aryn-vessel-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
UNION ALL
SELECT '白名单含 vessel_invite_code',
       IF(`content` LIKE '%- vessel_invite_code%', 'OK', '缺失')
FROM `config_info` WHERE `data_id` = 'aryn-vessel-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP';
