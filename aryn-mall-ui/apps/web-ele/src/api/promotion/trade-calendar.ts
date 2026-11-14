import { requestClient } from '#/api/request';

/** 采购档期（C 端展示 VO，与 uniapp AppCalendar 同构） */
export interface AppCalendar {
  /** 锚点类型：1周度 2靠港 3自然月 4自定义 */
  anchorType: string;
  /** 档期结束 */
  endTime: string;
  /** 档期 ID */
  id: string;
  /** 档期名称 */
  calendarName: string;
  /** 档期开始 */
  startTime: string;
}

/** 档期特供商品项（与 uniapp CalendarSpecialItem 同构） */
export interface AppCalendarSpecialItem {
  /** 当前售价（划线基准；远程降级时为 null） */
  originalPrice?: null | number;
  /** 每人限购件数（0/null=不限） */
  perUserLimit?: null | number;
  /** 特供价 */
  price: number;
  /** SKU ID */
  skuId: string;
}

/** 档期特供活动（与 uniapp AppCalendarSpecial 同构） */
export interface AppCalendarSpecial {
  activityId: string;
  activityName: string;
  calendarId?: null | string;
  calendarName?: null | string;
  endTime: string;
  /** 剩余限量（件数；totalQuota 为 null 时不限量） */
  remainingQuota?: null | number;
  /** 活动级限量（件数；null=不限） */
  totalQuota?: null | number;
  items: AppCalendarSpecialItem[];
}

/** 采购档期列表 */
export async function getTradeCalendars(params?: any) {
  return requestClient.get('/promotion/trade-calendar/list', { params });
}

/** 档期详情 */
export async function getTradeCalendarDetail(id: string) {
  return requestClient.get(`/promotion/trade-calendar/${id}`);
}

/** 新建档期 */
export async function addTradeCalendar(data: any) {
  return requestClient.post('/promotion/trade-calendar', data);
}

/** 修改档期 */
export async function updateTradeCalendar(id: string, data: any) {
  return requestClient.put(`/promotion/trade-calendar/${id}`, data);
}

/** 切换档期状态（targetStatus：1生效 2停用 0草稿） */
export async function changeTradeCalendarStatus(
  id: string,
  targetStatus: string,
) {
  return requestClient.put(
    `/promotion/trade-calendar/${id}/status/${targetStatus}`,
  );
}

/** 删除档期 */
export async function deleteTradeCalendar(id: string) {
  return requestClient.delete(`/promotion/trade-calendar/${id}`);
}

/** C 端生效档期列表（装修预览用，与 C 端同源口径） */
export async function getAppTradeCalendars(): Promise<AppCalendar[]> {
  return requestClient.get('/promotion/app/calendar/list');
}

/** C 端档期特供列表（装修预览用，calendarId 为空=全部生效档期） */
export async function getAppCalendarSpecials(
  calendarId?: string,
): Promise<AppCalendarSpecial[]> {
  return requestClient.get('/promotion/app/calendar/specials', {
    params: { calendarId: calendarId || undefined },
  });
}
