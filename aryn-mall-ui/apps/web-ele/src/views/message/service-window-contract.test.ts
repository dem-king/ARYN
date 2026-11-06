import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 客服工作台独立窗口的接线契约。
 *
 * <p>独立窗口依赖若干「不在同一文件里」的约定，单测很容易在重构时被绕过：
 * - 路由必须 `noBasicLayout`（不套管理端布局）且 `sessionInvalidModal`（401 走页内弹窗）；
 * - 独立壳必须启动实时通道、挂会话失效弹窗、启动跨窗协同；
 * - 主窗口铃铛必须优先把客服会话转给独立窗口；
 * - 提醒宿主抑制必须在消息 store 里生效，否则一条消息会响两遍。</p>
 */
const appRoot = resolve(process.cwd(), 'apps/web-ele');
const readAppSource = (path: string) =>
  readFileSync(resolve(appRoot, 'src', path), 'utf8');

describe('service desk window wiring contract', () => {
  it('registers a noBasicLayout route that hosts the invalid-session modal', () => {
    const router = readAppSource('router/routes/modules/message.ts');
    expect(router).toContain("path: '/message/service-window'");
    expect(router).toContain('noBasicLayout: true');
    expect(router).toContain('sessionInvalidModal: true');
    expect(router).toContain('hideInMenu: true');
    expect(router).toContain('hideInTab: true');
  });

  it('mounts the standalone shell with realtime startup and cross-window sync', () => {
    const shell = readAppSource('views/message/service-window.vue');
    expect(shell).toContain('messageStore.start()');
    expect(shell).toContain('startDeskServiceDeskRole(');
    expect(shell).toContain('subscribeServiceDesk(');
    // 独立窗口必须自己挂载失效弹窗（它没有 BasicLayout 的 #extra 插槽）
    expect(shell).toContain('<SessionInvalidModal />');
  });

  it('routes service bell clicks to the desk window before falling back to main window', () => {
    const layout = readAppSource('layouts/basic.vue');
    expect(layout).toContain('locateConversationInServiceDesk(');
    const locateIndex = layout.indexOf('locateConversationInServiceDesk(');
    const pushIndex = layout.indexOf(
      'messageStore.requestConversation(',
      locateIndex,
    );
    expect(locateIndex).toBeGreaterThan(-1);
    expect(pushIndex).toBeGreaterThan(locateIndex);
  });

  it('suppresses duplicate alerts in the main window while the desk is alive', () => {
    const store = readAppSource('store/message.ts');
    expect(store).toContain('isServiceDeskAlertSuppressed');
    expect(store).toMatch(
      /lastAlertCounts = current;[\s\S]{0,200}isServiceDeskAlertSuppressed\(\)[\s\S]{0,80}return;/,
    );
  });

  it('keeps session invalid modal reachable from the standalone route', () => {
    const sessionInvalid = readAppSource('api/session-invalid.ts');
    expect(sessionInvalid).toContain('sessionInvalidModal === true');
    expect(sessionInvalid).toContain('return true;');
  });

  it('embeds a reclaim-only placeholder while the standalone window is open', () => {
    const embedded = readAppSource('views/message/service/index.vue');
    // 独立窗口打开时不再渲染完整工作台，只保留聚焦/收回占位；主窗口其它页面不受影响
    expect(embedded).toContain('v-if="!deskAlive"');
    expect(embedded).toContain('class="desk-placeholder"');
    expect(embedded).toContain('客服工作台已在独立窗口运行');
    expect(embedded).toContain('focusServiceDeskWindow');
    expect(embedded).toContain('reclaimServiceDeskWindow');
    expect(embedded).toContain('syncServiceDeskLiveness');
    // 占位逻辑只存在于嵌入页，不能挂到布局层变成整窗锁定
    const layout = readAppSource('layouts/basic.vue');
    expect(layout).not.toContain('desk-placeholder');
  });

  it('broadcasts read/changed from the workbench so the other window converges', () => {
    const desk = readAppSource('views/message/service/service-desk.vue');
    const messages = readAppSource(
      'views/message/service/use-desk-messages.ts',
    );
    expect(messages).toContain('broadcastServiceDeskRead(');
    expect(desk).toContain('broadcastServiceDeskChanged(');
    // 上翻历史与按会话隔离草稿在消息域组合式里
    expect(messages).toContain('loadOlder');
    expect(messages).toContain('conversationDraftKey');
  });
});
