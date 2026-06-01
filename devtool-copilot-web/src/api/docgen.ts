import { apiPost } from './http'

export type DocGenSaveTo = 'KB' | 'DELIVERABLE' | 'BOTH' | 'DOWNLOAD'

export interface DocGenRequest {
  projectId: number
  requirement: string
  title?: string | null
  style?: string | null
  saveTo?: DocGenSaveTo | null
  taskId?: number | null
}

export interface DocGenResponse {
  projectId: number
  assetId: number
  filename: string
  downloadUrl: string
  kbDocId?: number | null
  deliverableId?: number | null
}

export const docgenApi = {
  generatePptx(req: DocGenRequest): Promise<DocGenResponse> {
    return apiPost<DocGenResponse>('/api/docgen/pptx', req)
  },
  generateDocx(req: DocGenRequest): Promise<DocGenResponse> {
    return apiPost<DocGenResponse>('/api/docgen/docx', req)
  }
}

