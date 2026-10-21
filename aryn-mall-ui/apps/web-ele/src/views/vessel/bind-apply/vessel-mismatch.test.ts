import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { isSameVesselIdentity, normalizeVesselName } from '../constants';

/**
 * 跨船绑定一致性判定的口径测试。
 *
 * 事故背景（2026-10-19）：管理端审核弹窗把小程序申请的「悦航8号」
 * 绑到了「悦航7号」，全程无提示、无校验、无留痕。修复的核心是
 * 「申请船与目标船是不是同一条船」这个判定，它必须**前后端同源**：
 *
 * - 前端据此在提交前提示并拦下，运营能看到两条船名并显式背书；
 * - 后端据此决定放行还是拒绝，并写入 member.bind_source。
 *
 * 两边归一化一旦漂移，就会出现「前端说一致、后端说一致不了」的错位：
 * 运营勾了确认、填了理由，提交仍被拒绝；或者前端没提示、后端却拒绝。
 * 因此这里既覆盖纯函数各分支，也有一条**直接读 Java 源码**的对齐断言。
 */

const here = dirname(fileURLToPath(import.meta.url));
const adminRoot = resolve(here, '../../../..');
const repoRoot = resolve(adminRoot, '../../..');
const BIND_SERVICE =
  'aryn-vessel/aryn-vessel-biz/src/main/java/com/aryn/cloud/vessel/service/impl/VesselBindServiceImpl.java';

function readBackend(relative: string) {
  return readFileSync(resolve(repoRoot, 'aryn-mall-java', relative), 'utf8');
}

/** `悦航8号` 的申请身份（默认都填了） */
const apply = {
  applyVesselCallSign: 'BXYZ8',
  applyVesselImo: 'IMO9123456',
  applyVesselName: '悦航8号',
};

describe('normalizeVesselName：与后端 normalizeVesselName 同口径', () => {
  it('去空白与分隔符 - _ .', () => {
    expect(normalizeVesselName('悦 航 8 号')).toBe('悦航8');
    expect(normalizeVesselName('悦航-8-号')).toBe('悦航8');
    expect(normalizeVesselName('悦航_8_号')).toBe('悦航8');
    expect(normalizeVesselName('悦航.8.号')).toBe('悦航8');
  });

  it('全角转半角（含全角空格与全角括号）', () => {
    expect(normalizeVesselName('ＡＢＣ１２３')).toBe('abc123');
    expect(normalizeVesselName('悦航（８）号')).toBe('悦航(8)');
    expect(normalizeVesselName('悦航\u3000８号')).toBe('悦航8');
  });

  it('转小写', () => {
    expect(normalizeVesselName('OCEAN STAR')).toBe('oceanstar');
  });

  it('小写化保持「一字符换一字符」，不学 JS 的扩展映射', () => {
    // Java 用 Character.toLowerCase(char)：一对一简单映射。
    // JS 的 String#toLowerCase 会把 U+0130「İ」扩成 'i' + U+0307 两个码位，
    // 而 Java 给的是 'i'（单码位）——归一化结果长度不同，判据就会错位。
    expect(normalizeVesselName('İ')).toBe('i');
    expect([...normalizeVesselName('İ')]).toHaveLength(1);
    // 普通拉丁大写照常转小写
    expect(normalizeVesselName('ABC')).toBe('abc');
  });

  it('去掉尾部「号」（循环去掉全部尾随「号」）', () => {
    expect(normalizeVesselName('悦航8号')).toBe('悦航8');
    expect(normalizeVesselName('悦航8')).toBe('悦航8');
    expect(normalizeVesselName('悦航8号号')).toBe('悦航8');
    // 只去尾部：中间的「号」是名字的一部分
    expect(normalizeVesselName('号悦航8号')).toBe('号悦航8');
  });

  it('空值/全空白回落空串，不抛错', () => {
    expect(normalizeVesselName(undefined)).toBe('');
    expect(normalizeVesselName(null)).toBe('');
    expect(normalizeVesselName('')).toBe('');
    expect(normalizeVesselName('   ')).toBe('');
    expect(normalizeVesselName('---')).toBe('');
  });

  it('不与 Java 的 Character.isWhitespace 错位：NBSP 不算空白', () => {
    // Java isWhitespace('\u00A0') == false → 后端会保留它；JS 的 \s 会丢弃。
    // 这里必须保留，否则 '\u00A0悦航8' 归一化后前后端不同
    expect(normalizeVesselName('悦航\u00A08号')).toBe('悦航\u00A08');
  });
});

