import {
  Bell,
  Box,
  ChatDotRound,
  Clock,
  EditPen,
  Files,
  Grid,
  Headset,
  Location,
  LocationInformation,
  MagicStick,
  RefreshLeft,
  Ship,
  Star,
  Ticket,
  TrendCharts,
  User,
  Van,
  Wallet,
} from '@element-plus/icons-vue';

export const services = {
  message: { icon: Bell, label: '消息中心' },
  membership: { icon: User, label: '会员中心' },
  address: { icon: Location, label: '收货地址' },
  review: { icon: EditPen, label: '我的评价' },
  footprint: { icon: Clock, label: '浏览记录' },
  favorite: { icon: Star, label: '我的收藏' },
  coupon: { icon: Ticket, label: '我的优惠券' },
  support: { icon: Headset, label: '联系客服' },
};

export const orders = {
  payment: { icon: Wallet, label: '待付款' },
  shipment: { icon: Box, label: '待发货' },
  receipt: { icon: Van, label: '待收货' },
  review: { icon: ChatDotRound, label: '待评价' },
  refund: { icon: RefreshLeft, label: '退款/售后' },
};

export const workbenches = {
  delivery: { icon: Van, label: '配送工作台' },
  vessel: { icon: Ship, label: '我的船舶' },
  sales: { icon: TrendCharts, label: '销售工作台' },
};

export const assetLabels = {
  coupon: '优惠券',
  points: '积分',
  balance: '余额',
};

export const customIcons = {
  bookmark: { icon: Files, label: '书签' },
  pin: { icon: LocationInformation, label: '图钉' },
  star: { icon: Star, label: '星标' },
  grid: { icon: Grid, label: '网格' },
  sparkles: { icon: MagicStick, label: '灵感' },
};

export const recommendationLabels = {
  recommended: '系统推荐',
  member: '会员专区',
  clearance: '出清专区',
};

export type UserCenterPreviewMode =
  'enterprise' | 'guest' | 'personal' | 'staff';

export type UserCenterModule =
  | 'assets'
  | 'contentPosition'
  | 'hero'
  | 'orders'
  | 'recommendations'
  | 'services'
  | 'workbenches';
