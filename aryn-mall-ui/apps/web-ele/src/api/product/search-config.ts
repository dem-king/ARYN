import { requestClient } from '#/api/request';

/** 同义词组 */
export interface SynonymGroup {
  id: string;
  words: string[];
  createdAt: string;
  updatedAt: string;
}

/** 热搜词 */
export interface HotSearchWord {
  enabled: boolean;
  id: string;
  sort: number;
  word: string;
  createdAt: string;
}

/**
 * 获取同义词组列表
 */
export async function getSynonymList() {
  return requestClient.get<SynonymGroup[]>('/product/search/synonyms');
}

/**
 * 添加同义词组
 */
export async function addSynonym(data: { words: string[] }) {
  return requestClient.post('/product/search/synonyms', data);
}

/**
 * 更新同义词组
 */
export async function updateSynonym(id: string, data: { words: string[] }) {
  return requestClient.put(`/product/search/synonyms/${id}`, data);
}

/**
 * 删除同义词组
 */
export async function deleteSynonym(id: string) {
  return requestClient.delete(`/product/search/synonyms/${id}`);
}

/**
 * 获取热搜词列表
 */
export async function getHotSearchList() {
  return requestClient.get<HotSearchWord[]>('/product/search/hot-words');
}

/**
 * 添加热搜词
 */
export async function addHotSearch(data: {
  enabled: boolean;
  sort: number;
  word: string;
}) {
  return requestClient.post('/product/search/hot-words', data);
}

/**
 * 更新热搜词
 */
export async function updateHotSearch(
  id: string,
  data: { enabled: boolean; sort: number; word: string },
) {
  return requestClient.put(`/product/search/hot-words/${id}`, data);
}

/**
 * 删除热搜词
 */
export async function deleteHotSearch(id: string) {
  return requestClient.delete(`/product/search/hot-words/${id}`);
}

/**
 * 全量重建搜索索引
 */
export async function rebuildSearchIndex() {
  return requestClient.post('/product/goodsspu/search/rebuild');
}
