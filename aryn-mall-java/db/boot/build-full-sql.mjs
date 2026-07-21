import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const sections = [
  ['创建数据库', '1schema.sql'],
  ['Boot 商城基础库', '2aryn_boot.sql'],
  ['商品品牌', '19product_brand.sql'],
  ['会员管理模块', '4aryn_boot_member.sql'],
  ['菜单种子修复', '15menu_seed_repair.sql'],
  ['页面装修类型升级', '10page_design_alter.sql'],
  ['页面装修发布与模板', '11page_design_publish.sql'],
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
-- 生成日期: 2026-07-17
`;

const resetTables = `
-- 全量脚本允许在开发环境重复执行；以下表在原增量来源中没有 DROP 语句。
USE aryn_boot;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS \`page_design_version\`;
DROP TABLE IF EXISTS \`page_design_template\`;
DROP TABLE IF EXISTS \`group_buy_member\`;
DROP TABLE IF EXISTS \`group_buy_record\`;
DROP TABLE IF EXISTS \`group_buy_activity\`;
DROP TABLE IF EXISTS \`goods_brand\`;
`;

const output = sections.reduce((sql, [title, file]) => {
  const source = readFileSync(join(root, file), 'utf8');
  const content = file === '1schema.sql' ? normalizeSchema(source) : normalize(source);
  const reset = file === '2aryn_boot.sql' ? resetTables : '';
  return `${sql}${banner(title, file)}${reset}${content}\n`;
}, header);

writeFileSync(join(root, 'aryn_boot_full.sql'), output, 'utf8');
