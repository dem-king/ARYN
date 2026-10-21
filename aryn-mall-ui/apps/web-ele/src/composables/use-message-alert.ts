/**
 * 新消息提醒：声音 + 语音播报 + 浏览器桌面通知。
 *
 * 背景：站内信与客服会话靠 WebSocket 免刷新进铃铛，但工作人员盯着别的页面
 * （或在仓库里走动）时仍然会漏——铃铛只是悄悄变数字。本模块在未读数上升时补强提醒，
 * 让新订单/取消/退款申请这类事件**不必点开铃铛就能被听到**。
 *
 * 四条硬约束（都踩过）：
 * 1. 首次进入不提醒：登录时拉到的历史未读是存量，不是「新消息」，
 *    必须先把基线定下来，遇到上升沿才提醒。
 * 2. 音频与语音都要用户手势解锁：浏览器 autoplay 策略下，未解锁的 AudioContext 不发声；
 *    Chrome 的 Web Speech 同样要求页面已获得用户激活——无手势时 speak() 不抛异常，
 *    只派发 error=not-allowed，声音被静默丢掉。早期只在「切换开关」时解锁音频、
 *    且默认语音与手势无关，导致刚打开后台没点过任何东西的管理员第一条新订单是静默的。
 *    现在音频与语音都会在无手势时排队，armAudioUnlock() 等首次手势后补播。
 * 3. 语音播报要节流：连单高峰逐条朗读等于机器枪，4 秒内的后续事件只更新铃铛。
 * 4. 提醒绝不能反过来影响消息链路：所有副作用都吞异常，失败只退化为静默。
 *
 * 播报音源是「预生成音频优先、TTS 兜底」的混合方案：
 * 定稿话术（新订单/退款/COD/客服会话/开关试播）用预生成的 wav（public/static/voice/，
 * 统一外卖接单音风格），音质与试听一致、零网络依赖；动态话术（取消订单来源、
 * 船舶绑定申请标题、超出常见数量的多笔合并）没有现成音频，回退浏览器 TTS 的音色选优。
 */

/** 提醒开关的本地存储键（按浏览器存，不跟随后端账号）。 */
export const ALERT_SOUND_KEY = 'aryn-message-sound-enabled';
export const ALERT_VOICE_KEY = 'aryn-message-voice-enabled';
export const ALERT_DESKTOP_KEY = 'aryn-message-desktop-enabled';

/** 语音播报最小间隔（毫秒）：连单高峰避免逐条朗读刷屏。 */
export const SPEECH_MIN_INTERVAL_MS = 4000;

export interface AlertCounts {
  /** 站内信未读数 */
  noticeUnread: number;
  /** 客服会话未读数（含共享池待领取） */
  conversationUnread: number;
}

export interface AlertPayload {
  /** 本次上升的条数，用于「您有 N 笔新的订单」这类合并播报 */
  count?: number;
  /** 站内信正文/摘要 */
  body: string;
  /** 订单金额（由 cardPayload 解析而来），有则播报「金额 xx 元」 */
  amount?: string;
  kind: 'conversation' | 'notice';
  /** 后端 bizType：ORDER_CREATE / ORDER_CANCEL / ORDER_REFUND_APPLY / COD_PAY_REMIND … */
  sourceType?: string;
  title: string;
}

/** 上升沿判定：只报「增加」的部分，减少（已读）与持平都不提醒。 */
export function unreadIncrease(
  previous: AlertCounts | undefined,
  current: AlertCounts,
): undefined | { conversationAdded: number; noticeAdded: number } {
  // 首次调用只用于定基线：存量未读不是新消息
  if (!previous) return undefined;
  const noticeAdded = Math.max(0, current.noticeUnread - previous.noticeUnread);
  const conversationAdded = Math.max(
    0,
    current.conversationUnread - previous.conversationUnread,
  );
  if (noticeAdded <= 0 && conversationAdded <= 0) return undefined;
  return { conversationAdded, noticeAdded };
}

/**
 * 从站内信 cardPayload 提取金额。
 *
 * 后端下发的卡片载荷形如 `{"orderId":"…","title":"订单 NO-x","amount":"88.00"}`；
 * 解析失败只影响播报文案，不影响提醒本身。
 */
