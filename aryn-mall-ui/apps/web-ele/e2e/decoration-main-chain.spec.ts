import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 商城装修发布主链路 E2E（Phase 1 验收链路）：
 * 登录 -> 新建页面 -> 添加组件 -> 保存草稿 -> 提交发布 -> App 读取已发布快照 -> 回滚 -> 跨租户拒绝。
 *
 * 依赖真实运行环境（boot 后端 + MySQL + Redis）；环境不可用或未提供账号时自动跳过，
 * 不会在 CI 中产生假失败。环境变量：
 *   - E2E_USERNAME / E2E_PASSWORD：管理端登录账号
 *   - E2E_USERNAME2 / E2E_PASSWORD2：第二个租户的账号（跨租户拒绝用例）
 */

const APP_ENDPOINT = '/api/promotion/app/pagedesign';

async function backendReachable(request: APIRequestContext) {
  try {
    const response = await request.get(APP_ENDPOINT, { timeout: 5_000 });
    // 已发布首页不存在时返回业务错误 JSON，同样证明后端可用
    return response.status() < 500;
  } catch {
    return false;
  }
}

async function login(page: Page) {
  await page.goto('/auth/login');
  await page.getByPlaceholder('请输入用户名').fill(process.env.E2E_USERNAME ?? '');
  await page.getByPlaceholder('请输入密码').fill(process.env.E2E_PASSWORD ?? '');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).not.toHaveURL(/auth\/login/, { timeout: 15_000 });
}

async function openNewDesigner(origin: Page): Promise<Page> {
  const listPage = await origin.context().newPage();
  await listPage.goto('/promotion/page-design');
  await listPage.getByRole('button', { name: '新建页面' }).click();
  const designerPage = await listPage.context().waitForEvent('page');
  await designerPage.waitForURL(/page-designer/);
  return designerPage;
}

test.describe('商城装修发布主链路', () => {
  test.beforeEach(async ({ request }) => {
    const reachable = await backendReachable(request);
    test.skip(!reachable, '本地后端不可达，跳过集成主链路（仅提供骨架）');
    test.skip(
      !process.env.E2E_USERNAME || !process.env.E2E_PASSWORD,
      '未提供 E2E_USERNAME / E2E_PASSWORD，跳过',
    );
  });

  test('新建、编辑、保存并发布后 App 端读取到 v3 已发布快照', async ({ page }) => {
    await login(page);

    const designer = await openNewDesigner(page);
    // 组件库：加入第一个组件（公告）并确认画布大纲出现
    await designer.locator('.library-item').first().click();
    await expect(designer.locator('.page-outline-list li, [data-test="outline-item"]')).toHaveCount(1, { timeout: 10_000 });

    await designer.getByRole('button', { name: '保存草稿' }).click();
    await expect(designer.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 });

    // 从编辑器地址解析页面 ID
    const pageId = designer.url().match(/page-designer\/([^/?]+)/)?.[1];
    expect(pageId, '保存后地址栏应包含页面 ID').toBeTruthy();

    // 提交发布（未开启审批时直接发布成功）
    await designer.getByRole('button', { name: '发布' }).click();
    await designer.getByRole('button', { name: '提交发布' }).click();
    await expect(
      designer.getByText(/发布成功|发布申请已提交/).first(),
    ).toBeVisible({ timeout: 15_000 });

    // App 端公开读取：schema v3 且包含组件
    const published = await page.request.get(`${APP_ENDPOINT}/${pageId}`);
    expect(published.ok()).toBeTruthy();
    const publishedBody = (await published.json()) as {
      data?: { schemaVersion?: number; pageContent?: { components?: unknown[] } };
    };
    expect(publishedBody.data?.schemaVersion).toBe(3);
    expect(publishedBody.data?.pageContent?.components?.length).toBeGreaterThan(0);

    // 回滚到同一版本（历史版本重发），App 端仍可读取
    const versionsResponse = await page.request.get(
      `/api/promotion/pagedesign/${pageId}/versions`,
    );
    const versions = (await versionsResponse.json()) as {
      data?: Array<{ id: string; versionNo: number }>;
    };
    const latest = versions.data?.[0];
    expect(latest).toBeTruthy();
    const rollback = await page.request.post(
      `/api/promotion/pagedesign/${pageId}/versions/${latest?.id}/rollback`,
      { data: { publishRemark: 'E2E 回滚验证' } },
    );
    expect(rollback.ok()).toBeTruthy();
    const reread = await page.request.get(`${APP_ENDPOINT}/${pageId}`);
    expect(reread.ok()).toBeTruthy();
  });

  test('跨租户访问页面发布数据被拒绝', async ({ page, request }) => {
    test.skip(
      !process.env.E2E_USERNAME2 || !process.env.E2E_PASSWORD2,
      '未提供第二租户账号 E2E_USERNAME2 / E2E_PASSWORD2，跳过',
    );

    await login(page);
    const designer = await openNewDesigner(page);
    await designer.locator('.library-item').first().click();
    await designer.getByRole('button', { name: '保存草稿' }).click();
    await expect(designer.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 });
    const pageId = designer.url().match(/page-designer\/([^/?]+)/)?.[1];
    expect(pageId).toBeTruthy();

    // 第二租户登录后直接访问该页面管理接口，应被租户隔离拒绝
    const intruder = await request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5180',
    });
    const loginResponse = await intruder.post('/api/auth/login', {
      data: {
        username: process.env.E2E_USERNAME2,
        password: process.env.E2E_PASSWORD2,
      },
    });
    const loginBody = (await loginResponse.json()) as {
      data?: { access_token?: string };
    };
    expect(loginBody.data?.access_token).toBeTruthy();

    const foreign = await intruder.get(`/api/promotion/pagedesign/${pageId}`, {
      headers: { Authorization: `Bearer ${loginBody.data?.access_token}` },
    });
    const foreignBody = (await foreign.json()) as {
      data?: unknown;
      msg?: string;
    };
    expect(
      foreignBody.data === null ||
        /不存在|无权|失败/.test(foreignBody.msg ?? ''),
    ).toBeTruthy();
  });
});