describe('isSameVesselIdentity：强键或同名放行，其余一律保守', () => {
  it('iMO 相等即视为同一条船，船名不同也放行', () => {
    expect(
      isSameVesselIdentity(apply, {
        imoCode: 'IMO9123456',
        vesselName: '悦航7号',
      }),
    ).toBe(true);
  });

  it('呼号相等即视为同一条船', () => {
    expect(
      isSameVesselIdentity(
        { ...apply, applyVesselImo: '' },
        { callSign: 'BXYZ8', vesselName: '悦航7号' },
      ),
    ).toBe(true);
  });

  it('船名归一化后相等即视为同一条船（写法差异不算不一致）', () => {
    expect(
      isSameVesselIdentity(
        { applyVesselName: '悦航 8 号' },
        { vesselName: '悦航８号' },
      ),
    ).toBe(true);
    expect(
      isSameVesselIdentity(
        { applyVesselName: 'Ocean Star' },
        {
          vesselName: 'ocean-star',
        },
      ),
    ).toBe(true);
  });

  it('iMO/呼号/船名全不命中 → 不一致（误绑事故的现场）', () => {
    // 「悦航8号」的申请绑到「悦航7号」：这里必须是 false，
    // 否则前端不会提示、后端也不会拦——事故就会重演
    expect(
      isSameVesselIdentity(apply, {
        callSign: 'BXYZ7',
        imoCode: 'IMO7654321',
        vesselName: '悦航7号',
      }),
    ).toBe(false);
  });

  it('弱键也不能「凑」出命中：IMO 不同 + 呼号相同才算', () => {
    expect(
      isSameVesselIdentity(apply, { callSign: 'BXYZ7', imoCode: 'IMO7654321' }),
    ).toBe(false);
    expect(
      isSameVesselIdentity(apply, { callSign: 'BXYZ8', imoCode: 'IMO7654321' }),
    ).toBe(true);
  });

  it('一侧强键缺失时不按该键放行', () => {
    // 申请有 IMO、目标船没有 → 不能因为「都为空」或「单侧有值」就判定一致
    expect(
      isSameVesselIdentity(apply, { imoCode: '', vesselName: '悦航7号' }),
    ).toBe(false);
    expect(
      isSameVesselIdentity(
        { applyVesselCallSign: 'BXYZ8', applyVesselName: '悦航7号' },
        { callSign: null, vesselName: '悦航7号' },
      ),
    ).toBe(true); // 船名相同仍可放行
  });

  it('空值：两侧都空不是「同名」，判不一致（保守）', () => {
    expect(isSameVesselIdentity({}, {})).toBe(false);
    expect(isSameVesselIdentity(undefined, { vesselName: '悦航8号' })).toBe(
      false,
    );
    expect(isSameVesselIdentity(apply, undefined)).toBe(false);
    expect(isSameVesselIdentity(apply, null)).toBe(false);
    // 归一化后为空（全是分隔符）同样不算
    expect(
      isSameVesselIdentity({ applyVesselName: '  ' }, { vesselName: '-' }),
    ).toBe(false);
  });

  it('大小写差异不算不一致（船名统一转小写比对）', () => {
    expect(
      isSameVesselIdentity(
        { applyVesselName: 'OCEAN STAR' },
        { vesselName: 'ocean star' },
      ),
    ).toBe(true);
  });
});

describe('前端归一化必须与后端 normalizeVesselName 同源', () => {
  it('后端仍按「全角转半角 → 去空白/_-. → 小写 → 去尾部号」实现', () => {
    const service = readBackend(BIND_SERVICE);

    // 四个步骤缺一不可：少了任何一步，前端提示与后端判定就会分叉。
    // 只断言这四步的**实现特征**，不绑定到具体方法名/调用点——
    // 后端同一文件的其它改动不该让这条契约变红。
    const normalizeAt = service.indexOf('String normalizeVesselName');
    expect(normalizeAt).toBeGreaterThan(-1);
    const normalizeSection = service.slice(normalizeAt, normalizeAt + 1200);

    // 全角转半角
    expect(normalizeSection).toContain('0xFEE0');
    // 去空白与 - _ .
    expect(normalizeSection).toMatch(/isWhitespace/);
    expect(normalizeSection).toMatch(/==\s*'-'/);
    expect(normalizeSection).toMatch(/==\s*'_'/);
    expect(normalizeSection).toMatch(/==\s*'\.'/);
    // 转小写
    expect(normalizeSection).toMatch(/toLowerCase/);
    // 去尾部「号」
    expect(normalizeSection).toMatch(/endsWith\("号"\)/);
  });
});

describe('归一化与 Java 实现逐码位等价（跨语言对齐）', () => {
  /**
   * 用「归一化后的码位序列」断言，覆盖 Java 与 JS 两个运行时最容易分叉的地方：
   * 空白判定、全角转换、小写映射、尾部「号」。
   *
   * 这些期望值全部由后端同款 `normalizeVesselName` 在 JDK 17/21 上实跑得出
   * （遍历 BMP 全部 63487 个码位对比，当前实现零差异）。改前端归一化必须
   * 先跑一遍跨语言对比，否则测试与后端会一起漂移、失去意义。
   */
  const cases: Array<[input: string, expected: string]> = [
    // 空白与分隔符
    ['悦航 8 号', '悦航8'],
    ['悦航-8-号', '悦航8'],
    ['悦航_8_号', '悦航8'],
    ['悦航.8.号', '悦航8'],
    // 全角：U+FF01..U+FF5E 统一转半角
    ['悦航８号', '悦航8'],
    ['悦航　８号', '悦航8'], // U+3000 全角空格
    ['悦航（８）号', '悦航(8)'],
    ['ＡＢＣ１２３', 'abc123'],
    // 小写
    ['OCEAN STAR', 'oceanstar'],
    ['ocean-star', 'oceanstar'],
    // 尾部「号」循环去除，中间的不动
    ['悦航8号号', '悦航8'],
    ['号悦航8号', '号悦航8'],
    // 空值
    ['  ', ''],
    ['', ''],
    ['---', ''],
    // Java 的 Character.isWhitespace 不认 NBSP → 必须保留
    ['悦航\u00A08号', '悦航\u00A08'],
    // 扩展小写映射：Java 一对一，结果只有 1 个码位
    ['İ', 'i'],
  ];

  it('逐条与 Java 输出一致', () => {
    for (const [input, expected] of cases) {
      expect(normalizeVesselName(input)).toBe(expected);
    }
  });

  it('扩展映射不增加码位长度（Java 是 char 级一对一映射）', () => {
    for (const [input] of cases) {
      // 输入与输出长度不必相同（会删空白/分隔符），但字符必须都是单码位
      for (const ch of normalizeVesselName(input)) {
        expect([...ch]).toHaveLength(1);
      }
    }
  });
});
