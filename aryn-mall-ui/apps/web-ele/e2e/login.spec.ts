import { expect, test } from '@playwright/test';

test('login page exposes the primary authentication controls', async ({
  page,
}) => {
  await page.goto('/auth/login');

  await expect(page.getByText('登录账号', { exact: true })).toBeVisible();
  await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  await expect(page.getByPlaceholder('请输入密码')).toBeVisible();
  await expect(page.getByRole('button', { name: '登录' })).toBeVisible();
});
