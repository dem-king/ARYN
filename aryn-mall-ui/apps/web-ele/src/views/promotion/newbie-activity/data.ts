import type { FormRules } from 'element-plus';

import type { NewbieSkuPrice } from '#/api/promotion/newbie-activity';

import {
  NEWBIE_ACTIVITY_TYPE,
  NEWBIE_SCOPE_TYPE,
} from '#/api/promotion/newbie-activity';

export type { NewbieSkuPrice };

/** 新人专享价表单模型（rules 拆开为独立字段，保存时组装回 JSON） */
export interface NewbieForm {
  activityName: string;
  endTime: string;
  priority: number;
  /** 注册 N 天内有效；null 表示不限时效 */
  registerWithinDays: null | number;
  skuPrices: NewbieSkuPrice[];
  startTime: string;
}

export function createDefaultForm(): NewbieForm {
  return {
    activityName: '',
    endTime: '',
    priority: 0,
    registerWithinDays: 30,
    skuPrices: [],
    startTime: '',
  };
}

export const formRules: FormRules = {
  activityName: [
    { required: true, message: '请输入活动名称', trigger: 'blur' },
  ],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  registerWithinDays: [
    {
      min: 1,
      message: '注册时效必须为正整数天数',
      trigger: 'blur',
      type: 'number',
    },
  ],
};

/**
 * 表单 → 后端活动对象。
 *
 * 活动类型固定 9（新人专享价）、范围固定 2（指定商品），
 * scopeValue 由 skuPrices 派生，保证与 rules.skus 一一对应（发布校验会拦不一致）。
 * rules.skus 额外写入 spuId：后端仅读取 skuId/price，多余键被忽略，
 * 但管理端编辑回填需要它反查商品名/图（无按 SKU 反查的管理端接口）。
 */
export function toActivityPayload(form: NewbieForm) {
  const skuIds = form.skuPrices.map((item) => item.skuId);
  const rules = JSON.stringify({
    registerWithinDays: form.registerWithinDays ?? undefined,
    limitPerUser: 1,
    skus: form.skuPrices.map((item) => ({
      skuId: item.skuId,
      spuId: item.spuId,
      price: Number(item.price),
    })),
  });
  return {
    activityName: form.activityName,
    activityType: NEWBIE_ACTIVITY_TYPE,
    endTime: form.endTime,
    priority: form.priority,
    rules,
    scopeType: NEWBIE_SCOPE_TYPE,
    scopeValue: skuIds.join(','),
    startTime: form.startTime,
  };
}

/** 后端活动对象 → 表单（编辑回填） */
export function fromActivity(activity: any): NewbieForm {
  const form = createDefaultForm();
  form.activityName = activity?.activityName ?? '';
  form.startTime = activity?.startTime ?? '';
  form.endTime = activity?.endTime ?? '';
  form.priority = activity?.priority ?? 0;
  try {
    const rules = JSON.parse(activity?.rules ?? '{}');
    form.registerWithinDays = rules.registerWithinDays ?? null;
    form.skuPrices = (rules.skus ?? []).map((item: any) => ({
      price: Number(item.price),
      skuId: item.skuId,
      spuId: item.spuId,
    }));
  } catch {
    form.skuPrices = [];
  }
  return form;
}
