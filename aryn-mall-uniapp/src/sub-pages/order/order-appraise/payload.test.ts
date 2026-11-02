import { describe, expect, it } from 'vitest'
import {
  APPRAISE_MAX_PICS,
  APPRAISE_MAX_TAG_LENGTH,
  buildAppraisePayload,
  buildReviewedItemMap,
  countUnreviewedItems,
  extractUploadedUrls,
  findFirstAppraiseIssue,
  normalizeContent,
  normalizeScore,
  normalizeTags,
} from './payload'

/** 构造一个上游接口返回的 wd-upload 文件项（response 为上传接口原始返回） */
function uploadedFile(url: string, extra: Record<string, any> = {}) {
  return {
    url,
    status: 'success',
    response: JSON.stringify({ code: 200, data: url, msg: 'ok' }),
    ...extra,
  }
}

function draftItem(overrides: Record<string, any> = {}) {
  return {
    orderItemId: 'item-1',
    goodsScore: 5,
    content: '',
    uploadList: [] as any[],
    tags: [] as string[],
    ...overrides,
  }
}

describe('normalizeScore', () => {
  it('接受数字/数字字符串并四舍五入到 1~5', () => {
    expect(normalizeScore(5)).toBe(5)
    expect(normalizeScore('4')).toBe(4)
    expect(normalizeScore(4.4)).toBe(4)
    expect(normalizeScore(4.6)).toBe(5)
  })

  it('非法值返回 null，由调用方决定兜底', () => {
    expect(normalizeScore(0)).toBeNull()
    expect(normalizeScore(6)).toBeNull()
    expect(normalizeScore('')).toBeNull()
    expect(normalizeScore(null)).toBeNull()
    expect(normalizeScore(undefined)).toBeNull()
    expect(normalizeScore(Number.NaN)).toBeNull()
  })
})

describe('normalizeContent', () => {
  it('去首尾空白；非字符串归空', () => {
    expect(normalizeContent('  很好吃  ')).toBe('很好吃')
    expect(normalizeContent(undefined)).toBe('')
    expect(normalizeContent(123)).toBe('')
  })

  it('超过 255 字按字符截断（不按 UTF-16 码元切 emoji）', () => {
    expect(normalizeContent('好'.repeat(300))).toHaveLength(255)
    const emoji = '🍎'
    const clipped = normalizeContent(emoji.repeat(300))
    expect(Array.from(clipped)).toHaveLength(255)
    expect(clipped).toBe(emoji.repeat(255))
  })
})

describe('normalizeTags', () => {
  it('去空白、去空项、去重', () => {
    expect(normalizeTags([' 新鲜 ', '', '新鲜', '包装完好'])).toEqual(['新鲜', '包装完好'])
  })

  it('单项超过 10 字截断，最多保留 5 个', () => {
    const clipped = normalizeTags(['一二三四五六七八九十十一'])
    expect(clipped).toEqual(['一二三四五六七八九十'])
    expect(clipped[0]).toHaveLength(APPRAISE_MAX_TAG_LENGTH)

    const many = normalizeTags(['a', 'b', 'c', 'd', 'e', 'f'])
    expect(many).toEqual(['a', 'b', 'c', 'd', 'e'])
  })

  it('非数组安全返回空数组', () => {
    expect(normalizeTags(undefined)).toEqual([])
    expect(normalizeTags('新鲜')).toEqual([])
  })
})

