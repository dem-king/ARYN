import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

import {
  categoryPageAllowedComponents,
  detailPageAllowedComponents,
  userCenterAllowedComponents,
} from './component-registry';

/**
 * 页面类型组件白名单的跨端一致性契约。
 *
 * 同一份白名单存在两处独立实现：
 *  - 管理端 `component-registry.ts`（决定组件面板里能看到什么）
 *  - 后端 `PageDesignComponentTypes.java`（发布时阻断，是真正的安全边界）
 *
 * 两者必须严格一致：管理端多一个 → 运营能拖进去但发布被拒（体验断裂）；
 * 后端多一个 → 面板里没有但可通过接口塞进去（约束被绕过）。
 * 这里直接读后端源码比对，任一端增删组件都会失败。
 */

const backendConstants = readFileSync(
  resolve(
    process.cwd(),
    '../aryn-mall-java/aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/constant/PageDesignComponentTypes.java',
  ),
  'utf8',
);

/** 取 Java 侧某个 Set.of(...) 白名单的组件字面量。 */
function backendWhitelist(constantName: string): string[] {
  const start = backendConstants.indexOf(constantName);
  expect(start, `未找到后端常量 ${constantName}`).toBeGreaterThan(-1);
  const block = backendConstants.slice(
    start,
    backendConstants.indexOf(');', start),
  );
  // Java 侧写的是常量名，映射回字面量后与 TS 比对
  const constantMap: Record<string, string> = {};
  for (const match of backendConstants.matchAll(
    /String\s+([A-Z_]+)\s*=\s*"([a-z-]+)"/g,
  )) {
    constantMap[match[1] as string] = match[2] as string;
  }
  return [...block.matchAll(/\b([A-Z][A-Z_]+)\b/g)]
    .map((match) => constantMap[match[1] as string])
    .filter((value): value is string => value !== undefined)
    .sort();
}

function sorted(set: ReadonlySet<string>) {
  return [...set].sort();
}

describe('page type component whitelist parity: admin registry <-> backend validator', () => {
  it('detail page (pageType=2) matches', () => {
    expect(sorted(detailPageAllowedComponents)).toEqual(
      backendWhitelist('DETAIL_PAGE_ALLOWED_TYPES'),
    );
  });

  it('category page (pageType=3) matches', () => {
    expect(sorted(categoryPageAllowedComponents)).toEqual(
      backendWhitelist('CATEGORY_PAGE_ALLOWED_TYPES'),
    );
  });

  it('user center page (pageType=4) matches', () => {
    expect(sorted(userCenterAllowedComponents)).toEqual(
      backendWhitelist('USER_CENTER_ALLOWED_TYPES'),
    );
  });
});
