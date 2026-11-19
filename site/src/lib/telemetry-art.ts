// Hand-authored speed trace for the stats backdrop. Low opacity, decorative.
// Shape approximates a lap with two long straights and three braking zones.

const SPEED_POINTS: number[] = [
  0.3, 0.45, 0.62, 0.78, 0.88, 0.95, 1.0, 1.0, 1.0,
  0.92, 0.7, 0.5, 0.4, 0.35, 0.42, 0.55, 0.7, 0.82, 0.9, 0.96, 1.0, 1.0, 1.0,
  0.95, 0.8, 0.6, 0.45, 0.3, 0.25, 0.32, 0.48, 0.65, 0.78, 0.88, 0.95, 1.0, 1.0,
  0.9, 0.68, 0.48, 0.38, 0.3, 0.22, 0.28, 0.4, 0.55, 0.7, 0.82, 0.9, 0.96, 1.0, 1.0,
  0.95, 0.75, 0.55, 0.4, 0.3, 0.35, 0.5, 0.65, 0.78, 0.88, 0.94, 1.0, 1.0
]

export function speedPath(width: number, height: number): string {
  const n = SPEED_POINTS.length
  const pad = height * 0.12
  const usable = height - pad * 2
  return SPEED_POINTS.map((v, i) => {
    const x = (i / (n - 1)) * width
    const y = pad + (1 - v) * usable
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}

export function speedAreaPath(width: number, height: number): string {
  const line = speedPath(width, height)
  return `${line} L${width},${height} L0,${height} Z`
}