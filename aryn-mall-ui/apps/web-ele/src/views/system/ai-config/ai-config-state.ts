import type { AiTestResult } from '#/api/upms/ai-config';

const testStatusLabels: Record<string, string> = {
  AUTHENTICATION: '鉴权失败，请检查 API Key 和模型权限',
  BUDGET_EXCEEDED: '调用额度不足',
  CANCELLED: '测试已取消',
  DISABLED: '当前模型已停用',
  INVALID_RESULT: '模型返回格式不符合要求',
  PASSED: '测试通过',
  PROTOCOL: '接口协议不兼容，请检查 API 地址和模型 ID',
  RATE_LIMITED: '供应商限流，请稍后重试',
  RESPONSE_TOO_LARGE: '模型响应超出大小限制',
  TIMEOUT: '模型响应超时',
  TRUNCATED: '模型响应被截断',
  UNAVAILABLE: '供应商暂不可用，请检查 API 地址和服务状态',
};

export function testStatusLabel(status?: string) {
  return status ? (testStatusLabels[status] ?? '测试失败') : '待测试';
}

export function testIsFresh(
  test: AiTestResult,
  version: string,
  now = Date.now(),
) {
  return (
    test.passed &&
    test.version === version &&
    now - Date.parse(test.testedAt) < 86_400_000 &&
    now >= Date.parse(test.testedAt)
  );
}
export function canActivate(
  dirty: boolean,
  version: null | string,
  models: string[],
  tests: AiTestResult[],
  now = Date.now(),
) {
  return (
    !dirty &&
    !!version &&
    models.length > 0 &&
    models.every((id) =>
      tests.some(
        (test) => test.modelId === id && testIsFresh(test, version, now),
      ),
    )
  );
}
