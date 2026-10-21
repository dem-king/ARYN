import type { Dayjs } from 'dayjs';

import dayjs from 'dayjs';

const DATE_FORMAT = 'YYYY-MM-DD';
const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export interface DashboardDateRange {
  endTime: string;
  startTime: string;
}

export interface DashboardRangeInput {
  endDate?: string;
  key: string;
  startDate?: string;
}

function formatRange(start: Dayjs, end: Dayjs): DashboardDateRange {
  return {
    startTime: start.format(DATE_TIME_FORMAT),
    endTime: end.format(DATE_TIME_FORMAT),
  };
}

function resolvePreset(key: string, now: Dayjs): DashboardDateRange {
  switch (key) {
    case '7d': {
      return formatRange(now.startOf('day').subtract(6, 'day'), now);
    }
    case '30d': {
      return formatRange(now.startOf('day').subtract(29, 'day'), now);
    }
    case 'today': {
      return formatRange(now.startOf('day'), now);
    }
    case 'yesterday': {
      const yesterday = now.subtract(1, 'day');
      return formatRange(yesterday.startOf('day'), yesterday.endOf('day'));
    }
    default: {
      return formatRange(now.startOf('day').subtract(29, 'day'), now);
    }
  }
}

/**
 * 近 N 天包含今天，因此回退天数为 N - 1，避免“近 7 天”实际查到 8 天。
 */
export function resolveDateRange(
  input: DashboardRangeInput | string,
  now: Dayjs = dayjs(),
): DashboardDateRange {
  const range = typeof input === 'string' ? { key: input } : input;
  if (range.key === 'custom' && range.startDate && range.endDate) {
    const start = dayjs(range.startDate);
    const end = dayjs(range.endDate);
    if (
      start.isValid() &&
      end.isValid() &&
      !start.startOf('day').isAfter(end.startOf('day'))
    ) {
      return formatRange(start.startOf('day'), end.endOf('day'));
    }
  }
  return resolvePreset(range.key, now);
}

function resolveShiftDays(input: DashboardRangeInput | string): number {
  const range = typeof input === 'string' ? { key: input } : input;
  switch (range.key) {
    case '7d': {
      return 7;
    }
    case '30d': {
      return 30;
    }
    case 'custom': {
      if (range.startDate && range.endDate) {
        const start = dayjs(range.startDate);
        const end = dayjs(range.endDate);
        if (start.isValid() && end.isValid()) {
          return Math.max(
            1,
            end.startOf('day').diff(start.startOf('day'), 'day') + 1,
          );
        }
      }
      return 30;
    }
    case 'today':
    case 'yesterday': {
      return 1;
    }
    default: {
      return 30;
    }
  }
}

/**
 * 上一周期按自然日平移，保证“今日”对比昨日同一时刻，而不是向前推一段等长窗口。
 */
export function resolveComparisonDateRange(
  input: DashboardRangeInput | string,
  now: Dayjs = dayjs(),
): DashboardDateRange {
  const current = resolveDateRange(input, now);
  const shiftDays = resolveShiftDays(input);
  return {
    startTime: dayjs(current.startTime)
      .subtract(shiftDays, 'day')
      .format(DATE_TIME_FORMAT),
    endTime: dayjs(current.endTime)
      .subtract(shiftDays, 'day')
      .format(DATE_TIME_FORMAT),
  };
}

export function resolveYearAgoDateRange(
  input: DashboardRangeInput | string,
  now: Dayjs = dayjs(),
): DashboardDateRange {
  const current = resolveDateRange(input, now);
  return {
    startTime: dayjs(current.startTime)
      .subtract(1, 'year')
      .format(DATE_TIME_FORMAT),
    endTime: dayjs(current.endTime)
      .subtract(1, 'year')
      .format(DATE_TIME_FORMAT),
  };
}

export { DATE_FORMAT, DATE_TIME_FORMAT };
