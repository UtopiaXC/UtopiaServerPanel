import { api, unwrap } from './client'
import type { ProfilePrivacyConfig, SocialAccountItem } from './profile'

export interface PublicMcOnlineStatus {
  online: boolean
  dimension: string | null
  gamemode: string | null
  health: number | null
  maxHealth: number | null
  deathCount?: number
}

export interface PublicMcStatItem {
  id: string
  value: number
}

export interface PublicMcStats {
  available: boolean
  format?: 'modern' | 'legacy'
  playTimeSeconds: number
  summary?: Record<string, number>
  topMined?: PublicMcStatItem[]
  topCrafted?: PublicMcStatItem[]
  topUsed?: PublicMcStatItem[]
  topKilled?: PublicMcStatItem[]
  topKilledBy?: PublicMcStatItem[]
  customEntries?: PublicMcStatItem[]
}

export interface PublicMcAchievementItem {
  id: string
  category: string
  done: boolean
  achievedAt: string | null
  criteriaCount: number
}

export interface PublicMcAchievements {
  available: boolean
  completedCount: number
  inProgressCount: number
  recipesUnlocked: number
  items: PublicMcAchievementItem[]
}

export interface PublicMcCharacter {
  playerUuid: string
  playerName: string
  primary: boolean
  boundAt: number
  onlineStatus?: PublicMcOnlineStatus
  playTimeSeconds?: number
  stats?: PublicMcStats
  achievements?: PublicMcAchievements
}

export interface PublicPlayerProfile {
  exists: boolean
  publicProfileEnabled: boolean
  isOwner: boolean
  visibleFields?: ProfilePrivacyConfig
  username?: string
  avatarData?: string
  nickname?: string
  bio?: string
  email?: string | null
  gender?: string
  birthday?: string
  socialAccounts?: SocialAccountItem[]
  roleName?: string
  roleDisplayName?: string
  createdAt?: number
  mcCharacters?: PublicMcCharacter[]
}

export const publicProfileApi = {
  getByUsername: (username: string) =>
    unwrap<PublicPlayerProfile>(api.get(`/public/users/${encodeURIComponent(username)}`)),
  getMcSkinUrl: (uuid: string, name?: string) => {
    const params = new URLSearchParams()
    if (uuid) params.set('uuid', uuid)
    if (name) params.set('name', name)
    return `/api/public/mc-skin?${params.toString()}`
  }
}
