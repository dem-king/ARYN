/**
 * 页面设计相关API配置
 * 定义页面设计获取的API接口
 */

import { alovaInstance } from '@/api/core/instance'

export interface PageDesign {
  id: string
  /** 页面名称 */
  pageName: string
  /** 页面内容 */
  pageContent: unknown
  pageType: string
  schemaVersion: number
  publishedVersionId?: string
  publishedVersionNo?: number
}

/**
 * 查询页面设计列表
 */
export function getPageDesign(params?: object) {
  return alovaInstance.Get<PageDesign>('/promotion/app/pagedesign', {
    params,
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 根据ID获取页面设计
 */
export interface MetricEvent {
  action: 'component_click' | 'page_view' | 'render_error'
  componentType?: string
  pageDesignId: string
  terminal?: 'h5' | 'weapp'
  versionId?: string
}

/** 上报装修埋点事件（page_view/component_click/render_error） */
export function reportMetrics(events: MetricEvent[]) {
  return alovaInstance.Post<number>('/promotion/app/pagedesign/metrics', events)
}

export function getById(id: string) {
  return alovaInstance.Get<PageDesign>(`/promotion/app/pagedesign/${id}`, {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 按 pageType 获取已发布的装修页面内容（无需登录）。
 * @param pageType 页面类型（'1'=首页, '2'=商品详情页, ...）
 */
export function getPublishedByType(pageType: string) {
  return alovaInstance.Get<PageDesign>(`/promotion/app/pagedesign/type/${pageType}`, {
    headers: {
      skipToken: true,
    },
  })
}

/** 商城默认主题（C 端启动换肤用，未设置时 data 为 null） */
export interface MallTheme {
  themeId: string
  themeName: string
  primaryColor?: string
  /** 品牌辅色，后端由主色衍生下发 */
  secondaryColor?: string
  pageBackgroundColor?: string
  navigationColor?: string
  navigationTextColor?: string
  radius?: number
}

/**
 * 获取商城默认主题（无需登录）。
 * 未设置默认主题时返回 null，调用方应保持当前配色不变。
 */
export function getMallTheme() {
  return alovaInstance.Get<MallTheme | null>('/promotion/app/pagedesign/mall-theme', {
    headers: {
      skipToken: true,
    },
  })
}
