import type { DashboardRangeInput } from './date-range';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import dayjs from 'dayjs';

import {
  resolveComparisonDateRange,
  resolveDateRange,
  resolveYearAgoDateRange,
} from './date-range';

const RANGE_KEYS = new Set(['7d', '30d', 'custom', 'today', 'yesterday']);

export type CompareMode = 'previous' | 'year';

export function useDashboardRange(defaultKey = '30d') {
  const route = useRoute();
  const router = useRouter();

  const read = (key: string) => {
    const raw = route.query[key];
    const value = Array.isArray(raw) ? raw[0] : raw;
    return typeof value === 'string' ? value : '';
  };

  const initialKey = read('range');
  const rangeKey = ref(RANGE_KEYS.has(initialKey) ? initialKey : defaultKey);
  const customStart = ref(read('start'));
  const customEnd = ref(read('end'));
  const compare = ref(read('compare') !== '0');
  const compareMode = ref<CompareMode>(
    read('compareMode') === 'year' ? 'year' : 'previous',
  );

  if (rangeKey.value === 'custom' && (!customStart.value || !customEnd.value)) {
    const end = dayjs();
    customStart.value = end.subtract(6, 'day').format('YYYY-MM-DD');
    customEnd.value = end.format('YYYY-MM-DD');
  }

  const rangeInput = computed<DashboardRangeInput>(() => ({
    endDate: customEnd.value,
    key: rangeKey.value,
    startDate: customStart.value,
  }));
  const currentRange = computed(() => resolveDateRange(rangeInput.value));
  const previousRange = computed(() =>
    compareMode.value === 'year'
      ? resolveYearAgoDateRange(rangeInput.value)
      : resolveComparisonDateRange(rangeInput.value),
  );

  const syncQuery = () => {
    const query = { ...route.query };
    query.range = rangeKey.value;
    if (rangeKey.value === 'custom') {
      query.start = customStart.value;
      query.end = customEnd.value;
    } else {
      delete query.start;
      delete query.end;
    }
    if (compare.value) {
      delete query.compare;
    } else {
      query.compare = '0';
    }
    if (compareMode.value === 'year') {
      query.compareMode = 'year';
    } else {
      delete query.compareMode;
    }
    void router.replace({ path: route.path, query });
  };

  watch([rangeKey, customStart, customEnd, compare, compareMode], syncQuery);

  // 浏览器前进/后退或外部链接再次进入时，以 URL 为准恢复筛选。
  watch(
    () => [
      route.query.range,
      route.query.start,
      route.query.end,
      route.query.compare,
      route.query.compareMode,
    ],
    () => {
      const nextKeyRaw = read('range');
      const nextKey = RANGE_KEYS.has(nextKeyRaw) ? nextKeyRaw : defaultKey;
      if (nextKey !== rangeKey.value) {
        rangeKey.value = nextKey;
      }
      if (nextKey === 'custom') {
        const nextStart = read('start');
        const nextEnd = read('end');
        if (nextStart && nextEnd) {
          if (nextStart !== customStart.value) {
            customStart.value = nextStart;
          }
          if (nextEnd !== customEnd.value) {
            customEnd.value = nextEnd;
          }
        } else if (!customStart.value || !customEnd.value) {
          const end = dayjs();
          customStart.value = end.subtract(6, 'day').format('YYYY-MM-DD');
          customEnd.value = end.format('YYYY-MM-DD');
        }
      }
      const nextCompare = read('compare') !== '0';
      if (nextCompare !== compare.value) {
        compare.value = nextCompare;
      }
      const nextCompareMode: CompareMode =
        read('compareMode') === 'year' ? 'year' : 'previous';
      if (nextCompareMode !== compareMode.value) {
        compareMode.value = nextCompareMode;
      }
    },
  );

  syncQuery();

  return {
    compare,
    compareMode,
    currentRange,
    customEnd,
    customStart,
    previousRange,
    rangeKey,
  };
}
