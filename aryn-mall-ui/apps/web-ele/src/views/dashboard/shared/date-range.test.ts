import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  resolveComparisonDateRange,
  resolveDateRange,
  resolveYearAgoDateRange,
} from './date-range';

const now = dayjs('2026-10-20 15:30:00');

describe('resolveDateRange', () => {
  it('uses seven calendar days including today', () => {
    expect(resolveDateRange('7d', now)).toEqual({
      startTime: '2026-10-14 00:00:00',
      endTime: '2026-10-20 15:30:00',
    });
  });

  it('uses thirty calendar days including today', () => {
    expect(resolveDateRange('30d', now)).toEqual({
      startTime: '2026-09-21 00:00:00',
      endTime: '2026-10-20 15:30:00',
    });
  });

  it('handles today and yesterday boundaries', () => {
    expect(resolveDateRange('today', now)).toEqual({
      startTime: '2026-10-20 00:00:00',
      endTime: '2026-10-20 15:30:00',
    });
    expect(resolveDateRange('yesterday', now)).toEqual({
      startTime: '2026-10-19 00:00:00',
      endTime: '2026-10-19 23:59:59',
    });
  });

  it('resolves a custom date range', () => {
    expect(
      resolveDateRange(
        {
          endDate: '2026-10-10',
          key: 'custom',
          startDate: '2026-10-01',
        },
        now,
      ),
    ).toEqual({
      startTime: '2026-10-01 00:00:00',
      endTime: '2026-10-10 23:59:59',
    });
  });

  it('falls back to thirty days for invalid custom ranges', () => {
    expect(
      resolveDateRange(
        {
          endDate: '2026-10-01',
          key: 'custom',
          startDate: '2026-10-10',
        },
        now,
      ),
    ).toEqual({
      startTime: '2026-09-21 00:00:00',
      endTime: '2026-10-20 15:30:00',
    });
  });
});

describe('resolveComparisonDateRange', () => {
  it('shifts preset ranges by calendar days', () => {
    expect(resolveComparisonDateRange('7d', now)).toEqual({
      startTime: '2026-10-07 00:00:00',
      endTime: '2026-10-13 15:30:00',
    });
    expect(resolveComparisonDateRange('today', now)).toEqual({
      startTime: '2026-10-19 00:00:00',
      endTime: '2026-10-19 15:30:00',
    });
  });

  it('shifts a custom range by its own day count', () => {
    expect(
      resolveComparisonDateRange(
        {
          endDate: '2026-10-10',
          key: 'custom',
          startDate: '2026-10-01',
        },
        now,
      ),
    ).toEqual({
      startTime: '2026-09-21 00:00:00',
      endTime: '2026-09-30 23:59:59',
    });
  });

  it('resolves the same range one year earlier', () => {
    expect(resolveYearAgoDateRange('7d', now)).toEqual({
      startTime: '2025-10-14 00:00:00',
      endTime: '2025-10-20 15:30:00',
    });
    expect(
      resolveYearAgoDateRange(
        {
          endDate: '2026-10-10',
          key: 'custom',
          startDate: '2026-10-01',
        },
        now,
      ),
    ).toEqual({
      startTime: '2025-10-01 00:00:00',
      endTime: '2025-10-10 23:59:59',
    });
  });
});
