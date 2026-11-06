import type { Ref } from 'vue';

import type {
  ConversationFilterState,
  ServiceTab,
  ServiceTabOption,
} from './service-state';

import type { Conversation } from '#/api/message/types';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import {
  getAdminConversations,
  getStaffConversations,
  getWaitingConversations,
} from '#/api/message/conversation';

import {
  buildConversationInboxParams,
  conversationDisplayName,
  conversationFilterTags,
  conversationQuery,
  createConversationFilterState,
  visibleServiceTabs,
} from './service-state';

/** 列表单页条数：历史/主管页签用 cursor 加载更多。 */
const PAGE_SIZE = 20;
/** 服务端搜索防抖：避免每敲一个字都重置游标打一次接口。 */
const SEARCH_DEBOUNCE_MS = 300;

export interface UseDeskConversationsOptions {
  /** 当前打开的会话：列表刷新后用最新快照覆盖（未读/状态保持新鲜）。 */
  active: Ref<Conversation | undefined>;
  /** 是否主管：决定是否出现「全部会话」页签。 */
  hasSupervisor: Ref<boolean>;
  /** 每次列表加载成功后回调（如刷新队列统计），失败不触发。 */
  onLoaded?: () => void;
}

/**
 * 客服工作台会话列表（页签 / 游标分页 / 服务端搜索 / 精细筛选）。
 *
 * 请求代际用于防慢网连点：只认最后一次请求的结果，旧响应直接丢弃。
 * 搜索/筛选变化必须重置游标（`loadConversations(true)`），翻页时原样复用
 * 同一组筛选参数（契约 §2.3-7：cursor 不与筛选条件绑定，由前端负责）。
 */
