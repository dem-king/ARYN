/**
 * 页面设计相关API配置
 * 定义页面设计获取的API接口
 */

import { alovaInstance } from "@/api/core/instance";

export interface PageDesign {
  id: string;
  /** 页面名称 */
  pageName: string;
  /** 页面内容 */
  pageContent: unknown;
  pageType: string;
  schemaVersion: number;
  publishedVersionId?: string;
  publishedVersionNo?: number;
}

/**
 * 查询页面设计列表
 */
export function getPageDesign(params?: object) {
  return alovaInstance.Get<PageDesign>("/promotion/app/pagedesign", {
    params,
    headers: {
      skipToken: true,
    },
  });
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
  });
}
