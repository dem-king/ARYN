export interface LatestRequestCallbacks<T> {
  onError?: (error: unknown) => void
  onSettled?: () => void
  onSuccess?: (value: T) => void
}

export function createLatestRequestRunner() {
  let latestRequestId = 0

  async function run<T>(
    request: () => Promise<T>,
    callbacks: LatestRequestCallbacks<T> = {},
  ) {
    const requestId = ++latestRequestId
    try {
      const value = await request()
      if (requestId === latestRequestId)
        callbacks.onSuccess?.(value)
    }
    catch (error) {
      if (requestId === latestRequestId)
        callbacks.onError?.(error)
    }
    finally {
      if (requestId === latestRequestId)
        callbacks.onSettled?.()
    }
  }

  return { run }
}
