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

export function posthogKey(): string {
  const key = import.meta.env.PUBLIC_POSTHOG_KEY ?? ''
  // Only a well-formed project key may reach the inline script.
  return /^phc_[A-Za-z0-9]{20,}$/.test(key) ? key : ''
}

export function posthogHost(): string {
  const host = import.meta.env.PUBLIC_POSTHOG_HOST ?? ''
  return /^https:\/\/[a-z0-9.-]+\.i\.posthog\.com$/.test(host) ? host : 'https://us.i.posthog.com'
}

export function cfBeacon(): string {
  const token = import.meta.env.PUBLIC_CF_BEACON_TOKEN ?? ''
  return /^[0-9a-f]{16,64}$/.test(token) ? token : ''
}

export function isProdHost(): boolean {
  if (typeof window === 'undefined') return false
  return window.location.hostname === 'lapcraft.jlai.ca'
}