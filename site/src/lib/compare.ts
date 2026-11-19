export function initCompare(): void {
  const tabs = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-compare-tab]'))
  const panels = Array.from(document.querySelectorAll<HTMLElement>('[data-compare-panel]'))
  if (!tabs.length) return

  const activate = (name: string): void => {
    tabs.forEach((t) => {
      const on = t.dataset.compareTab === name
      t.classList.toggle('active', on)
      t.setAttribute('aria-selected', String(on))
      t.tabIndex = on ? 0 : -1
    })
    panels.forEach((p) => {
      const on = p.dataset.comparePanel === name
      p.classList.toggle('active', on)
      p.hidden = !on
    })
  }

  tabs.forEach((t) => {
    t.addEventListener('click', () => activate(t.dataset.compareTab ?? 'video'))
    t.addEventListener('keydown', (e) => {
      const i = tabs.indexOf(t)
      if (e.key === 'ArrowRight') {
        e.preventDefault()
        const n = tabs[(i + 1) % tabs.length]
        if (n) { n.focus(); n.click() }
      }
      if (e.key === 'ArrowLeft') {
        e.preventDefault()
        const n = tabs[(i - 1 + tabs.length) % tabs.length]
        if (n) { n.focus(); n.click() }
      }
    })
  })

  activate('video')
}