describe('extractUploadedUrls', () => {
  it('解析字符串/对象两种 response，并保留预填项自身的 url', () => {
    const urls = extractUploadedUrls([
      uploadedFile('https://cdn/a.png'),
      { url: 'https://cdn/b.png', response: { code: 200, data: 'https://cdn/b2.png' } },
      { url: 'https://cdn/c.png' },
    ])
    expect(urls).toEqual(['https://cdn/a.png', 'https://cdn/b2.png', 'https://cdn/c.png'])
  })

  it('跳过失败/上传中文件与重复地址', () => {
    const urls = extractUploadedUrls([
      uploadedFile('https://cdn/a.png'),
      uploadedFile('https://cdn/a.png'),
      { url: 'https://cdn/x.png', status: 'fail', response: JSON.stringify({ data: 'https://cdn/x.png' }) },
      { url: 'https://cdn/y.png', status: 'loading' },
    ])
    expect(urls).toEqual(['https://cdn/a.png'])
  })

  it('坏 JSON 不抛错；最多 6 张', () => {
    expect(extractUploadedUrls([{ url: 'https://cdn/a.png', response: '{oops' }])).toEqual(['https://cdn/a.png'])

    const urls = extractUploadedUrls(
      Array.from({ length: 8 }, (_, index) => uploadedFile(`https://cdn/${index}.png`)),
    )
    expect(urls).toHaveLength(APPRAISE_MAX_PICS)
  })
})

describe('buildAppraisePayload', () => {
  it('未分开评价：统一文案/晒图/标签套用到所有未评商品', () => {
    const payload = buildAppraisePayload(
      [draftItem(), draftItem({ orderItemId: 'item-2' })],
      {
        anonymous: true,
        logisticsScore: 4,
        serviceScore: 5,
        unified: {
          content: '整单都很好',
          uploadList: [uploadedFile('https://cdn/unified.png')],
          tags: ['新鲜', '物流快'],
        },
      },
    )

    expect(payload).toHaveLength(2)
    expect(payload[0]).toEqual({
      orderItemId: 'item-1',
      goodsScore: 5,
      isAnonymous: true,
      content: '整单都很好',
      picUrls: ['https://cdn/unified.png'],
      tags: ['新鲜', '物流快'],
      logisticsScore: 4,
      serviceScore: 5,
    })
    // 统一内容是共享语义，逐条独立拷贝而不是同一个引用
    expect(payload[0].picUrls).not.toBe(payload[1].picUrls)
  })

  it('本卡内容优先于统一内容（单独评价覆盖）', () => {
    const payload = buildAppraisePayload(
      [
        draftItem({ content: '这件特别好', tags: ['分量足'], uploadList: [uploadedFile('https://cdn/own.png')] }),
        draftItem({ orderItemId: 'item-2' }),
      ],
      { unified: { content: '统一文案', tags: ['新鲜'] } },
    )
    expect(payload[0]).toMatchObject({
      content: '这件特别好',
      picUrls: ['https://cdn/own.png'],
      tags: ['分量足'],
    })
    expect(payload[1]).toMatchObject({ content: '统一文案', tags: ['新鲜'] })
  })

  it('分开评价：整单文案/晒图/标签不再套用，只用本卡内容', () => {
    const payload = buildAppraisePayload(
      [draftItem({ content: '只写这一件' }), draftItem({ orderItemId: 'item-2' })],
      {
        separate: true,
        unified: { content: '整单文案', tags: ['新鲜'], uploadList: [uploadedFile('https://cdn/u.png')] },
      },
    )
    expect(payload[0]).toMatchObject({ content: '只写这一件', goodsScore: 5 })
    expect(payload[0].picUrls).toBeUndefined()
    expect(payload[0].tags).toBeUndefined()
    expect(payload[1]).not.toHaveProperty('content')
    expect(payload[1]).not.toHaveProperty('tags')
  })

  it('跳过/已评/缺 orderItemId 的商品不提交，顺序按原列表', () => {
    const payload = buildAppraisePayload([
      draftItem({ orderItemId: 'a' }),
      draftItem({ orderItemId: 'b', skipped: true }),
      draftItem({ orderItemId: 'c', reviewed: true }),
      draftItem({ orderItemId: null }),
      draftItem({ orderItemId: 'd' }),
    ])
    expect(payload.map(item => item.orderItemId)).toEqual(['a', 'd'])
  })

  it('空文字与空图片不发送字段；非法星级按 5 星兜底；默认不匿名', () => {
    const payload = buildAppraisePayload(
      [draftItem({ goodsScore: 0, content: '   ', uploadList: [] })],
      { logisticsScore: null, serviceScore: 0 },
    )
    expect(payload[0]).toEqual({
      orderItemId: 'item-1',
      goodsScore: 5,
      isAnonymous: false,
    })
  })

  it('内容超长截断、标签超量截断后再提交', () => {
    const payload = buildAppraisePayload([
      draftItem({
        content: '好'.repeat(300),
        tags: ['一', '二', '三', '四', '五', '六'],
      }),
    ])
    expect(Array.from(payload[0].content || '')).toHaveLength(255)
    expect(payload[0].tags).toEqual(['一', '二', '三', '四', '五'])
  })

  it('空列表安全返回空数组', () => {
    expect(buildAppraisePayload([])).toEqual([])
    expect(buildAppraisePayload(undefined)).toEqual([])
  })
})

