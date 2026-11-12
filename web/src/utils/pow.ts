import { sha256HexSync } from './sha256'

export interface PowChallenge {
  algorithm: string
  challenge: string
  maxnumber: number
  salt: string
  signature: string
}

async function computeSha256Hex(encoder: TextEncoder, text: string): Promise<string> {
  const data = encoder.encode(text)
  if (typeof window !== 'undefined' && window.crypto?.subtle) {
    const hashBuf = await window.crypto.subtle.digest('SHA-256', data)
    const arr = Array.from(new Uint8Array(hashBuf))
    return arr.map((b) => b.toString(16).padStart(2, '0')).join('')
  }
  return sha256HexSync(data)
}

export async function solvePowChallenge(ch: PowChallenge): Promise<string> {
  const encoder = new TextEncoder()
  const max = ch.maxnumber || 100000
  const useNative = typeof window !== 'undefined' && Boolean(window.crypto?.subtle)

  for (let n = 0; n <= max; n++) {
    const hex = await computeSha256Hex(encoder, ch.salt + n)
    if (hex === ch.challenge) {
      const payload = JSON.stringify({
        algorithm: ch.algorithm,
        challenge: ch.challenge,
        number: n,
        salt: ch.salt,
        signature: ch.signature
      })
      return btoa(payload)
    }
    // Yield to browser UI every ~600 synchronous steps when native WebCrypto isn't available
    if (!useNative && n % 600 === 0) {
      await new Promise((r) => setTimeout(r, 0))
    }
  }
  throw new Error('Failed to solve security challenge')
}

