/// <reference types="astro/client" />
export function initShowcase(): void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const steps = Array.from(document.querySelectorAll<HTMLElement>('[data-step]'))
  const shots = Array.from(document.querySelectorAll<HTMLElement>('[data-shot]'))
  const device = document.querySelector<HTMLElement>('[data-device]')
  if (!steps.length || !shots.length || !device) return

  const activate = (i: number): void => {
    steps.forEach((s, idx) => s.classList.toggle('active', idx === i))
    shots.forEach((s, idx) => s.classList.toggle('active', idx === i))
    device.setAttribute('data-orient', steps[i]?.dataset.orient ?? 'portrait')
    // play active video, pause the rest
    shots.forEach((s, idx) => {
      const v = s.querySelector<HTMLVideoElement>('video')
      if (!v) return
      if (idx === i) v.play().catch(() => {})
      else v.pause()
    })
  }

  if (reduced) {
    steps.forEach((s) => s.classList.add('active'))
    // show posters: hide the stage, show inline figures
    const stage = document.querySelector<HTMLElement>('[data-stage]')
    if (stage) stage.hidden = true
    document.querySelectorAll<HTMLElement>('[data-inline-media]').forEach((m) => {
      m.hidden = false
    })
    return
  }

  // Desktop shows the stage beside the copy, so the reading band is the middle of the
  // viewport. Mobile pins the stage in the top half, so the band moves to the lower half.
  const narrow = window.matchMedia('(max-width: 900px)')
  const band = (): string => (narrow.matches ? '-52% 0px 0px' : '-45% 0px -45% 0px')

  const ratios = new Map<number, number>()
  let observer: IntersectionObserver | null = null

  const pick = (): void => {
    let best = -1
    let bestRatio = 0
    ratios.forEach((r, i) => {
      if (r > bestRatio) {
        best = i
        bestRatio = r
      }
    })
    if (best >= 0) activate(best)
  }

  const observe = (): void => {
    observer?.disconnect()
    ratios.clear()
    observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          const i = steps.indexOf(e.target as HTMLElement)
          if (i >= 0) ratios.set(i, e.isIntersecting ? e.intersectionRatio : 0)
        }
        pick()
      },
      { rootMargin: band(), threshold: [0, 0.2, 0.4, 0.6, 0.8, 1] }
    )
    steps.forEach((s) => observer?.observe(s))
  }

  observe()
  narrow.addEventListener('change', observe)

  // initial
  activate(0)
}