export function useDeskConversations(options: UseDeskConversationsOptions) {
  const activeTab = ref<ServiceTab>('mine');
  const conversations = ref<Conversation[]>([]);
  const listLoading = ref(false);
  const listFailed = ref(false);
  const nextCursor = ref<string>();
  const hasMore = ref(false);
  /** 搜索框输入值：待领取页签用于本地过滤，其它页签经防抖后作为服务端 keyword。 */
  const conversationKeyword = ref('');
  /** 已生效的服务端 keyword：与输入框解耦，避免防抖期间翻页用错参数。 */
  const appliedKeyword = ref('');
  const filters = ref<ConversationFilterState>(createConversationFilterState());
  let keywordTimer: ReturnType<typeof setTimeout> | undefined;
  let listGeneration = 0;

  const tabs = computed<ServiceTabOption[]>(() =>
    visibleServiceTabs(options.hasSupervisor.value),
  );

  const filterTags = computed(() => conversationFilterTags(filters.value));
  const hasActiveFilters = computed(() => filterTags.value.length > 0);
  /** 是否处于「搜索/筛选」态：用于空态文案区分「暂无会话」与「无匹配」。 */
  const hasActiveQuery = computed(() => {
    if (hasActiveFilters.value) return true;
    const keyword =
      activeTab.value === 'waiting'
        ? conversationKeyword.value
        : appliedKeyword.value;
    return keyword.trim().length > 0;
  });

  /**
   * 列表展示数据：非待领取页签由服务端完成 keyword/筛选，直接展示；
   * 待领取页签（`/waiting` 不支持 keyword，契约 §2.3-9）保持本地过滤。
   */
  const filteredConversations = computed(() => {
    if (activeTab.value !== 'waiting') return conversations.value;
    const keyword = conversationKeyword.value.trim().toLowerCase();
    if (!keyword) return conversations.value;
    return conversations.value.filter((item) => {
      const name = conversationDisplayName(item).toLowerCase();
      const summary = (item.lastMessageSummary ?? '').toLowerCase();
      const customerId = (item.customerId ?? '').toLowerCase();
      return (
        name.includes(keyword) ||
        summary.includes(keyword) ||
        customerId.includes(keyword)
      );
    });
  });

  function clearKeywordTimer() {
    if (keywordTimer) {
      clearTimeout(keywordTimer);
      keywordTimer = undefined;
    }
  }

  /** 应用服务端搜索（只在 keyword 真正变化时重新拉列表）。 */
  function applyServerKeyword(value: string) {
    const next = value.trim();
    if (next === appliedKeyword.value) return;
    appliedKeyword.value = next;
    void loadConversations(true);
  }

  watch(conversationKeyword, (value) => {
    // 待领取页签是本地过滤：输入即时生效，不发服务端搜索
    if (activeTab.value === 'waiting') return;
    clearKeywordTimer();
    keywordTimer = setTimeout(() => {
      keywordTimer = undefined;
      applyServerKeyword(value);
    }, SEARCH_DEBOUNCE_MS);
  });

  async function loadConversations(reset = true) {
    const generation = ++listGeneration;
    if (reset) {
      nextCursor.value = undefined;
      hasMore.value = false;
    }
    listLoading.value = true;
    try {
      if (activeTab.value === 'waiting') {
        const records = await getWaitingConversations({
          limit: 50,
          queueCode: 'DEFAULT',
        });
        if (generation !== listGeneration) return;
        conversations.value = reset
          ? records
          : [...conversations.value, ...records];
        hasMore.value = false;
      } else {
        const cursor = reset ? undefined : nextCursor.value;
        const params = buildConversationInboxParams({
          cursor,
          filters: filters.value,
          keyword: appliedKeyword.value,
          limit: PAGE_SIZE,
          status: conversationQuery(activeTab.value).status,
        });
        const page =
          activeTab.value === 'supervisor'
            ? await getAdminConversations(params)
            : await getStaffConversations(params);
        if (generation !== listGeneration) return;
        conversations.value = reset
          ? page.records
          : [...conversations.value, ...page.records];
        nextCursor.value = page.nextCursor;
        hasMore.value = page.hasMore === true && !!page.nextCursor;
      }
      listFailed.value = false;
      // 列表刷新后同步当前会话的未读/状态快照（消息体仍以时间线为准）。
      if (options.active.value) {
        const fresh = conversations.value.find(
          (item) => item.id === options.active.value?.id,
        );
        if (fresh) options.active.value = fresh;
      }
      options.onLoaded?.();
    } catch {
      if (generation !== listGeneration) return;
      if (reset) {
        conversations.value = [];
        listFailed.value = true;
      }
    } finally {
      if (generation === listGeneration) listLoading.value = false;
    }
  }

  async function loadMore() {
    if (listLoading.value || !hasMore.value) return;
    await loadConversations(false);
  }

  async function handleTabChange(tab: ServiceTab) {
    activeTab.value = tab;
    // 切页签立即结算搜索：取消待触发防抖，避免旧 keyword 的延迟请求覆盖新页签列表
    clearKeywordTimer();
    appliedKeyword.value =
      tab === 'waiting' ? '' : conversationKeyword.value.trim();
    await loadConversations(true);
  }

  /** 精细筛选变化：合并补丁并重置游标重新拉列表（契约 §9.3）。 */
  function updateFilters(patch: Partial<ConversationFilterState>) {
    filters.value = { ...filters.value, ...patch };
    void loadConversations(true);
  }

  function clearFilters() {
    filters.value = createConversationFilterState();
    void loadConversations(true);
  }

  /** 清空搜索 + 筛选（空态「清除条件」入口），只触发一次列表请求。 */
  function resetQuery() {
    clearKeywordTimer();
    conversationKeyword.value = '';
    appliedKeyword.value = '';
    filters.value = createConversationFilterState();
    void loadConversations(true);
  }

  onBeforeUnmount(clearKeywordTimer);

  return {
    activeTab,
    appliedKeyword,
    clearFilters,
    conversationKeyword,
    conversations,
    filteredConversations,
    filterTags,
    filters,
    handleTabChange,
    hasActiveFilters,
    hasActiveQuery,
    hasMore,
    listFailed,
    listLoading,
    loadConversations,
    loadMore,
    nextCursor,
    resetQuery,
    tabs,
    updateFilters,
  };
}