export function parseCardAmount(cardPayload?: string): string | undefined {
  if (!cardPayload) return undefined;
  try {
    const parsed = JSON.parse(cardPayload) as { amount?: unknown };
    const amount = parsed.amount;
    return typeof amount === 'string' && amount !== '' ? amount : undefined;
  } catch {
    return undefined;
  }
}

/** 提醒文案：会话提醒优先（有人在等回复比一条通知更急）。 */
export function buildAlert(
  increase: { conversationAdded: number; noticeAdded: number },
  notifications: Array<{
    amount?: string;
    kind?: string;
    message?: string;
    sourceType?: string;
    title?: string;
  }>,
): AlertPayload {
  if (increase.conversationAdded > 0) {
    const conversation = notifications.find(
      (item) => item.kind === 'conversation',
    );
    return {
      body:
        conversation?.message ??
        `有 ${increase.conversationAdded} 个会话有新消息`,
      count: increase.conversationAdded,
      kind: 'conversation',
      title: conversation?.title ?? '新的客服会话消息',
    };
  }
  const notice = notifications.find((item) => item.kind === 'notice');
  return {
    amount: notice?.amount,
    body: notice?.message ?? `收到 ${increase.noticeAdded} 条新通知`,
    count: increase.noticeAdded,
    kind: 'notice',
    sourceType: notice?.sourceType,
    title: notice?.title ?? '新的站内通知',
  };
}

/**
 * 语音播报文本。
 *
 * 按业务语义说人话：工作人员在别的页面时，「您有一笔新订单，请及时处理」
 * 比念一遍站内信标题有用得多；取消订单的标题本身已含来源（买家取消/超时/管理端），
 * 直接沿用以免丢失区分度。金额不播报：金额在铃铛列表/桌面通知里可见，
 * 连单高峰念金额反而成噪音。
 */
export function buildSpeech(payload: AlertPayload): string {
  if (payload.kind === 'conversation') {
    return '有新的客服消息，请及时回复';
  }
  const multiple = (payload.count ?? 1) > 1;
  let head: string;
  switch (payload.sourceType) {
    case 'COD_PAY_REMIND': {
      head = '有货到付款订单待收款';
      break;
    }
    case 'ORDER_CANCEL': {
      // 标题即来源：买家取消订单 / 订单超时自动取消 / 管理端取消订单
      head = payload.title;
      break;
    }
    case 'ORDER_CREATE': {
      // 不分笔数：多条合并也只提醒「有新的订单」，笔数看铃铛角标
      head = multiple
        ? '您有新的订单，请及时处理'
        : '您有一笔新订单，请及时处理';
      break;
    }
    case 'ORDER_REFUND_APPLY': {
      head = '有新的退款申请待审核';
      break;
    }
    case 'VESSEL_BIND_APPLY_TODO': {
      // 标题已区分申请来源：有新的船员绑定申请 / 有新的业务员认领船舶申请；
      // 多条合并不念数量，统一提醒「有新的船舶绑定申请待审核」
      head = multiple ? '有新的船舶绑定申请待审核' : payload.title;
      break;
    }
    default: {
      head = payload.title;
    }
  }
  return head;
}

/** 节流判定：距上次播报超过最小间隔才再播。 */
export function shouldSpeak(
  now: number,
  lastSpokenAt: number,
  minIntervalMs = SPEECH_MIN_INTERVAL_MS,
): boolean {
  return now - lastSpokenAt >= minIntervalMs;
}

/** 读取布尔开关：只有显式存过 '0' 才算关闭，缺省都算开启。 */
export function readToggle(key: string): boolean {
  try {
    return localStorage.getItem(key) !== '0';
  } catch {
    return true;
  }
}

export function writeToggle(key: string, enabled: boolean): void {
  try {
    localStorage.setItem(key, enabled ? '1' : '0');
  } catch {
    // 隐私模式下写不进去：开关当次生效即可，不阻断
  }
}

// ==================== 音频上下文（单例） ====================

