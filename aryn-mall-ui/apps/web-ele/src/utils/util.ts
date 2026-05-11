import { requestClient } from '#/api/request';

/**
 * 生成随机len位数字
 */
export const randomLenNum = (len: any, date: any) => {
  let random = '';
  random = Math.ceil(Math.random() * 100_000_000_000_000)
    .toString()
    .slice(0, Math.max(0, len || 4));
  if (date) random = random + Date.now();
  return random;
};

/**
 * 判断数组是否相等
 */
export const arrEquals = (arr1: any, arr2: any) => {
  if (!arr1 || !arr2) {
    return false;
  }

  if (arr1.length !== arr2.length) {
    return false;
  }

  for (const element of arr2) {
    if (!arr1.includes(element)) {
      return false;
    }
  }

  for (const element of arr1) {
    if (!arr2.includes(element)) {
      return false;
    }
  }
  return true;
};
/**
 * 笛卡儿积
 */
export const descartes = (array: any) => {
  if (array.length < 2) return array[0] || [];
  return Array.prototype.reduce.call(array, (col: any, set: any) => {
    const res: any = [];
    col.forEach((c: any) => {
      set.forEach((s: any) => {
        const t = [Array.isArray(c) ? c : [c]].flat();
        t.push(s);
        res.push(t);
      });
    });
    return res;
  });
};

export const findArray = (array: any, value: string) => {
  return array.findIndex((item: any) => {
    return item.dictValue === value;
  });
};

export function downloadBlobFile(url: string, query: any, fileName: string) {
  return requestClient
    .download(url, { params: query, responseReturn: 'raw' })
    .then((response) => {
      // 如果 data 不是 Blob，则转换为 Blob
      const blob =
        response.data instanceof Blob
          ? response.data
          : new Blob([response.data], { type: 'application/octet-stream' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.append(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    })
    .catch((error) => {
      console.error('下载文件失败:', error);
    });
}
