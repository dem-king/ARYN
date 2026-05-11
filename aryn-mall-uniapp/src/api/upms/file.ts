import { alovaInstance } from '@/api/core/instance'

export function uploadImg(filePath: string) {
  return alovaInstance.Post<any>(
    '/upms/file/app/upload',
    {
      filePath,
      name: 'file',
    },
    {
      requestType: 'upload',
      fileType: 'image',
    },
  )
}

export function uploadFile(filePath: string) {
  const authStore = useAuthStore()
  const baseURL = import.meta.env.VITE_OPEN_BOOT === 'true' ? '/boot' : 'upms'
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${import.meta.env.VITE_API_BASE_URL}${baseURL}/file/app/upload`,
      filePath,
      name: 'file',
      header: {
        'tenant-id': import.meta.env.VITE_TENANT_ID,
        'satoken': authStore.getToken,
      },
      success(res) {
        const data = JSON.parse(res.data)
        resolve(data)
      },
      fail: reject,
    })
  })
}