let sharedContext: AudioContext | undefined;
let audioUnlocked = false;
let unlockArmed = false;
let detachUnlockListeners: (() => void) | undefined;
/** 音频被浏览器拦截期间产生的提醒：解锁后补播，超过时效就丢弃。 */
let pendingReplays: Array<{ at: number; run: () => void }> = [];
/**
 * 语音被手势策略拦下时的补播队列。
 *
 * Chrome 的 Web Speech 与自动播放策略同源：页面没有用户手势时 speak() 只会
 * 派发 error=not-allowed，声音彻底丢失。这里与音频一样先排队，等首次手势补播。
 */
let pendingSpeeches: Array<{ at: number; run: () => void }> = [];
const REPLAY_TTL_MS = 60_000;

function ensureAudioContext(): AudioContext | undefined {
  if (sharedContext) return sharedContext;
  if (typeof window === 'undefined') return undefined;
  const Ctor =
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: typeof AudioContext })
      .webkitAudioContext;
  if (!Ctor) return undefined;
  try {
    sharedContext = new Ctor();
  } catch {
    return undefined;
  }
  return sharedContext;
}

function hasSpeech(): boolean {
  return (
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    typeof window.SpeechSynthesisUtterance === 'function'
  );
}

/** 音色列表是否已挂上 voiceschanged 刷新监听（Chrome 首次 getVoices() 为空）。 */
let speechVoicesPrimed = false;

/**
 * 预热中文音色列表。
 *
 * Chrome 的 getVoices() 首次调用通常返回空数组，音色要等 voiceschanged 事件
 * 之后才可用；在解锁（首次手势）时预热一次，让后面的播报能选到高质感音色
 * 而不是默认音色。幂等：同一浏览器会话只挂一次监听。
 */
export function primeSpeechVoices(): void {
  try {
    const synthesis = window.speechSynthesis;
    if (!synthesis) return;
    synthesis.getVoices();
    if (speechVoicesPrimed) return;
    speechVoicesPrimed = true;
    synthesis.addEventListener?.('voiceschanged', () => {
      try {
        synthesis.getVoices();
      } catch {
        // 刷新失败不影响播报，默认音色兜底
      }
    });
  } catch {
    // 不支持语音的环境静默跳过
  }
}

/**
 * 解锁音频：必须在用户手势的调用栈里执行，否则浏览器会拒绝发声。
 * 同时给语音合成做一次静默预热（部分浏览器首次播报会因未加载音色而延迟）。
 */
export function unlockAudio(): void {
  try {
    const context = ensureAudioContext();
    if (context && context.state === 'suspended') {
      void context.resume();
    }
  } catch {
    // 解锁失败不影响其它能力
  }
  try {
    if (hasSpeech()) {
      // 预热音色列表：Chrome 首次 getVoices() 为空，等 voiceschanged 后才有可用音色
      primeSpeechVoices();
    }
  } catch {
    // 忽略
  }
  audioUnlocked = true;
  flushPendingReplays();
  flushPendingSpeeches();
}

/**
 * 当前能否真的发声。
 *
 * 浏览器可能仍把音频上下文挂在 suspended（未解锁 / 自动播放被拦），
 * 这时 `playChime()` 会静默失败——调用方据此决定是否排队等解锁后补播。
 * 没有 WebAudio 的环境按「可用」处理，避免把提醒永久排进队列。
 */
export function isAudioPlayable(): boolean {
  try {
    const context = ensureAudioContext();
    return !context || context.state === 'running';
  } catch {
    return false;
  }
}

/**
 * 登记一条「等音频可用后补播」的提醒。
 *
 * 场景：管理员刚打开后台、还没点过任何东西就来了新订单，自动播放策略会拦住
 * 这一声；等他第一次点击页面时补播，比彻底静默更符合预期。超过 TTL 的事件
 * 已经过期（再响反而误导），直接丢弃。
 */
export function queueAudioReplay(run: () => void): void {
  const now = Date.now();
  pendingReplays = pendingReplays.filter(
    (item) => now - item.at < REPLAY_TTL_MS,
  );
  pendingReplays.push({ at: now, run });
  // 上下文可能是在首次手势之后再被浏览器挂起的（标签页被节流、系统休眠），
  // 此时解锁监听早已卸载，需要重新挂上才能等到下一次手势补播。
  armAudioUnlock();
}

