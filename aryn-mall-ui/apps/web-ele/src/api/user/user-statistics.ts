import { requestClient } from '#/api/request';

export interface UserOverviewVO {
  monthNewUserCount: number;
  sevenDayNewUserCount: number;
  thirtyDayNewUserCount: number;
  todayNewUserCount: number;
  totalUserCount: number;
  yesterdayNewUserCount?: number;
}

export interface UserSourceStatisticsVO {
  count?: number;
  title: string;
}

export interface UserDashboardStatisticsVO {
  overview: UserOverviewVO;
  sourceStatistics: UserSourceStatisticsVO[];
}

/**
 * 用户侧概览聚合数据 (用户规模 + 流量来源)
 */
export async function getUserDashboardStatistics() {
  return requestClient.get<UserDashboardStatisticsVO>(
    '/mall-user/user/statistics/dashboard',
  );
}

/**
 * 用户统计-用户来源
 */
export async function getUserSourceStatistics(query: any) {
  return requestClient.get('/mall-user/userinfo/source/statistics', {
    params: query,
  });
}

/**
 * 用户趋势统计 (新增用户)
 */
export async function getUserOverview(query: any) {
  return requestClient.get('/mall-user/user/statistics/overview', {
    params: query,
  });
}
