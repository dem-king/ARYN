/** 出清商品基础价，与后台商品规则试算采用相同的分位四舍五入。 */
export function clearanceCandidate(
  originalPrice: null | number | undefined,
  fixedPrice: null | number | undefined,
  rate: null | number | undefined,
) {
  if (fixedPrice && fixedPrice > 0) return fixedPrice;
  if (originalPrice && originalPrice > 0 && rate && rate > 0) {
    const cents = Math.round(originalPrice * 100);
    const rateUnits = Math.round(rate * 10_000);
    return Math.round((cents * rateUnits) / 10_000) / 100;
  }
  return null;
}