function flushPendingReplays(): void {
  if (pendingReplays.length === 0) return;
  const now = Date.now();
  const due = pendingReplays.filter((item) => now - item.at < REPLAY_TTL_MS);
  pendingReplays = [];
  // 解锁瞬间补播一条即可：多条积压连着响会变成噪音
  due.slice(-1).forEach((item) => {
    try {
      item.run();
    } catch {
      // 补播失败只影响这一次提醒
    }
  });
}

/** 测试与登出用：丢弃尚未补播的提醒。 */
export function clearPendingReplays(): void {
  pendingReplays = [];
  pendingSpeeches = [];
}

/**
 * 排队一条「被浏览器手势策略拦下」的语音播报。
 *
 * 与音频补播同一个 TTL 与「只补最近一条」口径：积压的订单播报连着念等于噪音，
 * 而超过一分钟的播报已经过期，再响反而误导。
 */
export function queueSpeechReplay(run: () => void): void {
  const now = Date.now();
  pendingSpeeches = pendingSpeeches.filter(
    (item) => now - item.at < REPLAY_TTL_MS,
  );
  pendingSpeeches.push({ at: now, run });
  // 与音频补播同理：解锁监听可能已因早前手势卸载，需重新挂上等下一次手势
  armAudioUnlock();
}

function flushPendingSpeeches(): void {
  if (pendingSpeeches.length === 0) return;
  const now = Date.now();
  const due = pendingSpeeches.filter((item) => now - item.at < REPLAY_TTL_MS);
  pendingSpeeches = [];
  if (due.length === 0) return;
  // 用户在此期间关掉了语音开关，就没必要补念
  if (!readToggle(ALERT_VOICE_KEY)) return;
  try {
    // 只补最近一条：积压连着念会变成噪音
    due.slice(-1)[0]?.run();
  } catch {
    // 补播失败只影响这一次提醒
  }
}

/**
 * 页面是否已经获得过用户手势（Chrome 的 Web Speech 以此决定能否发声）。
 *
 * 不支持 userActivation 的浏览器按「已激活」处理，保持原来的立即播报行为。
 */
function hasUserActivation(): boolean {
  try {
    return navigator.userActivation?.hasBeenActive ?? true;
  } catch {
    return true;
  }
}

/**
 * 挂首次手势解锁：任意一次点击 / 按键 / 触摸即可让后续提醒发声。
 *
 * 这是「新订单到达就自动响」的关键——若只在开关点击时解锁，
 * 打开后台后什么都没点的管理员会静默漏掉第一条消息。
 * 幂等：重复调用不会重复挂监听。
 */
export function armAudioUnlock(): void {
  if (unlockArmed || typeof document === 'undefined') return;
  unlockArmed = true;
  const handler = () => {
    unlockAudio();
    detach();
  };
  const events = ['click', 'keydown', 'pointerdown', 'touchstart'] as const;
  const detach = () => {
    events.forEach((event) =>
      document.removeEventListener(event, handler, { capture: true }),
    );
    detachUnlockListeners = undefined;
    unlockArmed = false;
  };
  events.forEach((event) =>
    document.addEventListener(event, handler, { capture: true, passive: true }),
  );
  detachUnlockListeners = detach;
}

/** 卸载手势解锁监听（登出/销毁时调用，避免无用监听常驻）。 */
export function detachAudioUnlock(): void {
  detachUnlockListeners?.();
  // 登出后积压的补播属于上一个会话，不能带到下一个账号
  clearPendingReplays();
}

export function isAudioUnlocked(): boolean {
  return audioUnlocked;
}

// ==================== 提示音与语音播报 ====================

/**
 * 用 WebAudio 合成短提示音。
 *
 * 不引音频文件：仓库里没有 mp3/wav 资源，加二进制资源既增大包体又难审阅；
 * 两声短促的上行音足够引起注意，也不存在音频文件加载失败的问题。
 */