describe('findFirstAppraiseIssue', () => {
  it('定位第一个上传失败的商品卡', () => {
    const issue = findFirstAppraiseIssue([
      draftItem({ orderItemId: 'a' }),
      draftItem({ orderItemId: 'b', uploadList: [{ url: '', status: 'fail' }] }),
    ])
    expect(issue).toEqual({ index: 1, code: 'upload' })
  })

  it('整单晒图失败时定位到第一张可提交商品卡', () => {
    const issue = findFirstAppraiseIssue(
      [draftItem({ orderItemId: 'a', skipped: true }), draftItem({ orderItemId: 'b' })],
      { unified: { uploadList: [{ url: '', status: 'fail' }] } },
    )
    expect(issue).toEqual({ index: 1, code: 'upload' })
  })

  it('分开评价时忽略整单晒图的失败项', () => {
    const issue = findFirstAppraiseIssue(
      [draftItem()],
      { separate: true, unified: { uploadList: [{ url: '', status: 'fail' }] } },
    )
    expect(issue).toBeNull()
  })

  it('图片还在上传时定位到对应商品卡（提交会丢图）', () => {
    const issue = findFirstAppraiseIssue([
      draftItem({ orderItemId: 'a' }),
      draftItem({ orderItemId: 'b', uploadList: [{ url: '', status: 'loading' }] }),
    ])
    expect(issue).toEqual({ index: 1, code: 'uploading' })
  })
})

describe('buildReviewedItemMap', () => {
  it('按 orderItemId 建索引，系统默认评价星级为 null', () => {
    const map = buildReviewedItemMap([
      { orderItemId: 'a', createTime: '2026-10-01 10:00:00', goodsScore: 5, appraiseType: 0 },
      { orderItemId: 'b', createTime: '2026-10-02 10:00:00', goodsScore: null, appraiseType: 1 },
    ])
    expect(map.get('a')).toMatchObject({ reviewedTime: '2026-10-01 10:00:00', reviewedScore: 5, systemDefault: false })
    expect(map.get('b')).toMatchObject({ reviewedScore: null, systemDefault: true })
  })

  it('重复记录保留第一条，缺 orderItemId 的忽略', () => {
    const map = buildReviewedItemMap([
      { orderItemId: 'a', createTime: 'first' },
      { orderItemId: 'a', createTime: 'second' },
      { createTime: 'no-item' },
    ])
    expect(map.size).toBe(1)
    expect(map.get('a')?.reviewedTime).toBe('first')
  })
})

describe('countUnreviewedItems', () => {
  it('跳过的计入未评（订单仍停在待评价），已评的不计', () => {
    expect(countUnreviewedItems([
      draftItem({ orderItemId: 'a' }),
      draftItem({ orderItemId: 'b', skipped: true }),
      draftItem({ orderItemId: 'c', reviewed: true }),
    ])).toBe(2)
  })
})
