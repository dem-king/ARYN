import { requestClient } from '#/api/request';

export interface FirstOrderReportRow {
  activityId: string;
  activityName: string;
  subjectType: string;
  lockedCount: number;
  confirmedCount: number;
  releasedCount: number;
  subjectCount: number;
  discountAmount: number;
  monthlyBudget: number;
  monthlyUsed: number;
  usageRate?: number;
  alert?: boolean;
}

export interface FirstOrderReport {
  beginTime: string;
  endTime: string;
  lockedCount: number;
  confirmedCount: number;
  releasedCount: number;
  discountAmount: number;
  rows: FirstOrderReportRow[];
}

export async function getFirstOrderReport(params: {
  beginTime?: string;
  endTime?: string;
}) {
  return requestClient.get<FirstOrderReport>(
    '/promotion/first-order-report/page',
    { params },
  );
}

export async function getFirstOrderAlerts(params: {
  beginTime?: string;
  endTime?: string;
}) {
  return requestClient.get<FirstOrderReportRow[]>(
    '/promotion/first-order-report/alerts',
    { params },
  );
}