export function playChime(): void {
  try {
    const ctx = ensureAudioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') void ctx.resume();
    const now = ctx.currentTime;
    [880, 1180].forEach((frequency, index) => {
      const oscillator = ctx.createOscillator();
      const gain = ctx.createGain();
      oscillator.type = 'sine';
      oscillator.frequency.value = frequency;
      // 每声 0.12s、错开 0.16s：短促不刺耳
      const start = now + index * 0.16;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.18, start + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.12);
      oscillator.connect(gain).connect(ctx.destination);
      oscillator.start(start);
      oscillator.stop(start + 0.14);
    });
  } catch {
    // 提示音失败不影响任何主流程
  }
}

/**
 * 中文音色评分：zh-CN 优先，神经/在线等自然音色加分。
 *
 * 浏览器暴露的中文音色质量参差（macOS 基础音色偏机械、Edge 晓晓等神经音色自然），
 * 简单取第一个中文音色可能随机拿到机械声；这里按可观测特征打分选最优。
 */
function scoreChineseVoice(voice: SpeechSynthesisVoice): number {
  const lang = voice.lang.toLowerCase();
  let score = 0;
  if (lang === 'zh-cn') score += 4;
  else if (lang.startsWith('zh')) score += 2;
  const name = voice.name.toLowerCase();
  // 神经/在线音色明显比本地基础音色自然
  if (/neural|online|premium|natural/.test(name)) score += 3;
  // 常见高质感中文音色名（微软晓晓/晓伊/云希/云扬、macOS 婷婷等）
  if (
    /xiaoxiao|xiaoyi|yunxi|yunyang|tingting|婷婷|晓晓|晓伊|云希|云扬/.test(name)
  )
    score += 2;
  // 在线音色通常质量更高；localService 缺省视为在线
  if (!voice.localService) score += 1;
  return score;
}

function pickChineseVoice(): SpeechSynthesisVoice | undefined {
  try {
    const voices = window.speechSynthesis.getVoices();
    if (voices.length === 0) return undefined;
    return voices
      .filter((voice) => voice.lang?.toLowerCase().startsWith('zh'))
      .sort((a, b) => scoreChineseVoice(b) - scoreChineseVoice(a))[0];
  } catch {
    return undefined;
  }
}

/**
 * 真正调用浏览器 TTS。
 *
 * 先 cancel 再 speak：连单时若新播报排队，会越积越久直到念出过期信息。
 * 被手势策略拦下时 Chrome 只派发 error=not-allowed（不抛异常），必须接住这条事件，
 * 否则「新订单到了却一声不响」。
 */
function speakNow(text: string, onBlocked: () => void): void {
  const synthesis = window.speechSynthesis;
  synthesis.cancel();
  const utterance = new window.SpeechSynthesisUtterance(text);
  utterance.lang = 'zh-CN';
  utterance.rate = 1;
  utterance.volume = 1;
  const voice = pickChineseVoice();
  if (voice) utterance.voice = voice;
  utterance.addEventListener('error', (event) => {
    if ((event as SpeechSynthesisErrorEvent).error === 'not-allowed') {
      onBlocked();
    }
  });
  synthesis.speak(utterance);
}

/** 定稿话术 → 预生成音频文件名（public/static/voice/）。 */
const PREBUILT_VOICE_FILES: Record<string, string> = {
  '您有一笔新订单，请及时处理': 'order-1',
  '您有新的订单，请及时处理': 'order-multi',
  有新的退款申请待审核: 'refund',
  有货到付款订单待收款: 'cod',
  '有新的客服消息，请及时回复': 'conversation',
  语音播报已开启: 'voice-on',
  // 取消订单沿用标题（来源区分），三种来源各一份音频
  买家取消订单: 'cancel-buyer',
  订单超时自动取消: 'cancel-timeout',
  管理端取消订单: 'cancel-admin',
  // 船舶绑定申请沿用标题区分来源，多条合并统一提醒不念数量
  有新的船员绑定申请: 'vessel-crew',
  有新的业务员认领船舶申请: 'vessel-sales',
  有新的船舶绑定申请待审核: 'vessel-multi',
};

