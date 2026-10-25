import http from '@/utils/http'

export interface EveAgentApplication {
  id: string
  name: string
  status: 'ACTIVE' | 'REVOKED' | string
  createdAt: string
  expiresAt: string
  lastUsedAt?: string
  tokenPrefix: string
}

export interface EveAgentCredential {
  application: EveAgentApplication
  token: string
}

export interface EveAgentAudit {
  id: string
  applicationId: string
  applicationName: string
  toolName: string
  success: boolean
  durationMs: number
  resultCount: number
  occurredAt: string
}

const base = '/eve/agents'

export function getEveAgentApplications() {
  return http.get<EveAgentApplication[]>(base)
}

export function createEveAgentApplication(data: { name: string, expiresInDays: number, acknowledgeMailAccess: boolean }) {
  return http.post<EveAgentCredential>(base, data)
}

export function rotateEveAgentCredential(id: string) {
  return http.post<EveAgentCredential>(`${base}/${id}/rotate`)
}

export function revokeEveAgentApplication(id: string) {
  return http.post<void>(`${base}/${id}/revoke`)
}

export function getEveAgentAudits(params: { page: number, size: number }) {
  return http.get<{ list: EveAgentAudit[], total: number }>(`${base}/audit`, params)
}
