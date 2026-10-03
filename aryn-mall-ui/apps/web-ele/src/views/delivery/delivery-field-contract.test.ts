import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * 管理端配送模块的列字段必须存在于后端实体。
 *
 * 背景：2026-10-01 现场报「配送任务配送员没有显示，收货地址也没有显示」。根因是
 * 管理端列的 `prop` 引用了后端根本不存在的字段（`staffName` 在实体里没有，
 * 省市区在 `delivery_task` 表里也没有），而 Element Plus 对未知 `prop`
 * 不报错也不告警 —— 列恒为空，类型检查、构建、单测全绿。
 *
 * 本测试把「管理端引用的字段」与「后端实体/派生字段」对齐，静态拦住这类静默空白。
 * 后端 `delivery_task`/`delivery_trip` 无手写 Mapper XML（全 MyBatis-Plus 注解），
 * 因此实体字段名即 DB 列名，读 Java 源码即可判定。
 */
const here = dirname(fileURLToPath(import.meta.url));
/** apps/web-ele/src/views/delivery → ARYN 仓库根（java 与 ui 是同级子项目） */
const repoRoot = resolve(here, '../../../../../..');

const ENTITY_FILES: Record<string, string> = {
  DeliveryTask:
    'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/entity/DeliveryTask.java',
  DeliveryTrip:
    'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/entity/DeliveryTrip.java',
  DeliveryTaskItem:
    'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/entity/DeliveryTaskItem.java',
  DeliveryPickupSummaryVO:
    'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/DeliveryPickupSummaryVO.java',
};

/** 后端按 staffId 关联查询后回填的派生字段，实体上带 @TableField(exist = false) */
const DERIVED_FIELDS = new Set(['staffName']);

/** 后端不落库、由服务层聚合出的出车单摘要字段 */
const TRIP_DERIVED_FIELDS = new Set([
  'arrivedTaskCount',
  'pickedItemCount',
  'pickupSummary',
  'staffName',
  'taskList',
  'totalItemCount',
  'warehouseName',
]);

function fieldsOf(entity: string): Set<string> {
  const relativePath = ENTITY_FILES[entity];
  if (!relativePath) {
    throw new Error(`未登记实体源码路径：${entity}`);
  }
  const text = readFileSync(join(repoRoot, relativePath), 'utf8');
  const names = new Set<string>();
  // 只取字段声明行：以访问修饰符开头、以分号结尾，避免把方法签名当字段
  for (const line of text.split('\n')) {
    const match = /^\s*private\s+[\w.<>[\]]+\s+(\w+)\s*;/.exec(line);
    if (match?.[1]) names.add(match[1]);
  }
  return names;
}

/** 提取模板里全部字面量 prop="xxx" */
function propsOf(vuePath: string): string[] {
  const text = readFileSync(join(here, vuePath), 'utf8');
  const found: string[] = [];
  for (const match of text.matchAll(/\sprop="([A-Za-z]\w*)"/g)) {
    if (match[1]) found.push(match[1]);
  }
  return found;
}

describe('管理端配送页面列字段契约', () => {
  const taskFields = fieldsOf('DeliveryTask');
  const tripFields = fieldsOf('DeliveryTrip');
  const itemFields = fieldsOf('DeliveryTaskItem');
  const pickupSummaryFields = fieldsOf('DeliveryPickupSummaryVO');

  it('实体字段解析可用（解析失败时整个测试失去意义，必须先失败）', () => {
    expect(taskFields.size).toBeGreaterThan(10);
    expect(tripFields.size).toBeGreaterThan(5);
    expect(itemFields.size).toBeGreaterThan(5);
    expect(pickupSummaryFields.size).toBeGreaterThan(2);
    // 这几个字段是本轮修复的核心，缺少说明实体被改回旧结构
    expect(taskFields).toContain('recipientAddress');
    expect(taskFields).toContain('staffId');
    expect(tripFields).toContain('taskCount');
    expect(tripFields).toContain('startLoadTime');
    expect(tripFields).toContain('completeTime');
  });

  it('配送任务列表的列字段都存在于 DeliveryTask', () => {
    const known = new Set([...DERIVED_FIELDS, ...taskFields]);
    const unknown = propsOf('task/index.vue').filter((p) => !known.has(p));
    expect(unknown).toEqual([]);
  });

  it('配送任务详情的列字段都存在于 DeliveryTask 或 DeliveryTaskItem', () => {
    const known = new Set([
      'itemList',
      ...DERIVED_FIELDS,
      ...itemFields,
      ...taskFields,
    ]);
    const unknown = propsOf('task/task-detail.vue').filter(
      (p) => !known.has(p),
    );
    expect(unknown).toEqual([]);
  });

  it('出车单列表的列字段都存在于 DeliveryTrip', () => {
    const known = new Set([...TRIP_DERIVED_FIELDS, ...tripFields]);
    const unknown = propsOf('trip/index.vue').filter((p) => !known.has(p));
    expect(unknown).toEqual([]);
  });

  it('出车单详情引用的任务行与汇总行字段都存在于后端契约', () => {
    const known = new Set([
      'itemList',
      ...DERIVED_FIELDS,
      ...itemFields,
      ...pickupSummaryFields,
      ...taskFields,
    ]);
    const unknown = propsOf('trip/trip-detail.vue').filter(
      (p) => !known.has(p),
    );
    expect(unknown).toEqual([]);
  });
});
