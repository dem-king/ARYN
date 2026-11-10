import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const readApp = (path: string) => readFileSync(resolve(appRoot, path), 'utf8');

const groupPage = readApp('src/views/upms/material-group/index.vue');
const imageTab = readApp('src/views/upms/material/image-tab/index.vue');

/**
 * 「未分组」曾是前端 unshift 进列表的虚拟行（id='-1'），
 * 因为库里没有这条记录，改名/删除都会被刻意隐藏。
 * 现在默认分组由后端 is_default='1' 落库并排在第一，页面不得再伪造任何分组行。
 */
describe('material default group contract', () => {
  it('no longer injects a virtual ungrouped row', () => {
    // 虚拟行注入会让分页总数与表格行数相差 1，也让默认分组不可维护
    expect(groupPage).not.toContain("unshift({ name: '未分组'");
    expect(imageTab).not.toContain("unshift({ id: '-1'");
    expect(groupPage).not.toContain("'-1'");
    expect(imageTab).not.toContain("'-1'");
  });

  it('renders default group actions from backend isDefault flag', () => {
    // 改名保留，删除隐藏，并给出「默认」标签，让不可删除是可解释的
    expect(groupPage).toContain("String(row?.isDefault) === '1'");
    expect(groupPage).toContain('默认分组不可删除');
    expect(groupPage).toContain('v-if="!isDefaultGroup(scope.row)"');
  });

  it('selects the default group returned by backend instead of a hardcoded id', () => {
    expect(imageTab).toContain("String(item.isDefault) === '1'");
    expect(imageTab).toContain('state.queryParams.groupId =');
    // 分组未就绪时不应带着空分组去查素材
    expect(imageTab).toContain('if (!state.queryParams.groupId)');
  });

  it('validates group name against the column length before submitting', () => {
    expect(groupPage).toContain('GROUP_NAME_MAX_LENGTH');
    expect(groupPage).toContain('inputValidator');
    expect(imageTab).toContain('GROUP_NAME_MAX_LENGTH');
  });
});
