/**
 * 通知管理页的展示口径（纯函数，无 Vue / 请求依赖，便于单测钉住行为）。
 *
 * 背景：后端 `message_notice.category` 是自由枚举（SYSTEM / ORDER / REFUND /
 * PROMOTION / DELIVERY_TASK / VESSEL_CALL_CHANGE…），`dispatch_status` /
 * `status` 也是大写英文枚举。改造前页面把原始枚举直接渲染进「分类」列
 * （12/12 行显示 `VESSEL_CALL_CHANGE` 并被 120px 列宽强制换行，是行高跳动的
 * 直接来源），详情抽屉也原样透出 `PROMOTION`。
 *
 * 因此这里统一收敛成「中文标签 + 语义色」：
 * - 语义色只用于**圆点/描边/浅底**，文字始终用正文色承载，保证浅色与暗色
 *   主题下都 ≥ 4.5:1（Element Plus 的 light-* 语义色直接当文字色时会掉到 3:1）；
 * - 未知枚举回退成原始值 + info 色，不能因为后端新增分类就渲染空白。
 */
import type {
  NoticeDispatchStatus,
  NoticeRecord,
  NoticeStatus,
} from '#/api/message/types';

/** 语义色集合：对应样式里的 `.nt-pill--{tone}` / `.nt-dot--{tone}`。 */
export type NoticeTone = 'danger' | 'info' | 'primary' | 'success' | 'warning';

export interface NoticeMeta {
  label: string;
  tone: NoticeTone;
}

/** 分类：订单/营销/退款/配送用不同颜色区分，系统类统一灰色。 */
const CATEGORY_META: Record<string, NoticeMeta> = {
  DELIVERY_TASK: { label: '配送任务', tone: 'success' },
  ORDER: { label: '订单', tone: 'primary' },
  PROMOTION: { label: '营销', tone: 'warning' },
  REFUND: { label: '退款', tone: 'danger' },
  SYSTEM: { label: '系统', tone: 'info' },
  VESSEL_CALL_CHANGE: { label: '船舶动态', tone: 'info' },
};

/** 新建通知时可选的分类（与后端既有枚举一致，仍允许输入自定义值）。 */
export const CATEGORY_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '系统通知', value: 'SYSTEM' },
  { label: '订单', value: 'ORDER' },
  { label: '退款', value: 'REFUND' },
  { label: '营销', value: 'PROMOTION' },
  { label: '配送任务', value: 'DELIVERY_TASK' },
  { label: '船舶动态', value: 'VESSEL_CALL_CHANGE' },
];

const STATUS_META: Record<NoticeStatus, NoticeMeta> = {
  DRAFT: { label: '草稿', tone: 'info' },
  PUBLISHED: { label: '已发布', tone: 'success' },
  REVOKED: { label: '已撤回', tone: 'warning' },
};

const DISPATCH_META: Record<NoticeDispatchStatus, NoticeMeta> = {
  FAILED: { label: '分发失败', tone: 'danger' },
  NONE: { label: '未分发', tone: 'info' },
  PARTIAL_FAILED: { label: '部分失败', tone: 'warning' },
  RUNNING: { label: '分发中', tone: 'primary' },
  SUCCEEDED: { label: '已送达', tone: 'success' },
};

const TASK_STATUS_META: Record<string, NoticeMeta> = {
  CANCELED: { label: '已取消', tone: 'info' },
  FAILED: { label: '失败', tone: 'danger' },
  PARTIAL_FAILED: { label: '部分失败', tone: 'warning' },
  PENDING: { label: '待分发', tone: 'info' },
  RUNNING: { label: '分发中', tone: 'primary' },
  SUCCEEDED: { label: '成功', tone: 'success' },
};

const AUDIENCE_LABELS: Record<string, string> = {
  ALL: '全部',
  FILTERED: '条件筛选',
  SPECIFIED: '指定',
};

const TARGET_LABELS: Record<string, string> = {
  MALL_USER: '会员',
  SYS_USER: '工作人员',
};

const TONE_FALLBACK: NoticeMeta = { label: '', tone: 'info' };

/** 分类展示：未知枚举回退原始值，空值给占位符。 */
export function categoryMeta(raw?: null | string): NoticeMeta {
  const key = (raw ?? '').trim().toUpperCase();
  if (!key) {
    return { ...TONE_FALLBACK, label: '—' };
  }
  return CATEGORY_META[key] ?? { label: raw ?? key, tone: 'info' };
}

/** 状态展示：后端只返回三种状态，未知值仍回退原文。 */
export function statusMeta(status?: null | string): NoticeMeta {
  const key = (status ?? '').trim().toUpperCase() as NoticeStatus;
  return STATUS_META[key] ?? { label: status ?? '—', tone: 'info' };
}

