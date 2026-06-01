import { apiGet, apiPost } from './http'

export interface KbExternalDocItem {
  id: number
  projectId?: number | null
  title: string
  url?: string | null
  updateTime?: string | null
}

export const kbApi = {
  createExternalDoc(input: { projectId?: number | null; title: string; url?: string | null; content: string }): Promise<number> {
    return apiPost<number>('/api/kb/external/create', {
      projectId: input.projectId ?? undefined,
      title: input.title,
      url: input.url ?? undefined,
      content: input.content
    })
  },

  listExternalDocs(input?: { projectId?: number | null; q?: string | null; limit?: number }): Promise<KbExternalDocItem[]> {
    return apiGet<KbExternalDocItem[]>('/api/kb/external/list', {
      projectId: input?.projectId ?? undefined,
      q: input?.q ?? undefined,
      limit: input?.limit ?? undefined
    })
  }
}

