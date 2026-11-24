import posthog from 'posthog-js'
import { posthogKey, posthogHost, isProdHost } from './consent'

let initialised = false

export function initAnalytics(): void {
  if (initialised) return
  const key = posthogKey()
  if (!key) return
  posthog.init(key, {
    api_host: posthogHost(),
    cookieless_mode: 'always',
    autocapture: false,
    capture_pageview: true,
    person_profiles: 'never',
    disable_session_recording: true,
  })
  initialised = true
}

export function track(event: string, properties?: Record<string, unknown>): void {
  if (!initialised) return
  posthog.capture(event, properties)
}

export { isProdHost }