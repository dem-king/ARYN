import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 「保存草稿」按钮反馈回归保护。
 *
 * 背景（2026-09-23 用户反馈）：点击「保存草稿」没有任何反应。
 * 根因是编辑器有 1.8s 防抖自动保存，用户点按钮时改动通常已落库；
 * 而 `saveNow()` 在"无未保存改动"时直接 `return Promise.resolve()`，
 * 既不发请求、也不改状态、更不提示，调用方无从分辨"已保存"与"没动"。
 *
 * 本测试守住：按钮必须绑定带反馈的包装函数，且三种结果都要有可见反馈。
 */
const sourcePath = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer/index.vue',
);
const source = readFileSync(sourcePath, 'utf8');

describe('保存草稿按钮反馈', () => {
  it('binds the toolbar save action to a feedback wrapper, not raw saveNow', () => {
    expect(source).toContain('@save="saveDraftManually"');
    expect(source).not.toContain('@save="draftSave.saveNow"');
  });

  it('reports both the saved and the nothing-to-save outcomes', () => {
    const start = source.indexOf('async function saveDraftManually');
    expect(start).toBeGreaterThan(-1);
    const body = source.slice(start, source.indexOf('\n}\n', start));

    // 无未保存改动时不能静默：否则用户看到的就是"点了没有反应"
    expect(body).toContain("'skipped'");
    expect(body).toContain('当前没有未保存的修改');
    // 真正写盘后也要有成功提示
    expect(body).toContain('草稿已保存');
    // 失败原因由错误拦截器统一弹出，这里必须兜住 rejection，
    // 否则 saveNow 的 rejection 会变成 unhandled rejection
    expect(body).toContain('catch');
  });
});
