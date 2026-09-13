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
  'CREATE TABLE `message_notice`',
  'CREATE TABLE `message_conversation`',
  'CREATE TABLE `message_chat`',
  "'message:notice:publish'",
  "'message:service:agent'",
  "'message:service:supervisor'",
  "'message:conversation:initiate'",
  "'message:staff:direct'",
  "'message/inbox/index'",
  "'message/notice/index'",
  "'message/service/index'",
  "'message/direct/index'",
  'CREATE TABLE `product_refund_stock_record`',
  'CREATE TABLE IF NOT EXISTS goods_brand',
  'ADD COLUMN brand_id',
  "'product:goodsbrand:page'",
  'ADD COLUMN `member_level_id`',
  'ADD COLUMN page_type',
  'CREATE TABLE page_design_version',
  'CREATE TABLE page_design_template',
  'CREATE TABLE IF NOT EXISTS `page_design_release`',
  'CREATE TABLE IF NOT EXISTS `page_design_audit_log`',
  'CREATE TABLE IF NOT EXISTS `page_design_theme`',
  'CREATE TABLE IF NOT EXISTS `page_design_release_target`',
  'CREATE TABLE IF NOT EXISTS `page_design_metric_daily`',
  "'promotion:pagedesign:publish'",
  "'promotion:pagedesign:rollback'",
  "'promotion:pagedesign:template'",
  "'promotion:pagedesign:submit'",
  "'promotion:pagedesign:approve'",
  "'promotion:pagedesign:audit'",
  "'promotion:pagedesign:theme'",
  'INSERT INTO page_design_version',
  'CREATE TABLE `xxl_job_info`',
  'CREATE TABLE `seckill_activity`',
  'CREATE TABLE `seckill_session`',
  'CREATE TABLE `seckill_goods`',
  'CREATE TABLE `seckill_order`',
  'CREATE TABLE `discount_activity`',
  'CREATE TABLE `discount_goods`',
  "'promotion:seckill:page'",
  'CREATE TABLE `delivery_staff`',
  'CREATE TABLE `delivery_trip`',
  'CREATE TABLE `delivery_task`',
  'CREATE TABLE `delivery_task_item`',
  'CREATE TABLE `delivery_warehouse_config`',
  'CREATE TABLE `delivery_task_log`',
  'CREATE TABLE `delivery_evidence`',
  'CREATE TABLE `delivery_area`',
  'CREATE TABLE `sys_user_wechat_binding`',
  'CREATE TABLE IF NOT EXISTS `delivery_account_binding`',
  'uk_delivery_binding_mall_user',
  'uk_delivery_binding_active_sys_user',
  "'delivery:execute'",
  "'delivery:staff:bind'",
  "'delivery:staff:qualification'",
  "'delivery:staff:availability'",
  "'delivery_staff'",
  "'delivery:staff:get'",
  "'delivery:trip:get'",
  'delivery_way',
  'CREATE TABLE `distribution_refund_record`',
  'uk_user_tag_tenant',
  'CREATE TABLE `member_order_growth`',
  'uk_order_request',
  'active_order_item_id',
  'style_access_enabled',
  '1928797196747186177',
  'uk_pay_trade_order_no',
  'uk_pay_refund_order_no',
  'CREATE TABLE IF NOT EXISTS `vessel_info`',
  'CREATE TABLE IF NOT EXISTS `vessel_member`',
  'CREATE TABLE IF NOT EXISTS `vessel_call`',
  'CREATE TABLE IF NOT EXISTS `ship_goods_profile`',
  'CREATE TABLE IF NOT EXISTS `ship_sku_profile`',
  'CREATE TABLE IF NOT EXISTS `product_code_mapping`',
  'CREATE TABLE IF NOT EXISTS `shared_cart`',
  'CREATE TABLE IF NOT EXISTS `shared_cart_member`',
  'CREATE TABLE IF NOT EXISTS `shared_cart_item`',
  'CREATE TABLE IF NOT EXISTS `fulfillment_wave`',
  'CREATE TABLE IF NOT EXISTS `fulfillment_pick_item`',
  'CREATE TABLE IF NOT EXISTS `fulfillment_exception`',
  'CREATE TABLE IF NOT EXISTS `promotion_snapshot`',
  'CREATE TABLE IF NOT EXISTS `product_import_job`',
  'CREATE TABLE IF NOT EXISTS `product_import_error`',
  'CREATE TABLE IF NOT EXISTS `product_change_log`',
  'CREATE TABLE IF NOT EXISTS `product_import_row`',
  '2103000000000000004',
  '2110000000000000000',
  'vessel:vessel:page',
  'fulfillment:wave:pick',
  'product:import:confirm',
  '9610000000000000001',
  'CREATE TABLE IF NOT EXISTS `promotion_activity`',
  'CREATE TABLE IF NOT EXISTS `promotion_lock`',
  'uk_promotion_lock_order_activity',
  'CREATE TABLE IF NOT EXISTS `vessel_call_change_log`',
  'uk_vessel_member_unique',
  'uk_ship_goods_profile_spu',
  'uk_ship_sku_profile_sku',
  'uk_product_code_mapping_code',
  'idx_order_info_scene',
  'uk_fulfillment_pick_item',
];

for (const token of required) {
  if (!sql.includes(token)) throw new Error(`缺少关键 SQL: ${token}`);
}

