import http from '@/utils/http'

export interface Workspace {
  server: string
  authorizationStatus: 'not_implemented'
  capabilities: {
    key: string
    title: string
    description: string
    status: 'not_implemented'
  }[]
}

export function getWorkspace() {
  return http.get<Workspace>('/eve/workspace')
}
