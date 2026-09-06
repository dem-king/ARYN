import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const sections = [
  ['创建数据库', '1schema.sql'],
  ['Boot 商城基础库', '2aryn_boot.sql'],
  ['商品品牌', '19product_brand.sql'],
  ['会员管理模块', '4aryn_boot_member.sql'],
  ['站内信与客服会话', '20message_center.sql'],
  ['消息中心菜单与权限', '20message_menu.sql'],
  ['菜单种子修复', '15menu_seed_repair.sql'],
  ['页面装修类型升级', '10page_design_alter.sql'],
  ['页面装修发布与模板', '11page_design_publish.sql'],
  ['页面装修发布治理与审计', '30page_design_release_audit.sql'],
  ['页面装修主题', '32page_design_theme.sql'],
  ['页面装修定时灰度与指标', '33page_design_phase4.sql'],
  ['限时秒杀与限时折扣', '25seckill_discount.sql'],
  ['秒杀与折扣菜单权限', '26seckill_discount_menu.sql'],
  ['商城自配送模块', '25delivery_module.sql'],
  ['配送履约增量迁移', '27delivery_fulfillment_incremental.sql'],
  ['配送员商城账号绑定', '29delivery_account_binding.sql'],
  ['配送资格操作补偿记录', '34delivery_qualification_operation.sql'],
  ['配送员商城账号绑定结构升级', '35delivery_account_binding_schema_upgrade.sql'],
  ['用户角色关联逻辑删除改造', '36sys_user_role_logic_delete.sql'],
  ['XXL-JOB 调度库', '3aryn_boot_job.sql'],
];

const normalize = (content) => content.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim();
const normalizeSchema = (content) => normalize(content).replace(
  /create database `(aryn_boot(?:_job)?)`/gi,
  'CREATE DATABASE IF NOT EXISTS `$1`',
);
const banner = (title, file) => [
  '',
  '-- ============================================================================',
  `-- ${title}`,
  `-- Source: db/boot/${file}`,
  '-- ============================================================================',
  '',
].join('\n');

const header = `-- 悦航购 Aetheryn Mall - Boot 模式全量数据库初始化
-- 生成方式: node db/boot/build-full-sql.mjs
-- 适用环境: MySQL 8.0.13+
-- 警告: 本文件面向空库初始化，包含 DROP TABLE IF EXISTS，请勿用于存量生产库。
-- 生成日期: ${new Date().toISOString().slice(0, 10)}
`;

const resetTables = `
-- 全量脚本允许在开发环境重复执行；以下表在原增量来源中没有 DROP 语句。
USE aryn_boot;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS \`page_design_version\`;
DROP TABLE IF EXISTS \`page_design_template\`;
DROP TABLE IF EXISTS \`page_design_release\`;
DROP TABLE IF EXISTS \`page_design_audit_log\`;
DROP TABLE IF EXISTS \`page_design_theme\`;
DROP TABLE IF EXISTS \`page_design_release_target\`;
DROP TABLE IF EXISTS \`page_design_metric_daily\`;
DROP TABLE IF EXISTS \`group_buy_member\`;
DROP TABLE IF EXISTS \`group_buy_record\`;
DROP TABLE IF EXISTS \`group_buy_activity\`;
DROP TABLE IF EXISTS \`goods_brand\`;
DROP TABLE IF EXISTS \`delivery_account_binding\`;
DROP TABLE IF EXISTS \`delivery_qualification_operation\`;
`;

const output = sections.reduce((sql, [title, file]) => {
  const source = readFileSync(join(root, file), 'utf8');
  const content = file === '1schema.sql' ? normalizeSchema(source) : normalize(source);
  const reset = file === '2aryn_boot.sql' ? resetTables : '';
  return `${sql}${banner(title, file)}${reset}${content}\n`;
}, header);

writeFileSync(join(root, 'aryn_boot_full.sql'), output, 'utf8');
