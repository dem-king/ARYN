export async function requestDeliverySubscription(templateIds: string[]) {
  const ids = templateIds.filter(Boolean)
  if (!ids.length)
    return false
  try {
    const result = await new Promise<Record<string, string>>((resolve, reject) => {
      const request = uni.requestSubscribeMessage({
        tmplIds: ids,
        success: response => resolve(response as unknown as Record<string, string>),
        fail: reject,
      }) as unknown as Promise<Record<string, string>> | undefined
      request?.then(resolve, reject)
    })
    return ids.some(id => result[id] === 'accept')
  }
  catch {
    return false
  }
}
