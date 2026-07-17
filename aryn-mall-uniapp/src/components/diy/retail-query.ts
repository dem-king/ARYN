export function createRetailSortParams(sort: string, priceAscending = false) {
  if (sort === 'sales_price' && priceAscending)
    return { asc: 'sales_price' }
  const fields: Record<string, string> = {
    create_time: 'create_time',
    newest: 'create_time',
    price: 'sales_price',
    sales: 'sales_volume',
    sales_price: 'sales_price',
  }
  return { desc: fields[sort] || 'sales_volume' }
}
