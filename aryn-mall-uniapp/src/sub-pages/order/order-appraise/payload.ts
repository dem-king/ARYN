/**
 * 一单多品评价：表单状态 → 提交载荷。
 *
 * 抽成纯函数，页面只负责装配状态与发起请求；这里的规则直接对应后端契约：
 * - 只提交「未评价且未跳过」的商品（支持子集提交，全部评完后端才置订单已评价）；
 * - 文字/图片全部选填，空内容由后端兜底；
 * - 未开启「分开评价」时，本卡没写的文字/图片/标签由整单内容套用；
 *   本卡写过的以本卡为准（单独评价覆盖统一文案）；
 * - 星级 1~5，物流/服务分整单采集后写入每条商品评价，匿名整单生效；
 * - 标签最多 5 个、单项最多 10 字（UI 上再收窄到 3 个），图片最多 6 张。
 */

/** 单条评价最多图片数（与后端 @Size(max = 6) 对齐） */
export const APPRAISE_MAX_PICS = 6
/** 评价文字上限（与后端 @Size(max = 255) 对齐） */
export const APPRAISE_MAX_CONTENT = 255
/** 后端允许的单条评价标签数上限 */
export const APPRAISE_MAX_TAGS = 5
/** 后端允许的单个标签长度上限 */
export const APPRAISE_MAX_TAG_LENGTH = 10

/** wd-upload 文件项的最小结构（只依赖载荷组装需要的字段） */
export interface AppraiseUploadFile {
  url?: string
  status?: string
  response?: string | Record<string, any> | null
}

/** 待评价商品卡片的提交相关字段 */
export interface AppraiseDraftItem {
  orderItemId?: string | null
  goodsScore?: number | string | null
  content?: string | null
  uploadList?: AppraiseUploadFile[] | null
  tags?: string[] | null
  /** 用户点了「跳过」：本次不提交，留在待评价列表 */
  skipped?: boolean
  /** 已评价（拉取订单时按已评列表标记）：置灰不可再评 */
  reviewed?: boolean
}

/** 整单区表单 */
export interface AppraiseOrderForm {
  /** 开启后不再套用统一文案/晒图/标签，逐卡使用本卡内容 */
  separate?: boolean
  anonymous?: boolean
  logisticsScore?: number | string | null
  serviceScore?: number | string | null
  unified?: {
    content?: string | null
    uploadList?: AppraiseUploadFile[] | null
    tags?: string[] | null
  } | null
}

/** 后端评价提交体（数组元素） */
export interface AppraisePayloadItem {
  orderItemId: string
  goodsScore: number
  content?: string
  picUrls?: string[]
  tags?: string[]
  isAnonymous: boolean
  logisticsScore?: number
  serviceScore?: number
}

/** 已评列表回填信息（来自 goodsappraise/user-page） */
export interface AppraiseReviewedRecord {
  orderItemId?: string | null
  createTime?: string | null
  goodsScore?: number | string | null
  appraiseType?: number | string | null
}

export interface AppraiseReviewedInfo {
  reviewedTime: string
  reviewedScore: number | null
  systemDefault: boolean
}

/** 评价窗口内的后端按 createTime 判定「30 天内」，载荷这里只做 UI 前置定位 */
export interface AppraiseIssue {
  index: number
  /** upload=有失败图片；uploading=图片还没传完（此时提交会丢图） */
  code: 'upload' | 'uploading'
}

/** 数值化并收敛到 1~5 的整数评分；非法值返回 null */
export function normalizeScore(value: unknown): number | null {
  const score = Number(value)
  if (!Number.isFinite(score))
    return null
  const rounded = Math.round(score)
  if (rounded < 1 || rounded > 5)
    return null
  return rounded
}

/** 去首尾空白，并按字符（而不是 UTF-16 码元）截断到 255 字 */
export function normalizeContent(value: unknown): string {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text)
    return ''
  return Array.from(text).slice(0, APPRAISE_MAX_CONTENT).join('')
}