/**
 * 播报文本 → 预生成音频文件名。
 *
 * 全部为定稿话术的精确匹配；返回 undefined 表示没有现成音频，
 * 回退浏览器 TTS（系统通知等动态标题）。
 */
export function prebuiltVoiceFile(text: string): string | undefined {
  return PREBUILT_VOICE_FILES[text];
}

function prebuiltVoiceUrl(text: string): string | undefined {
  const file = prebuiltVoiceFile(text);
  return file ? `/static/voice/${file}.wav` : undefined;
}

/**
 * 播放预生成语音文件。
 *
 * 与提示音同口径处理自动播放策略：play() 被拦时排队，首次手势后补播。
 * 返回 true 表示已接管播报（播放中或已排队）；false 表示本环境不支持，
 * 调用方应回退 TTS。测试环境（jsdom 无真实媒体播放）直接返回 false。
 */
function tryPlayPrebuilt(url: string): boolean {
  if (import.meta.env.MODE === 'test') return false;
  if (typeof Audio === 'undefined') return false;
  try {
    const audio = new Audio(url);
    audio.preload = 'auto';
    const play = audio.play();
    if (play && typeof play.catch === 'function') {
      play.catch(() => {
        // 自动播放策略拦截：与提示音同口径排队，首次手势后补播
        queueAudioReplay(() => tryPlayPrebuilt(url));
      });
      return true;
    }
    return true;
  } catch {
    // 构造或播放失败（无媒体支持等），回退 TTS
    return false;
  }
}

/**
 * 语音播报：定稿话术播预生成音频，动态话术回退浏览器 TTS。
 *
 * 预生成音频与 Web Speech 一样受浏览器自动播放策略限制：页面还没获得用户手势时
 * 不能直接发声，先排队、等首次手势后补播（与提示音的自动播放解锁同口径）。
 * 不支持的浏览器按「已激活」处理，行为不变。
 */
export function speak(text: string): void {
  if (!text) return;
  try {
    const url = prebuiltVoiceUrl(text);
    if (url && tryPlayPrebuilt(url)) return;
  } catch {
    // 预生成音频异常时回退 TTS
  }
  try {
    if (!hasSpeech()) return;
    if (!hasUserActivation()) {
      queueSpeechReplay(() => speak(text));
      return;
    }
    let blocked = false;
    speakNow(text, () => {
      // 兜底：userActivation 不可用但浏览器仍拦截时（旧版 Chrome），排一次补播；
      // 加 blocked 标记避免补播再被拒时无限重试。
      if (blocked) return;
      blocked = true;
      queueSpeechReplay(() => speakNow(text, () => {}));
    });
  } catch {
    // 播报失败（无音色/被策略拦截）静默降级，铃铛角标仍然更新
  }
}

export function isSpeechSupported(): boolean {
  return hasSpeech();
}

// ==================== 桌面通知 ====================

/** 桌面通知权限状态（不支持的浏览器按 denied 处理，静默降级）。 */
export function desktopPermission(): 'unsupported' | NotificationPermission {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  return Notification.permission;
}

/**
 * 申请桌面通知权限：只能在用户手势里调用（浏览器要求），
 * 因此挂在开关的点击上，不在页面加载时自动弹。
 */
export async function requestDesktopPermission(): Promise<
  'unsupported' | NotificationPermission
> {
  if (desktopPermission() === 'unsupported') return 'unsupported';
  try {
    return await Notification.requestPermission();
  } catch {
    return 'denied';
  }
}

export function showDesktopNotification(
  payload: AlertPayload,
  onClick?: () => void,
): void {
  try {
    if (desktopPermission() !== 'granted') return;
    const notification = new Notification(payload.title, {
      body: payload.body,
      // tag 固定：多条连续通知只留最新一条，不糊满通知中心
      tag: 'aryn-new-message',
    });
    notification.addEventListener('click', () => {
      try {
        window.focus();
        onClick?.();
        notification.close();
      } catch {
        // 点开失败不影响通知本身
      }
    });
  } catch {
    // 部分环境（如无通知服务）构造即抛，静默降级
  }
}
