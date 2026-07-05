USE aryn_notify;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for notify_template  消息模板表
-- ----------------------------
DROP TABLE IF EXISTS `notify_template`;
CREATE TABLE `notify_template` (
  `id`             varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `template_code`  varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码（唯一）',
  `template_name`  varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `notify_type`    tinyint(4)   NOT NULL COMMENT '消息类型：1-订单 2-支付 3-物流 4-营销 5-系统 6-社交',
  `title`          varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息标题（支持变量 ${var}）',
  `content`        text         CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容（支持变量 ${var}）',
  `jump_type`      tinyint(4)   NULL DEFAULT 0 COMMENT '跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接',
  `jump_url`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转地址',
  `status`         char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0-禁用 1-启用',
  `remark`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
  `update_time`    datetime     NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag`       char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id`      varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
  `create_by`      varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by`      varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_template_code` (`template_code`, `tenant_id`) USING BTREE,
  KEY `idx_notify_type` (`notify_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息模板表';

-- ----------------------------
-- Table structure for notify_message  消息记录表
-- ----------------------------
DROP TABLE IF EXISTS `notify_message`;
CREATE TABLE `notify_message` (
  `id`             varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `user_id`        varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '接收用户ID',
  `notify_type`    tinyint(4)   NOT NULL COMMENT '消息类型：1-订单 2-支付 3-物流 4-营销 5-系统 6-社交',
  `title`          varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息标题',
  `content`        text         CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
  `biz_type`       varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务类型（order/pay/refund...）',
  `biz_id`         varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务ID（订单号/退款单号等）',
  `jump_type`      tinyint(4)   NULL DEFAULT 0 COMMENT '跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接',
  `jump_url`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转地址',
  `read_status`    char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '已读状态：0-未读 1-已读',
  `read_time`      datetime     NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
  `update_time`    datetime     NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag`       char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id`      varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
  `create_by`      varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by`      varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_type` (`user_id`, `notify_type`) USING BTREE,
  KEY `idx_user_read` (`user_id`, `read_status`) USING BTREE,
  KEY `idx_biz` (`biz_type`, `biz_id`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息记录表';

-- ----------------------------
-- Table structure for notify_broadcast  群发记录表
-- ----------------------------
DROP TABLE IF EXISTS `notify_broadcast`;
CREATE TABLE `notify_broadcast` (
  `id`             varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `title`          varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '群发标题',
  `content`        text         CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '群发内容',
  `notify_type`    tinyint(4)   NOT NULL COMMENT '消息类型',
  `target_type`    tinyint(4)   NOT NULL COMMENT '目标：1-全部用户 2-指定用户 3-指定会员等级 4-指定标签',
  `target_ids`     text         CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '目标ID列表（JSON数组）',
  `total_count`    int(11)      NULL DEFAULT 0 COMMENT '总发送数',
  `success_count`  int(11)      NULL DEFAULT 0 COMMENT '成功数',
  `status`         char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-待发送 1-发送中 2-已完成 3-已取消',
  `send_time`      datetime     NULL DEFAULT NULL COMMENT '发送时间',
  `operator_id`    varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人ID',
  `jump_type`      tinyint(4)   NULL DEFAULT 0 COMMENT '跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接',
  `jump_url`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转地址',
  `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
  `update_time`    datetime     NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag`       char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id`      varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
  `create_by`      varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by`      varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='群发记录表';

-- ----------------------------
-- 预置消息模板
-- ----------------------------
INSERT INTO `notify_template` (`id`, `template_code`, `template_name`, `notify_type`, `title`, `content`, `jump_type`, `jump_url`, `status`, `tenant_id`) VALUES
('1', 'ORDER_PAY_SUCCESS',     '订单支付成功', 1, '订单支付成功',     '您的订单 ${orderNo} 已支付成功，金额 ¥${amount}，我们将尽快为您发货。', 1, '/sub-pages/order/order-detail/index?id=${orderId}', '1', '0'),
('2', 'ORDER_COMPLETE',        '订单已完成',   1, '订单已完成',       '您的订单 ${orderNo} 已确认收货，感谢您的惠顾，欢迎评价分享购物体验。', 1, '/sub-pages/order/order-detail/index?id=${orderId}', '1', '0'),
('3', 'ORDER_CANCEL',          '订单已取消',   1, '订单已取消',       '您的订单 ${orderNo} 已取消。',                                       1, '/sub-pages/order/order-detail/index?id=${orderId}', '1', '0'),
('4', 'REFUND_SUCCESS',        '退款成功',     2, '退款成功',         '您的订单 ${orderNo} 退款 ¥${amount} 已原路退回，请注意查收。',       1, '/sub-pages/order/order-detail/index?id=${orderId}', '1', '0'),
('5', 'REFUND_REJECT',         '退款被拒绝',   2, '退款申请未通过',   '您的订单 ${orderNo} 退款申请未通过，原因：${reason}。',              1, '/sub-pages/order/order-detail/index?id=${orderId}', '1', '0'),
('6', 'COUPON_EXPIRE',         '优惠券即将过期', 4, '优惠券即将过期', '您有 ${count} 张优惠券即将在 ${expireDate} 过期，赶紧使用吧！',     3, '/sub-pages/promotion/coupon/coupon-user/index',     '1', '0'),
('7', 'PROMOTION_START',       '活动开始提醒', 4, '活动开始啦',       '您关注的 ${activityName} 已开始，快来参与吧！',                     3, '/sub-pages/home/index',                              '1', '0'),
('8', 'MEMBER_LEVEL_UP',       '会员等级提升', 5, '会员等级提升',     '恭喜！您的会员等级已升至 ${levelName}，享受更多专属权益。',         0, NULL,                                                  '1', '0'),
('9', 'BALANCE_CHANGE',        '余额变动提醒', 5, '余额变动',         '您的账户余额${changeType} ¥${amount}，当前余额 ¥${balance}。',       0, NULL,                                                  '1', '0'),
('10','SIGN_IN_REMIND',        '签到提醒',     5, '每日签到提醒',     '今天还没签到哦，签到可赚积分，连续签到奖励更多！',                  0, NULL,                                                  '1', '0');

SET FOREIGN_KEY_CHECKS = 1;
