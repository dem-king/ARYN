import { describe, expect, it } from 'vitest';

import { toCsv } from './csv';

describe('toCsv', () => {
  it('escapes commas, quotes and newlines', () => {
    expect(
      toCsv([
        ['a', 'b,c'],
        ['d"e', 'f\ng'],
      ]),
    ).toBe('a,"b,c"\r\n"d""e","f\ng"');
  });

  it('renders empty cells for null and undefined', () => {
    expect(toCsv([[null, undefined, 0]])).toBe(',,0');
  });

  it('neutralizes spreadsheet formula prefixes in text cells', () => {
    expect(toCsv([['=1+1', '+SUM(A1)', '-2+3', '@cmd']])).toBe(
      "'=1+1,'+SUM(A1),'-2+3,'@cmd",
    );
    expect(toCsv([[-2]])).toBe('-2');
  });
});
