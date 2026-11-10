/**
 * 商品属性标签的运营预设词表与一键模板。
 *
 * 背景：属性标签是自由输入，运营打开编辑器时「属性名/属性值」两个空输入框没有任何
 * 参照，不知道能填什么、该填多长，常常写出「产地/来自某某湖区」这种长短不一、
 * 与 C 端首屏横滑卡（176rpx 定宽、两行文案）不匹配的内容。
 *
 * 这里给出三层引导，**全部只作用于管理端输入体验**：
 * 1. `GOODS_ATTR_TAG_LABEL_PRESETS`：属性名候选（系列/产地/储存条件…），带默认图标与色调；
 * 2. `GOODS_ATTR_TAG_VALUE_PRESETS`：按属性名分组的属性值候选，选中属性名后收敛；
 * 3. `GOODS_ATTR_TAG_TEMPLATES`：整条标签模板，一次点选即填好值/名/图标/色调，
 *    其中「地标/冷藏/上榜」对齐参考商详的属性卡形态。
 *
 * 数据契约零变更：预设产出的仍然只是 `GoodsAttrTagFormValue` 的既有字段，
 * 后端 `GoodsAttrTagNormalizer` 的白名单与长度限制不变，提交时照旧走 `validateGoodsAttrTags`。
 * 预设文案同时受 `goods-spu-form-validation.ts` 的长度上限约束，由单测钉住，
 * 避免以后加词条时突破 12/24 字符而被后端拒绝。
 */
import type { GoodsAttrTagFormRow } from './goods-spu-form-validation';

/** 与后端 `GoodsAttrTagNormalizer.ICONS` 一致的预置图标 key（含「无图标」） */
export type GoodsAttrTagIconOption =
  | 'location'
  | 'none'
  | 'package'
  | 'security'
  | 'shopping-bag'
  | 'snowflake'
  | 'star-filled'
  | 'time'
  | 'trophy';

/** 与后端 `GoodsAttrTagNormalizer.TONES` 一致的色调 */
export type GoodsAttrTagToneOption = 'blue' | 'gray' | 'green' | 'orange';

/** 一条属性名候选：默认带出图标与色调，减少「填完名还要再挑两个下拉」的操作 */
export interface GoodsAttrTagLabelPreset {
  label: string;
  /** 提示文案，说明这个属性名适合放什么值 */
  hint: string;
  icon: GoodsAttrTagIconOption;
  tone: GoodsAttrTagToneOption;
  /** 该属性名下最常用的属性值，按运营使用频率排序 */
  values: string[];
}

/**
 * 属性名候选表。顺序即下拉顺序：先放商详首屏最常出现的四个，
 * 再放资质/规格类，最后是榜单类。
 */
export const GOODS_ATTR_TAG_LABEL_PRESETS: GoodsAttrTagLabelPreset[] = [
  {
    label: '系列',
    hint: '商品归属的系列或产地标识',
    icon: 'shopping-bag',
    tone: 'green',
    values: ['地标', '当季', '产地直采', '有机', '进口'],
  },
  {
    label: '产地',
    hint: '具体产地，弹窗可展开说明',
    icon: 'location',
    tone: 'green',
    values: ['阳澄湖', '五常', '烟台', '赣南', '新西兰', '挪威'],
  },
  {
    label: '储存条件',
    hint: '温层与保存方式',
    icon: 'snowflake',
    tone: 'blue',
    values: ['冷藏', '冷冻', '常温', '0-4℃冷藏', '阴凉干燥'],
  },
  {
    label: '榜单',
    hint: '榜单名次或推荐位',
    icon: 'trophy',
    tone: 'orange',
    values: ['上榜', '热销榜 TOP1', '回购榜 TOP3', '编辑推荐'],
  },
  {
    label: '规格',
    hint: '箱规、净含量等规格信息',
    icon: 'package',
    tone: 'gray',
    values: ['500g', '1kg', '整箱装', '12 瓶/箱', '独立小包装'],
  },
  {
    label: '保障',
    hint: '售后与品质承诺',
    icon: 'security',
    tone: 'blue',
    values: ['坏果包赔', '正品保障', '假一赔十', '7 天无理由'],
  },
  {
    label: '时效',
    hint: '配送或发货时效',
    icon: 'time',
    tone: 'gray',
    values: ['30 分钟达', '次日达', '48 小时内发货'],
  },
  {
    label: '精选',
    hint: '运营精选/买手推荐',
    icon: 'star-filled',
    tone: 'orange',
    values: ['买手精选', '本周新品', '限量供应'],
  },
];

/**
 * 属性名 → 候选值。属性名不在预设表时返回空数组，
 * 表示「这个词是运营自建的」，前端就不展示值候选，避免误导。
 */
