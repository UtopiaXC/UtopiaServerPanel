export function base64UrlToBuffer(base64url: string): ArrayBuffer {
  const padded = base64url.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((base64url.length + 3) % 4)
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

export function bufferToBase64Url(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

export function isWebAuthnSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.PublicKeyCredential !== 'undefined' &&
    Boolean(navigator.credentials?.create) &&
    Boolean(navigator.credentials?.get)
  )
}

export async function createPasskeyCredential(publicKeyOptions: any) {
  const options: PublicKeyCredentialCreationOptions = {
    ...publicKeyOptions,
    challenge: base64UrlToBuffer(publicKeyOptions.challenge),
    user: {
      ...publicKeyOptions.user,
      id: base64UrlToBuffer(publicKeyOptions.user.id)
    },
    excludeCredentials: (publicKeyOptions.excludeCredentials || []).map((item: any) => ({
      ...item,
      id: base64UrlToBuffer(item.id)
    }))
  }
  const cred = (await navigator.credentials.create({ publicKey: options })) as PublicKeyCredential | null
  if (!cred) {
    throw new Error('Passkey registration was cancelled')
  }
  const resp = cred.response as AuthenticatorAttestationResponse
  return {
    id: cred.id,
    rawId: bufferToBase64Url(cred.rawId),
    type: cred.type,
    response: {
      clientDataJSON: bufferToBase64Url(resp.clientDataJSON),
      attestationObject: bufferToBase64Url(resp.attestationObject),
      transports: typeof resp.getTransports === 'function' ? resp.getTransports() : []
    }
  }
}

export async function getPasskeyAssertion(publicKeyOptions: any) {
  const options: PublicKeyCredentialRequestOptions = {
    ...publicKeyOptions,
    challenge: base64UrlToBuffer(publicKeyOptions.challenge),
    allowCredentials: (publicKeyOptions.allowCredentials || []).map((item: any) => ({
      ...item,
      id: base64UrlToBuffer(item.id)
    }))
  }
  const cred = (await navigator.credentials.get({ publicKey: options })) as PublicKeyCredential | null
  if (!cred) {
    throw new Error('Passkey verification was cancelled')
  }
  const resp = cred.response as AuthenticatorAssertionResponse
  return {
    id: cred.id,
    rawId: bufferToBase64Url(cred.rawId),
    type: cred.type,
    response: {
      clientDataJSON: bufferToBase64Url(resp.clientDataJSON),
      authenticatorData: bufferToBase64Url(resp.authenticatorData),
      signature: bufferToBase64Url(resp.signature),
      userHandle: resp.userHandle ? bufferToBase64Url(resp.userHandle) : null
    }
  }
}
