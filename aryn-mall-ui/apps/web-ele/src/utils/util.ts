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
  let product = array[0];
  for (const set of array.slice(1)) {
    const res: any = [];
    product.forEach((c: any) => {
      set.forEach((s: any) => {
        const t = [Array.isArray(c) ? c : [c]].flat();
        t.push(s);
        res.push(t);
      });
    });
    product = res;
  }
  return product;
};

export const findArray = (array: any, value: string) => {
  return array.findIndex((item: any) => {
    return item.dictValue === value;
  });
};

export function downloadBlobFile(url: string, query: any, fileName: string) {
  return requestClient
    .download(url, { params: query })
    .then((response) => {
      const blob =
        response instanceof Blob
          ? response
          : new Blob([response], { type: 'application/octet-stream' });
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
