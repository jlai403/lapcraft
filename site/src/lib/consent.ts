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
  return import.meta.env.PUBLIC_GTM_ID ?? ''
}

export function cfBeacon(): string {
  return import.meta.env.PUBLIC_CF_BEACON_TOKEN ?? ''
}

export function isProdHost(): boolean {
  if (typeof window === 'undefined') return false
  return window.location.hostname === 'lapcraft.jlai.ca'
}