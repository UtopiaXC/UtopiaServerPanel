const USERNAME_REGEX = /^[a-zA-Z0-9_]{4,32}$/

const RESERVED_USERNAMES = new Set([
  'login',
  'logout',
  'register',
  'setup',
  'home',
  'tokens',
  'settings',
  'profiler',
  'mods',
  'whitelist',
  'messages',
  'forbidden',
  'bind',
  'activate',
  'assets',
  'static',
  'favicon',
  'index',
  'health',
  'status',
  'overview',
  'terminal',
  'logs',
  'public',
  'profile',
  'robots'
])

export function validateUsernameErrorKey(raw: string): 'INVALID_USERNAME' | 'RESERVED_USERNAME' | null {
  const trimmed = (raw || '').trim()
  if (!USERNAME_REGEX.test(trimmed)) {
    return 'INVALID_USERNAME'
  }
  if (RESERVED_USERNAMES.has(trimmed.toLowerCase())) {
    return 'RESERVED_USERNAME'
  }
  return null
}
