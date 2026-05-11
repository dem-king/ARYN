export const defaultCommonStyle: any = {
  styleTopMargin: 0,
  styleBottomMargin: 0,
  styleLeftMargin: 0,
  styleRightMargin: 0,
  styleTopPadding: 0,
  styleBottomPadding: 0,
  styleLeftPadding: 0,
  styleRightPadding: 0,
  styleLtRadius: 0,
  styleRtRadius: 0,
  styleLbRadius: 0,
  styleRbRadius: 0,
  bgColorDirection: 'to right',
  bgStartColor: '',
  bgEndColor: '',
  bgPicUrl: '',
}

/**
 * 将 DiyCommonStyle 转换为 CSS 样式对象
 */
export function getCommonStyle(
  style?: any | null,
): Record<string, string> {
  const s = style ?? defaultCommonStyle

  const bgStart = s.bgStartColor
  const bgEnd = s.bgEndColor
  const hasGradient = bgStart || bgEnd

  const styles: Record<string, string> = {
    marginTop: `${s.styleTopMargin}px`,
    marginLeft: `${s.styleLeftMargin}px`,
    marginRight: `${s.styleRightMargin}px`,
    marginBottom: `${s.styleBottomMargin}px`,
    paddingTop: `${s.styleTopPadding}px`,
    paddingLeft: `${s.styleLeftPadding}px`,
    paddingRight: `${s.styleRightPadding}px`,
    paddingBottom: `${s.styleBottomPadding}px`,
    borderTopLeftRadius: `${s.styleLtRadius}px`,
    borderTopRightRadius: `${s.styleRtRadius}px`,
    borderBottomLeftRadius: `${s.styleLbRadius}px`,
    borderBottomRightRadius: `${s.styleRbRadius}px`,
  }

  // 背景图或渐变背景
  if (s.bgPicUrl) {
    styles.background = `url(${s.bgPicUrl})`
    styles.backgroundRepeat = 'no-repeat'
    styles.backgroundPosition = 'center'
    styles.backgroundSize = '100% 100%'
  }
  else {
    styles.background = hasGradient
      ? `linear-gradient(${s.bgColorDirection || 'to right'}, ${bgStart || bgEnd}, ${bgEnd || bgStart})`
      : 'transparent'
  }
  return styles
}
