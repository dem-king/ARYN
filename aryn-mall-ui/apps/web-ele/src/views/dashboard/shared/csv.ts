function escapeCell(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  let text = String(value);
  // 防止商品名等用户输入以公式前缀开头，被 Excel 当作公式执行。
  if (typeof value === 'string' && /^[+\-=@\t\r]/.test(text)) {
    text = `'${text}`;
  }
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function toCsv(rows: unknown[][]): string {
  return rows
    .map((row) => row.map((cell) => escapeCell(cell)).join(','))
    .join('\r\n');
}

export function downloadCsv(filename: string, rows: unknown[][]): void {
  const blob = new Blob([`\uFEFF${toCsv(rows)}`], {
    type: 'text/csv;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
