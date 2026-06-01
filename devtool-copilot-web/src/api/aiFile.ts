import axios from 'axios'

export interface AiFileUploadResponse {
  projectId: number
  assetId: number
  filename: string
  contentType?: string | null
  extractedChars: number
  extractedText: string
}

export async function uploadAiFile(projectId: number, file: File): Promise<AiFileUploadResponse> {
  const token = localStorage.getItem('dtc_token')
  const form = new FormData()
  form.append('file', file)
  const resp = await axios.request({
    url: `/api/ai/file/upload?projectId=${encodeURIComponent(String(projectId))}`,
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    data: form,
    validateStatus: () => true
  })
  const payload = resp.data as any
  if (!payload || typeof payload !== 'object') throw new Error('上传失败')
  if (payload.code !== 0) throw new Error(payload.message || '上传失败')
  return payload.data as AiFileUploadResponse
}