/**
 * 标签归一化：去空白、去重、单项截断到 10 字、最多保留 5 个。
 * 用 `Array.from` 截断避免把 emoji 的代理对切成半个字符。
 */
export function normalizeTags(value: unknown, max = APPRAISE_MAX_TAGS): string[] {
  if (!Array.isArray(value))
    return []
  const tags: string[] = []
  for (const raw of value) {
    if (typeof raw !== 'string')
      continue
    const text = raw.trim()
    if (!text)
      continue
    const clipped = Array.from(text).slice(0, APPRAISE_MAX_TAG_LENGTH).join('')
    if (!clipped || tags.includes(clipped))
      continue
    tags.push(clipped)
    if (tags.length >= max)
      break
  }
  return tags
}

function parseUploadResponse(response: unknown): Record<string, any> | null {
  if (!response)
    return null
  if (typeof response === 'string') {
    try {
      const parsed = JSON.parse(response)
      return parsed && typeof parsed === 'object' ? parsed : null
    }
    catch {
      return null
    }
  }
  return typeof response === 'object' ? response as Record<string, any> : null
}

/**
 * 从 wd-upload 的 file-list 里提取已上传图片 URL。
 *
 * 只认上传成功（无 status 的预填项视为成功），失败/上传中的不提交；
 * 上传组件在 `response` 里放的是接口原始返回（字符串或对象），图片地址在 `data` 里。
 */
export function extractUploadedUrls(fileList: unknown, limit = APPRAISE_MAX_PICS): string[] {
  if (!Array.isArray(fileList))
    return []
  const urls: string[] = []
  for (const raw of fileList) {
    const file = raw as AppraiseUploadFile | null
    if (!file || typeof file !== 'object')
      continue
    if (file.status && file.status !== 'success')
      continue
    const response = parseUploadResponse(file.response)
    const data = response?.data
    const url = typeof data === 'string'
      ? data
      : (data && typeof data === 'object' && typeof data.url === 'string' ? data.url : file.url)
    if (!url || typeof url !== 'string')
      continue
    if (urls.includes(url))
      continue
    urls.push(url)
    if (urls.length >= limit)
      break
  }
  return urls
}

/** 上传列表里是否有失败项：提交前用它定位到具体商品卡 */
export function hasUploadFailure(fileList: unknown): boolean {
  if (!Array.isArray(fileList))
    return false
  return fileList.some(file => (file as AppraiseUploadFile | null)?.status === 'fail')
}

/** 上传列表里是否还有没传完的图片：此时提交会把未完成项丢掉 */
export function hasUploadPending(fileList: unknown): boolean {
  if (!Array.isArray(fileList))
    return false
  return fileList.some((file) => {
    const status = (file as AppraiseUploadFile | null)?.status
    return status === 'loading' || status === 'pending'
  })
}

function isSystemDefaultRecord(record: AppraiseReviewedRecord): boolean {
  return Number(record.appraiseType) === 1
}

/**
 * 已评列表 → orderItemId 索引。
 *
 * 只按 orderItemId 匹配（天然限定在当前订单内），重复记录保留第一条；
 * 系统默认评价不写星级，`reviewedScore` 为 null，由页面展示「未评分」。
 */
export function buildReviewedItemMap(
  records?: AppraiseReviewedRecord[] | null,
): Map<string, AppraiseReviewedInfo> {
  const map = new Map<string, AppraiseReviewedInfo>()
  if (!Array.isArray(records))
    return map
  for (const record of records) {
    const orderItemId = record?.orderItemId
    if (!orderItemId || map.has(orderItemId))
      continue
    map.set(orderItemId, {
      reviewedTime: record.createTime || '',
      reviewedScore: normalizeScore(record.goodsScore),
      systemDefault: isSystemDefaultRecord(record),
    })
  }
  return map
}

/** 选中商品的本卡文字：本卡优先；未分开评价时回落到整单文案 */
function resolveItemContent(item: AppraiseDraftItem, form: AppraiseOrderForm): string {
  const own = normalizeContent(item.content)
  if (own)
    return own
  if (form.separate)
    return ''
  return normalizeContent(form.unified?.content)
}

