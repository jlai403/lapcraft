<script lang="ts">
  import { onMount, onDestroy } from 'svelte'

  let container: HTMLElement
  let video: HTMLVideoElement
  let progress = 0
  let reduced = false
  let unlocked = false
  let seekInFlight = false
  let pendingTarget: number | null = null
  let rafId = 0
  let timedOut = 0
  let fallback = false

  const captions = [
    'The replay shows the lap.',
    'The telemetry shows the numbers.',
    'One frame anchor puts them on one clock.'
  ]

  function captionIndex(p: number): number {
    if (p < 0.33) return 0
    if (p < 0.66) return 1
    return 2
  }

  function seekTo(t: number): void {
    if (!video || !video.duration) return
    if (seekInFlight) {
      pendingTarget = t
      return
    }
    seekInFlight = true
    const start = performance.now()
    const done = (): void => {
      seekInFlight = false
      timedOut = 0
      if (pendingTarget !== null) {
        const nt = pendingTarget
        pendingTarget = null
        seekTo(nt)
      }
    }
    const timeout = setTimeout(() => {
      timedOut++
      if (timedOut >= 2) {
        fallback = true
        video.controls = true
      }
      done()
    }, 300)
    const onSeeked = (): void => {
      clearTimeout(timeout)
      video.removeEventListener('seeked', onSeeked)
      done()
    }
    video.addEventListener('seeked', onSeeked, { once: true })
    try {
      video.currentTime = t
    } catch {
      clearTimeout(timeout)
      video.removeEventListener('seeked', onSeeked)
      done()
    }
  }

  function update(): void {
    if (!container || fallback) return
    const rect = container.getBoundingClientRect()
    const vh = window.innerHeight
    const denom = rect.height - vh
    if (denom <= 0) { rafId = requestAnimationFrame(update); return }
    const p = Math.max(0, Math.min(1, -rect.top / denom))
    progress = p
    if (!video || !video.duration || reduced) { rafId = requestAnimationFrame(update); return }
    const target = p * video.duration
    seekTo(target)
    rafId = requestAnimationFrame(update)
  }

  function unlock(): void {
    if (unlocked || !video) return
    video.muted = true
    video.play().then(() => {
      video.pause()
      unlocked = true
    }).catch(() => {})
  }

  onMount(() => {
    reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) return
    const restart = (): void => { if (rafId) cancelAnimationFrame(rafId); rafId = requestAnimationFrame(update) }
    video?.addEventListener('canplay', unlock, { once: true })
    video?.addEventListener('loadedmetadata', restart, { once: true })
    restart()
  })

  onDestroy(() => {
    if (rafId) cancelAnimationFrame(rafId)
  })
</script>

<div class="scrub" bind:this={container}>
  <div class="scrub-sticky">
    <div class="phone">
      {#if fallback}
        <video
          bind:this={video}
          src="/media/scrub-portrait.mp4"
          poster="/media/scrub-portrait-poster.jpg"
          controls
          playsinline
          muted
          preload="auto"
          aria-label="LapCraft replay scrub demo"
        ></video>
      {:else}
        <video
          bind:this={video}
          src="/media/scrub-portrait.mp4"
          poster="/media/scrub-portrait-poster.jpg"
          playsinline
          muted
          preload="auto"
          aria-hidden="true"
          tabindex="-1"
        ></video>
      {/if}
    </div>
    <div class="captions">
      {#each captions as c, i}
        <p class="caption" class:active={captionIndex(progress) === i}>{c}</p>
      {/each}
    </div>
    <div class="rail">
      <div class="rail-fill" style="width: {progress * 100}%"></div>
    </div>
  </div>
</div>

<style>
  .scrub { position: relative; height: 240vh; }
  .scrub-sticky {
    position: sticky; top: calc(var(--nav-h, 64px) + 16px);
    display: flex; flex-direction: column; align-items: center; gap: 20px;
    padding: 24px 0;
  }
  .phone {
    background: var(--crust, #0a0e10);
    border: 2px solid var(--surface1, #2c3944);
    border-radius: 32px; padding: 12px;
    box-shadow: 0 30px 70px -28px rgb(0 0 0 / 0.7);
    width: min(320px, 80vw);
  }
  .phone video { border-radius: 22px; width: 100%; display: block; aspect-ratio: 1080 / 2424; object-fit: cover; }
  .captions { min-height: 3em; text-align: center; }
  .caption {
    margin: 0; font-size: 1.1rem; color: var(--subtext, #94a3b8);
    opacity: 0; transition: opacity 400ms ease; position: absolute; width: 100%; left: 0;
  }
  .caption.active { opacity: 1; position: relative; }
  .rail { width: min(320px, 80vw); height: 4px; background: var(--surface0, #1a2226); border-radius: 2px; overflow: hidden; }
  .rail-fill { height: 100%; background: var(--mint, #34d399); transition: width 80ms linear; }
  @media (max-width: 700px) {
    .phone { width: min(240px, 62vw); }
    .captions { min-height: 2.4em; }
  }
  @media (prefers-reduced-motion: reduce) {
    .scrub { height: auto; }
    .scrub-sticky { position: static; }
    .caption { opacity: 1; position: relative; }
    .rail { display: none; }
  }
</style>