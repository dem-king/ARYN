export interface GoodsUseScenario {
  key: 'storage' | 'usage'
  title: string
  content: string
  icon: 'snowflake' | 'restaurant'
  tone: 'blue' | 'orange'
}

interface UseScenarioInput {
  categoryName?: string | null
  storage?: string | null
  shelfLife?: string | null
}

const FOOD_USAGE: Record<string, string> = {
  '时令鲜果': '适合早餐、值班加餐和靠港补给；洗净即食，也可分餐到班组。',
  '新鲜蔬菜': '适合食堂配菜、汤锅和靠港补给；烹饪前清洗，按需取用。',
  '肉禽蛋品': '适合食堂正餐、烧烤火锅；解冻后彻底加热，避免反复冻融。',
  '海鲜水产': '适合食堂正餐、靠港加餐；清洗后彻底加热，不建议生食。',
  '乳品冷饮': '适合早餐、值班加餐；开封后尽快饮用，冷藏保存。',
  '水饮饮料': '适合值班补水、靠港备货；整箱存放，开封后尽快饮用。',
  '酒水酒类': '适合靠港聚餐、节日加餐；适量饮用，值班前禁饮。',
  '冲调热饮': '适合早餐、夜班热饮；按包装说明冲调，开封后防潮。',
  '休闲零食': '适合值班加餐、靠港休闲；开封后密封防潮，尽快食用。',
  '面包糕点': '适合早餐、下午加餐；短保产品尽快食用，避免高温潮湿。',
  '方便速食': '适合值班快速用餐、应急备餐；按包装说明冲泡或加热。',
  '罐头食品': '适合应急备餐、靠港补给；开封后冷藏并尽快食用。',
  '熟食卤味': '适合食堂加餐、靠港补给；食用前充分加热。',
  '烧烤火锅': '适合聚餐、火锅烧烤；食材需彻底加热，生熟分开。',
  '速冻美食': '适合食堂备餐、应急补给；解冻后尽快烹饪，避免反复冻融。',
  '面食早点': '适合早餐、值班餐；蒸煮或加热后食用，开封后尽快用完。',
  '本周上新': '适合早餐、值班加餐和靠港补给；按品类储存说明食用。',
}

const DRINK_CATEGORIES = new Set(['乳品冷饮', '水饮饮料', '酒水酒类'])

function clean(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function categoryOf(value: string): string {
  return value.split(' / ')[0]?.trim() ?? ''
}

function consumeVerb(category: string): string {
  return DRINK_CATEGORIES.has(category) ? '饮用' : '食用'
}

function storageContent(category: string, storage: string, shelfLife: string): string {
  const verb = consumeVerb(category)
  if (storage && shelfLife)
    return `${storage}保存，建议 ${shelfLife}内${verb}完；开封后密封，尽快${verb}。`
  if (storage)
    return `${storage}保存；开封后密封，尽快${verb}。`
  if (shelfLife)
    return `建议 ${shelfLife}内${verb}完；开封后密封，尽快${verb}。`
  return `按包装标识储存；开封后密封，尽快${verb}。`
}

/**
 * 船上使用场景卡：只做食品类目，数据来自商品现有储存/保质期参数。
 * 不引入新的批次/场景字段，避免为了卡片在 SPU 上造日期。
 */
export function resolveUseScenarios(input: UseScenarioInput | null | undefined): GoodsUseScenario[] {
  const category = categoryOf(clean(input?.categoryName))
  if (!FOOD_USAGE[category])
    return []

  const storage = clean(input?.storage)
  const shelfLife = clean(input?.shelfLife)
  return [
    {
      key: 'storage',
      title: '船上怎么存',
      content: storageContent(category, storage, shelfLife),
      icon: 'snowflake',
      tone: 'blue',
    },
    {
      key: 'usage',
      title: DRINK_CATEGORIES.has(category) ? '船上怎么喝' : '船上怎么吃',
      content: FOOD_USAGE[category],
      icon: 'restaurant',
      tone: 'orange',
    },
  ]
}