/** 选中商品的本卡图片：本卡优先；未分开评价时回落到整单晒图 */
function resolveItemPics(item: AppraiseDraftItem, form: AppraiseOrderForm): string[] {
  const own = extractUploadedUrls(item.uploadList)
  if (own.length > 0)
    return own
  if (form.separate)
    return []
  return extractUploadedUrls(form.unified?.uploadList)
}

/** 选中商品的本卡标签：本卡优先；未分开评价时回落到整单快捷标签 */
function resolveItemTags(item: AppraiseDraftItem, form: AppraiseOrderForm): string[] {
  const own = normalizeTags(item.tags)
  if (own.length > 0)
    return own
  if (form.separate)
    return []
  return normalizeTags(form.unified?.tags)
}

function isEligibleItem(item: AppraiseDraftItem): boolean {
  return Boolean(item?.orderItemId) && !item.reviewed && !item.skipped
}

/**
 * 组装提交载荷。
 *
 * 星级必填（缺省/非法按 5 星兜底，与页面默认 5 星一致）；文字、图片、标签、
 * 物流/服务分为空时不发送对应字段，交给后端兜底，避免把空串写进库。
 */
export function buildAppraisePayload(
  items?: AppraiseDraftItem[] | null,
  form: AppraiseOrderForm = {},
): AppraisePayloadItem[] {
  if (!Array.isArray(items))
    return []
  const logisticsScore = normalizeScore(form.logisticsScore)
  const serviceScore = normalizeScore(form.serviceScore)
  const payload: AppraisePayloadItem[] = []
  for (const item of items) {
    if (!isEligibleItem(item))
      continue
    const orderItemId = String(item.orderItemId)
    const entry: AppraisePayloadItem = {
      orderItemId,
      goodsScore: normalizeScore(item.goodsScore) ?? 5,
      isAnonymous: Boolean(form.anonymous),
    }
    const content = resolveItemContent(item, form)
    if (content)
      entry.content = content
    const picUrls = resolveItemPics(item, form)
    if (picUrls.length > 0)
      entry.picUrls = picUrls
    const tags = resolveItemTags(item, form)
    if (tags.length > 0)
      entry.tags = tags
    if (logisticsScore)
      entry.logisticsScore = logisticsScore
    if (serviceScore)
      entry.serviceScore = serviceScore
    payload.push(entry)
  }
  return payload
}

/**
 * 提交前的前端问题定位：目前只有「上传失败」需要拦住（否则会丢图提交）。
 * 返回第一个问题卡片的下标，页面据此滚动+高亮到具体商品，而不是只弹一句通用错误。
 */
export function findFirstAppraiseIssue(
  items?: AppraiseDraftItem[] | null,
  form: AppraiseOrderForm = {},
): AppraiseIssue | null {
  if (!Array.isArray(items))
    return null
  const unifiedUploads = form.separate ? null : form.unified?.uploadList
  const unifiedFailed = hasUploadFailure(unifiedUploads)
  const unifiedPending = hasUploadPending(unifiedUploads)
  for (let index = 0; index < items.length; index++) {
    const item = items[index]
    if (!isEligibleItem(item))
      continue
    if (hasUploadFailure(item.uploadList))
      return { index, code: 'upload' }
    if (hasUploadPending(item.uploadList))
      return { index, code: 'uploading' }
    // 整单晒图失败/未传完会套用到每条商品：报第一张可提交的商品卡即可
    if (unifiedFailed)
      return { index, code: 'upload' }
    if (unifiedPending)
      return { index, code: 'uploading' }
  }
  return null
}

/** 尚未评价的商品数（跳过的也算未评，订单仍会停在待评价） */
export function countUnreviewedItems(items?: AppraiseDraftItem[] | null): number {
  if (!Array.isArray(items))
    return 0
  return items.filter(item => item && !item.reviewed).length
}
