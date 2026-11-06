import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { mount } from '@vue/test-utils';

import { ElLoading } from 'element-plus';
import { describe, expect, it } from 'vitest';

import ConversationRail from './conversation-rail.vue';
import {
  createConversationFilterState,
  visibleServiceTabs,
} from './service-state';

/**
 * 会话栏品牌区布局契约。
 *
 * <p>回归场景：`.brand-actions` 曾是 `flex: none`，「客服调度台」+ 3 个按钮在
 * 330px 栏宽里放不进同一行，收缩压力全落在 `.desk-brand-copy` 上 —— 17px 标题
 * 被压成一行一个字并纵向溢出，直接叠在「独立窗口」按钮上（用户截图缺陷）。</p>
 *
 * <p>happy-dom 不做真实布局计算，因此这里同时守两件事：
 * 1）样式里的布局意图必须显式（品牌占整行、按钮另起一行、按钮不收缩）；
 * 2）组件确实渲染出品牌文案与三个入口（含独立窗口模式隐藏「独立窗口」按钮）。</p>
 */
const railSource = readFileSync(
  resolve(
    process.cwd(),
    'apps/web-ele/src/views/message/service/conversation-rail.vue',
  ),
  'utf8',
);

/** 取 `<style scoped>` 中某个选择器块的声明（按 `}` 截断）。 */
function styleBlock(selector: string): string {
  const start = railSource.indexOf(`${selector} {`);
  expect(
    start,
    `样式里缺少 ${selector} 规则，品牌区布局契约被破坏`,
  ).toBeGreaterThan(-1);
  return railSource.slice(start, railSource.indexOf('}', start) + 1);
}

const railProps = {
  activeTab: 'mine' as const,
  assigneeLoading: false,
  assigneeOptions: [],
  autoAccept: false,
  canFilterAssignee: false,
  configured: true,
  conversationKeyword: '',
  conversations: [],
  enabled: true,
  filteredConversations: [],
  filters: createConversationFilterState(),
  filterTags: [],
  hasActiveFilters: false,
  hasActiveQuery: false,
  hasMore: false,
  isWindowMode: false,
  listFailed: false,
  listLoading: false,
  presence: 'ONLINE' as const,
  presenceCapacity: '',
  presenceOptions: [],
  queueStatsDegraded: false,
  railHidden: false,
  syncing: false,
  tabCounts: { history: 0, mine: 0, supervisor: 0, waiting: 0 },
  tabs: visibleServiceTabs(false),
};

function mountRail(isWindowMode = false) {
  return mount(ConversationRail, {
    props: { ...railProps, isWindowMode },
    // v-loading 由 element-plus 提供、v-access 由应用注册；单测里补上以消除告警
    global: { directives: { access: () => {}, loading: ElLoading.directive } },
  });
}

describe('conversation rail brand layout', () => {
  it('keeps brand copy and the action buttons on separate rows', () => {
    // 品牌文案占满首行，按钮因此必然换到下一行：两者不再并排抢宽度
    expect(styleBlock('.desk-brand')).toContain('flex-wrap: wrap');
    expect(styleBlock('.desk-brand-copy')).toContain('flex: 1 1 100%');
    expect(styleBlock('.brand-actions')).toContain('flex: 1 1 100%');
    expect(styleBlock('.brand-actions')).toContain('justify-content: flex-end');
  });

  it('never lets buttons or the brand title collapse into a vertical column', () => {
    // 按钮自身不收缩：否则文字被压成竖排，正是截图里「独立窗口」贴着标题的原因
    const triggers = styleBlock('.config-trigger,\n.ghost-trigger');
    expect(triggers).toContain('flex: none');

    // 标题/副标题禁止逐字折行，溢出走省略号
    const title = styleBlock('.desk-brand strong');
    expect(title).toContain('white-space: nowrap');
    expect(title).toContain('text-overflow: ellipsis');
    expect(styleBlock('.desk-brand-copy > div')).toContain('min-width: 0');
  });

  it('keeps the presence label on one piece instead of breaking 自动接待 in half', () => {
    // 同一张截图里「自动接待」被折断成「自动接/待」：标签整体不拆字
    expect(styleBlock('.presence-row > span')).toContain('white-space: nowrap');
    // 换行后状态选择框仍贴右（与首行对齐）
    expect(styleBlock('.presence-select')).toContain('margin-left: auto');
    expect(styleBlock('.presence-row')).toContain('flex-wrap: wrap');
  });

  it('renders brand copy plus the three entries, hiding only 独立窗口 in window mode', () => {
    const embedded = mountRail();
    expect(embedded.find('.desk-brand strong').text()).toBe('客服调度台');
    expect(embedded.find('.desk-brand small').text()).toBe(
      'SHARED SERVICE POOL',
    );
    expect(embedded.find('.window-trigger').exists()).toBe(true);
    embedded.unmount();

    // 独立窗口自身没有「弹出独立窗口」入口，但品牌与另外两个入口仍在
    const standalone = mountRail(true);
    expect(standalone.find('.desk-brand strong').text()).toBe('客服调度台');
    expect(standalone.find('.window-trigger').exists()).toBe(false);
    expect(standalone.findAll('.brand-actions button')).toHaveLength(2);
    standalone.unmount();
  });
});