/** 分发状态：草稿/已撤回没有投递语义，统一展示占位符。 */
export function dispatchMeta(
  row: Pick<NoticeRecord, 'dispatchStatus' | 'status'>,
) {
  if (row.status !== 'PUBLISHED') {
    return { label: '—', tone: 'info' as NoticeTone };
  }
  const key = (row.dispatchStatus ?? 'NONE') as NoticeDispatchStatus;
  return DISPATCH_META[key] ?? DISPATCH_META.NONE;
}

export function taskStatusMeta(status?: null | string): NoticeMeta {
  const key = (status ?? '').trim().toUpperCase();
  return TASK_STATUS_META[key] ?? { label: status ?? '—', tone: 'info' };
}

/** 目标端（多选）：`MALL_USER,SYS_USER` → `会员、工作人员`。 */
export function targetTypesText(targetTypes?: null | string) {
  if (!targetTypes) {
    return '—';
  }
  return targetTypes
    .split(',')
    .map((type) => TARGET_LABELS[type.trim()] ?? type.trim())
    .filter(Boolean)
    .join('、');
}

export function targetTypesList(targetTypes?: null | string) {
  if (!targetTypes) {
    return [];
  }
  return targetTypes
    .split(',')
    .map((type) => type.trim())
    .filter(Boolean)
    .map((type) => ({ label: TARGET_LABELS[type] ?? type, tone: type }));
}

export function audienceText(audienceType?: null | string) {
  if (!audienceType) {
    return '—';
  }
  return AUDIENCE_LABELS[audienceType] ?? audienceType;
}

/**
 * 已读率（0–100 整数）。
 *
 * 送达数为 0（草稿、任务未开始、受众为空）时返回 0，避免除零得到 NaN
 * 让进度条渲染成异常宽度。
 */
export function deliveryPercent(
  row: Pick<NoticeRecord, 'readCount' | 'recipientCount'>,
) {
  const total = Number(row.recipientCount ?? 0);
  const read = Number(row.readCount ?? 0);
  if (!Number.isFinite(total) || total <= 0) {
    return 0;
  }
  const percent = (read / total) * 100;
  if (!Number.isFinite(percent)) {
    return 0;
  }
  return Math.min(100, Math.max(0, Math.round(percent)));
}

/** 已读 / 送达：无送达时占位，避免出现 `0 / 0`。 */
export function deliveryText(
  row: Pick<NoticeRecord, 'readCount' | 'recipientCount'>,
) {
  if ((row.recipientCount ?? 0) === 0) {
    return '—';
  }
  return `${row.readCount ?? 0} / ${row.recipientCount}`;
}

/**
 * 时间拆成「日期 / 时分」两行，列表里固定行高不随秒数抖动。
 *
 * 后端用 `yyyy-MM-dd HH:mm:ss` 下发，历史脏数据可能是 `T` 分隔或没有秒；
 * 无法解析时整串塞进 `date`、`time` 留空，保证渲染不出现 `undefined`。
 */
export function splitDateTime(raw?: null | string) {
  const text = (raw ?? '').trim();
  if (!text) {
    return { date: '—', time: '' };
  }
  const matched =
    /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/.exec(text);
  if (!matched) {
    return { date: text, time: '' };
  }
  const [, year, month, day, hour, minute, second] = matched;
  return {
    date: `${year}-${month}-${day}`,
    time: second ? `${hour}:${minute}:${second}` : `${hour}:${minute}`,
  };
}

/** 分发失败且已发布时才允许重试（与后端 retryDispatch 的前置条件一致）。 */
export function canRetry(row: Pick<NoticeRecord, 'dispatchStatus' | 'status'>) {
  return (
    row.status === 'PUBLISHED' &&
    (row.dispatchStatus === 'FAILED' || row.dispatchStatus === 'PARTIAL_FAILED')
  );
}

/** 列表行「更多」菜单：按状态与权限给出可用动作，空数组时不渲染菜单。 */
export type RowCommand =
  'delete' | 'edit' | 'publish' | 'republish' | 'retry' | 'revoke';

export function rowCommands(
  row: Pick<NoticeRecord, 'dispatchStatus' | 'status'>,
): RowCommand[] {
  if (row.status === 'DRAFT') {
    return ['edit', 'publish', 'delete'];
  }
  if (row.status === 'REVOKED') {
    return ['republish'];
  }
  return canRetry(row) ? ['revoke', 'retry'] : ['revoke'];
}

