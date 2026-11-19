<script lang="ts">
  import { onMount } from 'svelte'
  import { readConsent, writeConsent, gtmId, cfBeacon, isProdHost } from '../lib/consent'

  let visible = false
  let granted = false

  function loadGtm(): void {
    const id = gtmId()
    if (!id || !isProdHost()) return
    if (document.querySelector(`script[src*="${id}"]`)) return
    // Consent Mode v2 defaults already set inline in Base.astro
    const s = document.createElement('script')
    s.async = true
    s.src = `https://www.googletagmanager.com/gtm.js?id=${id}`
    document.head.appendChild(s)
    // dataLayer push
    window.dataLayer = window.dataLayer || []
    window.dataLayer.push({ event: 'consent_granted' })
  }

  function accept(): void {
    granted = true
    visible = false
    writeConsent('granted')
    loadGtm()
  }

  function decline(): void {
    granted = false
    visible = false
    writeConsent('denied')
  }

  onMount(() => {
    const state = readConsent()
    if (state === 'granted') {
      granted = true
      loadGtm()
    } else if (state === 'unknown') {
      visible = true
    }
    // Cloudflare beacon is loaded by Analytics.astro unconditionally
    void cfBeacon
  })
</script>

{#if visible}
  <div class="banner" role="dialog" aria-label="Cookie consent">
    <p>
      Cloudflare's page counter sets no cookies. A Google Analytics tag loads on every visit. If you
      accept, Google Tag Manager may load tags that set cookies. You can change this later by
      clearing site data.
    </p>
    <div class="actions">
      <button class="btn small" on:click={decline}>Decline</button>
      <button class="btn small primary" on:click={accept}>Accept</button>
    </div>
  </div>
{/if}

<style>
  .banner {
    position: fixed; bottom: 16px; left: 16px; right: 16px; z-index: 30;
    max-width: 560px; margin-inline: auto;
    background: var(--surface0, #1a2226); border: 1px solid var(--surface1, #2c3944);
    border-radius: 12px; padding: 14px 16px;
    display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
    box-shadow: 0 20px 40px -20px rgb(0 0 0 / 0.6);
    font-size: 0.85rem;
  }
  .banner p { margin: 0; flex: 1; min-width: 200px; font-size: 0.85rem; color: var(--subtext, #94a3b8); }
  @media (max-width: 500px) {
    .banner { flex-direction: column; align-items: stretch; }
    .actions { justify-content: flex-end; }
    .actions .btn { flex: 1; justify-content: center; }
  }
  .actions { display: flex; gap: 8px; }
  .btn { padding: 6px 14px; border-radius: 8px; font: 600 0.85rem var(--sans, system-ui); cursor: pointer; border: 1.5px solid var(--text, #f8fafc); background: none; color: var(--text, #f8fafc); }
  .btn.primary { background: var(--mint, #34d399); border-color: var(--mint, #34d399); color: var(--mint-on, #04130d); }
</style>