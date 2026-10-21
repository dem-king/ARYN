export interface SettledResult<T> {
  data?: T;
  error?: unknown;
}

/**
 * 让单个统计接口失败不影响其它卡片更新；调用方按模块收集失败项并提示重试。
 */
export async function settle<T>(
  promise: Promise<T>,
): Promise<SettledResult<T>> {
  try {
    return { data: await promise };
  } catch (error) {
    return { error };
  }
}
