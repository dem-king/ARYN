import { ref } from 'vue';

import { defineStore } from 'pinia';

interface DictCacheEntry {
  key: string;
  value: unknown;
}

export const useDictStore = defineStore('dict', () => {
  const dict = ref<DictCacheEntry[]>([]);

  function getDict(key: string) {
    if (!key) {
      return null;
    }
    return dict.value.find((item) => item.key === key)?.value ?? null;
  }

  function setDict(key: string, value: unknown) {
    if (!key) {
      return false;
    }

    const existing = dict.value.find((item) => item.key === key);
    if (existing) {
      existing.value = value;
    } else {
      dict.value.push({ key, value });
    }
    return true;
  }

  function removeDict(key: string) {
    const index = dict.value.findIndex((item) => item.key === key);
    if (index === -1) {
      return false;
    }
    dict.value.splice(index, 1);
    return true;
  }

  function cleanDict() {
    dict.value = [];
  }

  return { cleanDict, dict, getDict, removeDict, setDict };
});
