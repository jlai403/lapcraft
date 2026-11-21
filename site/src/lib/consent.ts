/// <reference types="astro/client" />
export type ConsentState = 'unknown' | 'granted' | 'denied'

const KEY = 'lc-consent'

export function readConsent(): ConsentState {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'granted' || v === 'denied') return v
  } catch {
    // ignore
  }
  return 'unknown'
}

export function writeConsent(state: 'granted' | 'denied'): void {
  try {
    localStorage.setItem(KEY, state)
  } catch {
    // ignore
  }
}

export function gtmId(): string {
  const id = import.meta.env.PUBLIC_GTM_ID ?? ''
  // Only a well-formed container id may reach the inline script and selector.
  return /^GTM-[A-Z0-9]{4,12}$/.test(id) ? id : ''
}

export function cfBeacon(): string {
  const token = import.meta.env.PUBLIC_CF_BEACON_TOKEN ?? ''
  return /^[0-9a-f]{16,64}$/.test(token) ? token : ''
}

export function isProdHost(): boolean {
  if (typeof window === 'undefined') return false
  return window.location.hostname === 'lapcraft.jlai.ca'
}