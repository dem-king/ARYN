/**
 * CRUD API 工厂函数
 *
 * 消除 52 个 API 文件中 195 处重复的 CRUD 模板代码。
 * 使用泛型约束，提供端到端类型推断。
 *
 * @example
 * ```ts
 * export interface OrderPageQuery { status?: string; keyword?: string }
 * export interface OrderDTO { id?: string; status: string }
 * export interface OrderVO { id: string; orderNo: string }
 *
 * export const { getPage, getById, add, edit, del } = createCrudApi<
 *   OrderPageQuery,
 *   OrderDTO,
 *   OrderVO
 * >('/mall-order/orderinfo');
 * ```
 */
import { requestClient } from '#/api/request';

export interface CrudApi<Q, D, V> {
  getPage: (query: Q) => Promise<any>;
  getById: (id: string) => Promise<V>;
  add: (data: D) => Promise<any>;
  edit: (data: D) => Promise<any>;
  del: (id: string) => Promise<any>;
}

/**
 * 创建标准 CRUD API 方法
 * @param basePath API 基础路径，如 '/mall-order/orderinfo'
 */
export function createCrudApi<Q extends Record<string, any>, D, V>(
  basePath: string,
): CrudApi<Q, D, V> {
  return {
    getPage: (query: Q) =>
      requestClient.get(`${basePath}/page`, { params: query }),
    getById: (id: string) => requestClient.get<V>(`${basePath}/${id}`),
    add: (data: D) => requestClient.post(basePath, data),
    edit: (data: D) => requestClient.put(basePath, data),
    del: (id: string) => requestClient.delete(`${basePath}/${id}`),
  };
}
