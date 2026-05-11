/**
 * 页面设计相关API配置
 * 定义页面设计获取的API接口
 */

import { alovaInstance } from '@/api/core/instance'

export interface PageDesign {
  id?: string
  /** 页面名称 */
  pageName?: string
  /** 页面内容 */
  pageContent?: any
  /** 状态：0.正常；1.停用； */
  status?: string
  /** 首页页面：0.否；1.是； */
  homeStatus?: string
}

/**
 * 查询页面设计列表
 */
export function getPageDesign(params?: object) {
  return alovaInstance.Get<PageDesign>('/shop/app/pagedesign', {
    params,
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 根据ID获取页面设计
 */
export function getById(id: string) {
  return alovaInstance.Get<PageDesign>(`/shop/app/pagedesign/${id}`, {
    headers: {
      skipToken: true,
    },
  })
}
