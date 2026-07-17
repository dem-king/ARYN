import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const sql = readFileSync(join(root, 'aryn_boot_full.sql'), 'utf8');

const required = [
  'CREATE DATABASE IF NOT EXISTS `aryn_boot`',
  'CREATE DATABASE IF NOT EXISTS `aryn_boot_job`',
  'USE aryn_boot;',
  'USE aryn_boot_job;',
  'CREATE TABLE `member_level`',
  'ADD COLUMN `member_level_id`',
  'ADD COLUMN page_type',
  'CREATE TABLE page_design_version',
  'CREATE TABLE page_design_template',
  "'promotion:pagedesign:publish'",
  "'promotion:pagedesign:rollback'",
  "'promotion:pagedesign:template'",
  'INSERT INTO page_design_version',
  'CREATE TABLE `xxl_job_info`',
];

for (const token of required) {
  if (!sql.includes(token)) throw new Error(`缺少关键 SQL: ${token}`);
}

const sourceFiles = [
  '1schema.sql',
  '2aryn_boot.sql',
  '4aryn_boot_member.sql',
  '10page_design_alter.sql',
  '11page_design_publish.sql',
  '3aryn_boot_job.sql',
];
for (const file of sourceFiles) {
  if (!sql.includes(`Source: db/boot/${file}`)) throw new Error(`缺少来源段: ${file}`);
}

if (sql.includes('Source: db/boot/12tenant_lookup_uniqueness.sql')) {
  throw new Error('不应重复合并 12tenant_lookup_uniqueness.sql');
}

const createTables = [...sql.matchAll(/^CREATE TABLE(?: IF NOT EXISTS)?\s+`?([^` (]+)/gim)]
  .map((match) => match[1]);
const dropTables = new Set(
  [...sql.matchAll(/^DROP TABLE IF EXISTS\s+`?([^`; ]+)/gim)].map((match) => match[1]),
);
const duplicateTables = createTables.filter((table, index) => createTables.indexOf(table) !== index);
if (duplicateTables.length > 0) {
  throw new Error(`存在重复建表: ${[...new Set(duplicateTables)].join(', ')}`);
}
const missingDrops = createTables.filter((table) => !dropTables.has(table));
if (missingDrops.length > 0) {
  throw new Error(`全量脚本缺少 DROP TABLE: ${missingDrops.join(', ')}`);
}
if (createTables.length !== 80) {
  throw new Error(`建表数量异常，期望 80，实际 ${createTables.length}`);
}
if (!sql.trimEnd().endsWith('SET FOREIGN_KEY_CHECKS = 1;')) {
  throw new Error('脚本末尾未恢复 FOREIGN_KEY_CHECKS');
}
if (sql.charCodeAt(0) === 0xfeff) throw new Error('文件不应包含 UTF-8 BOM');
if (sql.includes('\r')) throw new Error('文件应统一使用 LF');

console.log(`全量 SQL 静态校验通过，共 ${sql.split('\n').length} 行，${Buffer.byteLength(sql)} 字节。`);
