import type { Dirent } from 'node:fs';

import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * Element Plus 组件必须显式 import。
 *
 * 背景（2026-10-18）：`vessel/port-vehicle` 页面模板用了 `<ElDialog>` 但脚本里漏了
 * `ElDialog` 的 import。本仓库没有配置 unplugin-vue-components 自动注册，Vue 只会
 * 打印一条 `Failed to resolve component` 警告，然后把 `<ElDialog>` 当作未知自定义元素
 * 直接渲染到页面里：弹窗永远打不开，整张表单（港口名称/车牌号/…）平铺在列表下方，
 * 看起来像布局错乱，实际是组件没解析。
 *
 * 这类漏 import 在 typecheck / lint 阶段都不会报错（模板里的未知标签 Vue 编译器放行），
 * 只能靠运行时警告发现，因此在静态阶段用源码扫描钉住。
 */

const viewsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** 模板里出现即代表必须能解析到的全局组件白名单（本仓库未使用全局注册） */
const GLOBAL_COMPONENTS = new Set<string>([]);

const ELEMENT_TAG = /<(El[A-Z]\w*)[\s/>]/g;

function collectVueFiles(dir: string, acc: string[] = []): string[] {
  let entries: Dirent[];
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return acc;
  }
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectVueFiles(fullPath, acc);
    } else if (entry.name.endsWith('.vue')) {
      acc.push(fullPath);
    }
  }
  return acc;
}

/** 拆出 <script> 段与 <template> 段，避免把 `<ElButton>` 的属性值误当组件 */
function splitSfc(source: string) {
  const templateIndex = source.indexOf('<template>');
  if (templateIndex === -1) {
    return { script: source, template: '' };
  }
  return {
    script: source.slice(0, templateIndex),
    template: source.slice(templateIndex),
  };
}

describe('element-plus 组件解析', () => {
  const files = collectVueFiles(viewsRoot);

  it('扫描范围非空', () => {
    expect(files.length).toBeGreaterThan(100);
  });

  it('模板用到的 El* 组件都在脚本中 import', () => {
    const missing: string[] = [];

    for (const file of files) {
      const source = readFileSync(file, 'utf8');
      const { script, template } = splitSfc(source);
      if (!template) continue;

      const used = new Set<string>();
      for (const match of template.matchAll(ELEMENT_TAG)) {
        if (match[1]) used.add(match[1]);
      }

      for (const name of used) {
        if (GLOBAL_COMPONENTS.has(name)) continue;
        // 脚本里出现即视为已 import / 已注册（命名 import、components 选项、局部 const 均命中）
        if (new RegExp(`\\b${name}\\b`).test(script)) continue;
        missing.push(`${relative(viewsRoot, file)} -> ${name}`);
      }
    }

    expect(
      missing,
      `以下页面模板使用了未 import 的 Element Plus 组件（运行时会渲染成未知标签、功能静默失效）：\n${missing.join('\n')}`,
    ).toEqual([]);
  });
});