const sourceFiles = [
  '1schema.sql',
  '2aryn_boot.sql',
  '19product_brand.sql',
  '41grocery_catalog_seed.sql',
  '4aryn_boot_member.sql',
  '20message_center.sql',
  '20message_menu.sql',
  '15menu_seed_repair.sql',
  '10page_design_alter.sql',
  '11page_design_publish.sql',
  '30page_design_release_audit.sql',
  '32page_design_theme.sql',
  '33page_design_phase4.sql',
  '25seckill_discount.sql',
  '26seckill_discount_menu.sql',
  '25delivery_module.sql',
  '27delivery_fulfillment_incremental.sql',
  '29delivery_account_binding.sql',
  '42vessel_context_incremental.sql',
  '43ship_product_profile_incremental.sql',
  '44order_delivery_context_incremental.sql',
  '48product_import_incremental.sql',
  '50product_import_row_incremental.sql',
  '45ship_supply_menu_permission.sql',
  '46ship_supply_seed_pilot.sql',
  '53promotion_ship_supply_incremental.sql',
  '55promotion_ship_menu.sql',
  '56vessel_call_change_log.sql',
  '57shared_cart_admin_menu.sql',
  '58shopping_cart_vessel_incremental.sql',
  '59gift_activity_incremental.sql',
  '3aryn_boot_job.sql',
];
for (const file of sourceFiles) {
  if (!sql.includes(`Source: db/boot/${file}`)) throw new Error(`缺少来源段: ${file}`);
}

// Boot 与 Cloud 同名增量脚本必须语义一致（42/43/44）：
// 剔除注释、USE、CREATE DATABASE 等模式差异行后，剩余语句应完全一致。
for (const pairFile of [
  '42vessel_context_incremental.sql',
  '43ship_product_profile_incremental.sql',
  '44order_delivery_context_incremental.sql',
  '48product_import_incremental.sql',
  '50product_import_row_incremental.sql',
]) {
  const semantic = (content) => content
    .split('\n')
    .filter((line) => {
      const trimmed = line.trim();
      if (trimmed.startsWith('--')) return false;
      if (/^USE\s/i.test(trimmed)) return false;
      if (/^CREATE DATABASE\s/i.test(trimmed)) return false;
      return true;
    })
    .join('\n')
    .replace(/\s+/g, ' ')
    .trim();
  const bootContent = semantic(readFileSync(join(root, pairFile), 'utf8'));
  const cloudPath = join(root, '..', 'cloud', pairFile);
  let cloudContent;
  try {
    cloudContent = semantic(readFileSync(cloudPath, 'utf8'));
  } catch {
    throw new Error(`缺少 Cloud 增量脚本: db/cloud/${pairFile}`);
  }
  if (bootContent !== cloudContent) {
    throw new Error(`Boot/Cloud 增量脚本不一致: db/boot/${pairFile} 与 db/cloud/${pairFile}`);
  }
}

// 12/13/14/16/17 号增量的变更已并入 2aryn_boot.sql 基线，且脚本本身是无幂等守卫的裸
// ALTER，不参与全量合并；若被重复合并，全量脚本会在这些 ALTER 上执行报错。
for (const mergedFile of [
  '12tenant_lookup_uniqueness.sql',
  '13distribution_financial_hardening.sql',
  '14member_management_hardening.sql',
  '16order_checkout_hardening.sql',
  '17order_appraise_hardening.sql',
]) {
  if (sql.includes(`Source: db/boot/${mergedFile}`)) {
    throw new Error(`不应重复合并 ${mergedFile}（变更已包含在基础库 dump 中）`);
  }
}

// 27delivery_fulfillment_incremental.sql 用 CREATE TABLE IF NOT EXISTS 兜底存量库，
// 在全量脚本里是空操作，不计入重复与总数。
const plainCreates = [...sql.matchAll(/^CREATE TABLE\s+(?!IF NOT EXISTS)`?([^` (]+)/gim)]
  .map((match) => match[1]);
const createTables = [
  ...plainCreates,
  ...[...sql.matchAll(/^CREATE TABLE IF NOT EXISTS\s+`?([^` (]+)/gim)].map((match) => match[1]),
];
const dropTables = new Set(
  [...sql.matchAll(/^DROP TABLE IF EXISTS\s+`?([^`; ]+)/gim)].map((match) => match[1]),
);
const duplicateTables = plainCreates.filter((table, index) => plainCreates.indexOf(table) !== index);
if (duplicateTables.length > 0) {
  throw new Error(`存在重复建表: ${[...new Set(duplicateTables)].join(', ')}`);
}
const missingDrops = createTables.filter((table) => !dropTables.has(table));
if (missingDrops.length > 0) {
  throw new Error(`全量脚本缺少 DROP TABLE: ${missingDrops.join(', ')}`);
}
if (plainCreates.length !== 103) {
  throw new Error(`建表数量异常，期望 103，实际 ${plainCreates.length}`);
}
if (!sql.trimEnd().endsWith('SET FOREIGN_KEY_CHECKS = 1;')) {
  throw new Error('脚本末尾未恢复 FOREIGN_KEY_CHECKS');
}
if (sql.charCodeAt(0) === 0xfeff) throw new Error('文件不应包含 UTF-8 BOM');
if (sql.includes('\r')) throw new Error('文件应统一使用 LF');

console.log(`全量 SQL 静态校验通过，共 ${sql.split('\n').length} 行，${Buffer.byteLength(sql)} 字节。`);
