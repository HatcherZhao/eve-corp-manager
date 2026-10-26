import http from '@/utils/http'

export interface EveAgentApplication {
  id: string
  name: string
  status: 'ACTIVE' | 'REVOKED' | string
  createdAt: string
  expiresAt: string
  lastUsedAt?: string
  tokenPrefix: string
  invitationCode: string
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

export interface EveAgentGrant {
  id: string
  applicationId: string
  applicationName: string
  corporationId: string
  corporationName: string
  corporationTicker: string
  status: 'ACTIVE' | 'REVOKED' | string
  applicationStatus: 'ACTIVE' | 'REVOKED' | 'DELETED' | string
  applicationExpiresAt: string | null
  grantedAt: string
  revokedAt?: string
}

export interface EveAgentInvitation {
  applicationId: string
  applicationName: string
  expiresAt: string
  ownerCorporationName: string
  corporationId: string
  corporationName: string
  corporationTicker: string
  grantStatus: 'AVAILABLE' | 'ACTIVE' | 'REVOKED' | string
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

export function getEveAgentApplicationGrants(id: string) {
  return http.get<EveAgentGrant[]>(`${base}/${id}/grants`)
}

export function getEveAgentCorporationGrants() {
  return http.get<EveAgentGrant[]>(`${base}/grants`)
}

export function getEveAgentInvitation(code: string) {
  return http.get<EveAgentInvitation>(`${base}/invitations/${encodeURIComponent(code)}`)
}

export function approveEveAgentInvitation(code: string) {
  return http.post<EveAgentGrant>(`${base}/invitations/${encodeURIComponent(code)}/approve`, {
    acknowledgeMailAccess: true,
  })
}

export function revokeEveAgentGrant(id: string) {
  return http.post<void>(`${base}/grants/${id}/revoke`)
}

export function getEveAgentAudits(params: { page: number, size: number }) {
  return http.get<{ list: EveAgentAudit[], total: number }>(`${base}/audit`, params)
}
