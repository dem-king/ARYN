import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * 网关服务域首段白名单（来源：aryn-gateway-dev.yml 的 Path 断言）。
 *
 * 背景：2026-09-20 船供运营多个菜单报
 * `404 NOT_FOUND "No static resource order/fulfillment/wave/page."`。
 * 原因是新增页面把订单域首段写成 `/order`，而网关实际只注册了 `/mall-order/**`；
 * `/order/**` 不匹配任何路由，请求会落到 Gateway 自身的静态资源处理器。
 *
 * `rewriteBootUrl` 只做「去掉首段」，不会校验首段是否合法，
 * 因此 boot 模式同样会 404。本测试用于在静态阶段拦住这类错误。
 */
const ALLOWED_DOMAINS = new Set([
  'auth',
  'gen',
  'mall-order',
  'mall-user',
  'message',
  'pay',
  'product',
  'promotion',
  'upms',
  'vessel',
]);

const REQUEST_METHODS = ['delete', 'download', 'get', 'patch', 'post', 'put'];

/** 以测试文件自身定位 api 根目录，避免依赖运行时的 cwd。 */
const apiRoot = dirname(fileURLToPath(import.meta.url));

function collectApiSources(): Array<{ path: string; text: string }> {
  const collected: Array<{ path: string; text: string }> = [];
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir)) {
      if (entry === 'node_modules') continue;
      const full = join(dir, entry);
      if (statSync(full).isDirectory()) {
        walk(full);
      } else if (/\.(?:ts|vue)$/.test(entry) && !/\.test\.ts$/.test(entry)) {
        collected.push({
          path: relative(apiRoot, full),
          text: readFileSync(full, 'utf8'),
        });
      }
    }
  };
  walk(apiRoot);
  return collected;
}

/**
 * 按行提取字面量请求路径首段。
 *
 * 不用正则做整体回溯匹配，避免触发仓库的 regexp/no-super-linear-backtracking 规则；
 * 这里只做「找到 requestClient 调用 + 方法名 + 紧跟的引号路径」这种确定性扫描。
 */
function extractDomainPrefixes(source: string): string[] {
  const prefixes: string[] = [];
  const lines = source.split('\n');

  for (const line of lines) {
    if (!line.includes('requestClient')) continue;
    const callAt = line.indexOf('requestClient');
    let rest = line.slice(callAt + 'requestClient'.length).trimStart();
    if (rest.startsWith('baseRequestClient')) continue;

    // 允许链式调用间存在换行后的缩进，这里只看当前行剩余部分。
    if (!rest.startsWith('.')) continue;
    rest = rest.slice(1).trimStart();

    const method = REQUEST_METHODS.find((item) => rest.startsWith(item));
    if (!method) continue;
    rest = rest.slice(method.length).trimStart();

    // 跳过泛型参数，如 get<OrderTradeStatisticsVO>(...)
    if (rest.startsWith('<')) {
      const closeAt = rest.indexOf('>');
      if (closeAt === -1) continue;
      rest = rest.slice(closeAt + 1).trimStart();
    }

    if (!rest.startsWith('(')) continue;
    rest = rest.slice(1).trimStart();

    const quote = rest[0];
    if (quote !== `'` && quote !== '"' && quote !== '`') continue;
    const endAt = rest.indexOf(quote, 1);
    if (endAt === -1) continue;

    const pathname = rest.slice(1, endAt).split('?')[0] ?? '';
    if (!pathname.startsWith('/')) continue;
    const first = pathname.split('/').find(Boolean);
    if (first) prefixes.push(first);
  }

  return prefixes;
}

describe('管理端 API 服务域首段', () => {
  const sources = collectApiSources();

  it('扫描到了 api 源码，守卫本身没有失效', () => {
    expect(sources.length).toBeGreaterThan(0);
    expect(
      sources.some(({ path }) => path.endsWith('fulfillment/wave.ts')),
    ).toBe(true);
  });

  it('所有 requestClient 调用的首段都必须是已注册的网关服务域', () => {
    const violations: string[] = [];

    for (const { path, text } of sources) {
      for (const prefix of extractDomainPrefixes(text)) {
        if (!ALLOWED_DOMAINS.has(prefix)) {
          violations.push(`${path} -> /${prefix}/`);
        }
      }
    }

    expect(violations).toEqual([]);
  });

  it('订单和用户域使用网关真实前缀，而不是裸 /order 与 /user', () => {
    const offenders = sources
      .filter(({ text }) => {
        return /['"`]\/order\//.test(text) || /['"`]\/user\//.test(text);
      })
      .map(({ path }) => path);

    expect(offenders).toEqual([]);
  });
});
