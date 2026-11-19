export function initReveal(): void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const revealable = document.querySelectorAll('.reveal, [data-on]')

  const revealNow = (el: Element): void => {
    if (el.classList.contains('in')) return
    el.classList.add('in')
    io.unobserve(el)
  }

  const io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) revealNow(e.target)
      }
    },
    { rootMargin: '0px 0px -12% 0px', threshold: 0.12 }
  )

  revealable.forEach((el, i) => {
    const h = el as HTMLElement
    if (!h.style.getPropertyValue('--i')) {
      h.style.setProperty('--i', String(i % 6))
    }
    if (reduced) {
      setTimeout(() => revealNow(el))
    } else {
      io.observe(el)
    }
  })

  // reveal anything still hidden at the bottom of a tall viewport
  const revealAtEnd = (): void => {
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 100) {
      revealable.forEach(revealNow)
    }
  }

  if (!reduced) {
    let ticking = false
    const onScroll = (): void => {
      if (ticking) return
      ticking = true
      requestAnimationFrame(() => {
        revealAtEnd()
        ticking = false
      })
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    onScroll()
    // safety: reveal anything still hidden shortly after load
    setTimeout(() => revealable.forEach(revealNow), 1500)
  }
}