export function getAttrValuePresets(
  label: null | string | undefined,
): string[] {
  const key = (label ?? '').trim();
  if (!key) return [];
  const preset = GOODS_ATTR_TAG_LABEL_PRESETS.find(
    (item) => item.label === key,
  );
  return preset ? [...preset.values] : [];
}

/** 按属性名取该词条默认的图标与色调；自建属性名回落「无图标 + 灰色」 */
export function getAttrLabelPreset(
  label: null | string | undefined,
): GoodsAttrTagLabelPreset | undefined {
  const key = (label ?? '').trim();
  if (!key) return undefined;
  return GOODS_ATTR_TAG_LABEL_PRESETS.find((item) => item.label === key);
}

/** 一条可一键添加的标签模板 */
export interface GoodsAttrTagTemplate {
  label: string;
  value: string;
  icon: GoodsAttrTagIconOption;
  tone: GoodsAttrTagToneOption;
  /** 弹窗说明；留空表示「无跳转」 */
  popupContent: string;
  popupTitle: string;
}

/**
 * 一键模板：对齐参考商详的三张属性卡，点一下即得到一条可直接提交的标签。
 * 弹窗内容只做「说明怎么写」的示范，运营可改可删。
 */
export const GOODS_ATTR_TAG_TEMPLATES: GoodsAttrTagTemplate[] = [
  {
    label: '系列',
    value: '地标',
    icon: 'location',
    tone: 'green',
    popupTitle: '地标产品',
    popupContent: '产地为地理标志产品保护区，产地直采。',
  },
  {
    label: '储存条件',
    value: '冷藏',
    icon: 'snowflake',
    tone: 'blue',
    popupTitle: '储存条件',
    popupContent: '建议 0-4℃ 冷藏保存，开封后请尽快食用。',
  },
  {
    label: '榜单',
    value: '上榜',
    icon: 'trophy',
    tone: 'orange',
    popupTitle: '榜单说明',
    popupContent: '入选本周热销榜，按近 7 天销量排序。',
  },
  {
    label: '规格',
    value: '整箱装',
    icon: 'package',
    tone: 'gray',
    popupTitle: '',
    popupContent: '',
  },
  {
    label: '保障',
    value: '坏果包赔',
    icon: 'security',
    tone: 'blue',
    popupTitle: '售后保障',
    popupContent: '签收 24 小时内拍照反馈，坏果按比例赔付。',
  },
  {
    label: '时效',
    value: '30 分钟达',
    icon: 'time',
    tone: 'gray',
    popupTitle: '',
    popupContent: '',
  },
];

/**
 * 把模板转成表单行。带弹窗内容的模板默认打开「弹窗说明」，
 * 否则默认「无跳转」，与运营手动配置的心智一致。
 */
export function buildAttrTagFromTemplate(
  template: GoodsAttrTagTemplate,
): GoodsAttrTagFormRow {
  const hasPopup = template.popupContent.trim().length > 0;
  return {
    icon: template.icon,
    label: template.label,
    linkType: hasPopup ? 'popup' : 'none',
    linkUrl: '',
    popupContent: template.popupContent,
    popupTitle: template.popupTitle,
    tone: template.tone,
    value: template.value,
  };
}

/**
 * 自动补全候选：做「包含匹配」而不是前缀匹配，运营记得住「冷藏」但未必记得
 * 完整词条顺序；已用过的属性名排在后面但不隐藏（同一属性名允许出现多次，
 * 例如两个不同维度的「规格」），由调用方决定是否置灰。
 */
export function filterAttrSuggestions(
  candidates: string[],
  keyword: null | string | undefined,
): string[] {
  const query = (keyword ?? '').trim().toLowerCase();
  const unique = [...new Set(candidates.map((item) => item.trim()))].filter(
    (item) => item.length > 0,
  );
  if (!query) return unique;
  return unique.filter((item) => item.toLowerCase().includes(query));
}

/**
 * 判断候选值是否已被当前表单的其他行使用，用于在候选里标「已添加」。
 * 只比对同类属性名的行：不同属性名下的同名值（如两个「冷藏」）不算重复。
 */
/** 只读 label/value 两列，不要求整行齐全，便于测试与复用 */
export interface AttrTagComparableRow {
  label: string;
  value: string;
}

export function isAttrValueUsed(
  rows: AttrTagComparableRow[],
  label: null | string | undefined,
  value: null | string | undefined,
  currentIndex: number,
): boolean {
  const key = (label ?? '').trim();
  const target = (value ?? '').trim();
  if (!key || !target) return false;
  return rows.some(
    (row, index) =>
      index !== currentIndex &&
      row.label.trim() === key &&
      row.value.trim() === target,
  );
}