export const ROW_COMMAND_LABELS: Record<RowCommand, string> = {
  delete: '删除草稿',
  edit: '编辑草稿',
  publish: '发布',
  republish: '重新发布',
  retry: '重试分发',
  revoke: '撤回',
};

/** 受众快照：后端 `message_notice.audience_snapshot` 的 JSON 结构。 */
export interface AudienceSnapshot {
  deptIds?: string[];
  mallUserIds?: string[];
  memberLevelId?: string;
  memberTagId?: string;
  roleIds?: string[];
  staffUserIds?: string[];
}

/** 解析受众快照：脏数据不能让整页报错，失败按空条件处理。 */
export function parseAudienceSnapshot(
  snapshot?: null | string,
): AudienceSnapshot {
  if (!snapshot) {
    return {};
  }
  try {
    const parsed = JSON.parse(snapshot) as AudienceSnapshot;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * 受众条件的人类可读摘要（详情抽屉用）。
 *
 * 「不选条件=全部目标端」是后端语义（见 NoticeServiceImpl.resolveAudienceType），
 * 因此空条件必须显式说「全部」，不能什么都不显示让运营误以为是漏配。
 */
export function audienceSummary(
  snapshot: AudienceSnapshot,
  targetTypes?: null | string,
) {
  const parts: string[] = [];
  const hasMall = (targetTypes ?? '').includes('MALL_USER');
  const hasStaff = (targetTypes ?? '').includes('SYS_USER');
  if (hasMall) {
    if (snapshot.mallUserIds?.length) {
      parts.push(`指定会员 ${snapshot.mallUserIds.length} 名`);
    } else if (snapshot.memberLevelId || snapshot.memberTagId) {
      parts.push('会员按等级/标签筛选');
    } else {
      parts.push('全部会员');
    }
  }
  if (hasStaff) {
    if (snapshot.staffUserIds?.length) {
      parts.push(`指定工作人员 ${snapshot.staffUserIds.length} 名`);
    } else if (snapshot.roleIds?.length || snapshot.deptIds?.length) {
      parts.push('工作人员按角色/部门筛选');
    } else {
      parts.push('全部工作人员');
    }
  }
  return parts.length > 0 ? parts.join('；') : '全部';
}

/** 通知的破坏性/状态类动作：列表「更多」与详情抽屉共用同一套确认文案。 */
export type NoticeAction =
  'delete' | 'publish' | 'republish' | 'retry' | 'revoke';

export interface NoticeActionPlan {
  /** 危险动作：菜单里排到分隔线之后，详情里用 plain/danger 样式 */
  danger: boolean;
  /** 成功后的提示 */
  done: string;
  /** 二次确认的正文（关键：告诉用户后果，而不只是「确定吗」） */
  message: string;
  /** 确认按钮文案 */
  ok: string;
  /** 确认框标题 */
  title: string;
}

/**
 * 动作文案单源化。
 *
 * 这些说明解释的是**后端语义**（发布后正文不可改、撤回会取消未开始的
 * 分发任务、重试不会重复送达…），列表页和详情抽屉必须一字不差，
 * 否则同一件事在两个入口给出不同承诺。`{target}` 会被替换成目标端文案。
 */
export const NOTICE_ACTION_PLANS: Record<NoticeAction, NoticeActionPlan> = {
  delete: {
    danger: true,
    done: '草稿已删除',
    message: '删除后草稿不可恢复，未发送的通知不会进入任何收件箱。',
    ok: '删除',
    title: '删除草稿',
  },
  publish: {
    danger: false,
    done: '已提交分发任务',
    message: '发布后将按受众快照面向{target}异步分发，正文不可再修改。',
    ok: '发布',
    title: '确认发布',
  },
  republish: {
    danger: false,
    done: '通知已重新发布',
    message:
      '重新发布后通知会回到收件箱；已送达的收件人保留已读状态，未完成的分发会重新提交。',
    ok: '重新发布',
    title: '重新发布',
  },
  retry: {
    danger: true,
    done: '已重新提交分发',
    message: '将重新提交失败的分发任务，已送达的收件人不会重复收到。',
    ok: '重试',
    title: '重试分发',
  },
  revoke: {
    danger: false,
    done: '通知已撤回',
    message:
      '撤回后收件箱立即隐藏，未开始的分发任务会被取消；之后可在「已撤回」里重新发布。',
    ok: '撤回',
    title: '确认撤回',
  },
};

/** 取某个动作的确认文案：`{target}` 用目标端文案填充。 */
export function noticeActionPlan(action: NoticeAction, target?: null | string) {
  const plan = NOTICE_ACTION_PLANS[action];
  return {
    ...plan,
    message: plan.message.replace('{target}', targetTypesText(target)),
  };
}
