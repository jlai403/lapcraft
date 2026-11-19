export function startScrollDevices(): void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const connectors = document.querySelectorAll<HTMLElement>('[data-connector]')
  const stage = document.querySelector<HTMLElement>('.stage')

  if (reduced) {
    connectors.forEach((c) => {
      c.style.setProperty('--p', '1')
      c.classList.add('lit')
    })
    return
  }

  const drawConnectors = (): void => {
    const vh = window.innerHeight
    connectors.forEach((c) => {
      const track = c.querySelector<HTMLElement>('.track')
      if (!track) return
      const r = track.getBoundingClientRect()
      const p = Math.max(0, Math.min(1, (vh * 0.85 - r.top) / (r.height + vh * 0.3)))
      c.style.setProperty('--p', p.toFixed(3))
      c.classList.toggle('lit', p >= 1)
    })
  }

  const updateDepth = (): void => {
    if (!stage) return
    if (window.matchMedia('(max-width: 900px)').matches) return
    const panels = stage.querySelectorAll<HTMLElement>('.stage-panel')
    panels.forEach((p, i) => {
      const factor = [0.04, 0.08, 0.12] as const
      const f = factor[i] ?? 0.06
      const t = Math.min(16, window.scrollY * f)
      p.style.transform = `translate3d(0, ${-t}px, 0)`
    })
  }

  let ticking = false
  const onScroll = (): void => {
    if (ticking) return
    ticking = true
    requestAnimationFrame(() => {
      drawConnectors()
      updateDepth()
      ticking = false
    })
  }

  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll)
  onScroll()
}