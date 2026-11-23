/**
 * Regenerates the Android launcher icons from the same obsidian + warm-peach
 * orbit mark used by Antigravity Pulse Desktop (scripts/make-icon.mjs).
 *
 * Run with: node scripts/make-launcher-icons.mjs
 */

import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const RES = resolve(ROOT, 'app', 'src', 'main', 'res')

const ACCENT = [255, 199, 153]
const WHITE = [255, 255, 255]
const BLACK = [0, 0, 0]

const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value))

function smoothstep(edge0, edge1, value) {
  const t = clamp((value - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

function paint(pixel, color, alpha) {
  const amount = clamp(alpha)
  if (amount <= 0) return
  const index = pixel.index * 4
  const backgroundAlpha = pixel.data[index + 3] / 255
  const outAlpha = amount + backgroundAlpha * (1 - amount)
  if (outAlpha <= 0) return
  for (let channel = 0; channel < 3; channel += 1) {
    const foreground = color[channel] * amount
    const background = pixel.data[index + channel] * backgroundAlpha * (1 - amount)
    pixel.data[index + channel] = clamp(Math.round((foreground + background) / outAlpha), 0, 255)
  }
  pixel.data[index + 3] = Math.round(outAlpha * 255)
}

function render(size, round) {
  const data = new Uint8Array(size * size * 4)
  const aa = 1.5 / size
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const px = ((x + 0.5) / size) * 2 - 1
      const py = ((y + 0.5) / size) * 2 - 1
      const pixel = { index: y * size + x, data }
      const frame = round ? 1 - smoothstep(-aa, aa, Math.hypot(px, py) - 0.94) : 1

      paint(pixel, BLACK, frame)
      const ringDistance = Math.abs(Math.hypot(px, py) - 0.53) - 0.085
      paint(pixel, ACCENT, Math.exp(-Math.max(0, ringDistance) * 16) * 0.13 * frame)
      paint(pixel, ACCENT, (1 - smoothstep(-aa, aa, ringDistance)) * frame)

      const satellite = Math.hypot(px - 0.42, py + 0.31) - 0.095
      paint(pixel, WHITE, (1 - smoothstep(-aa, aa, satellite)) * frame * 0.96)

      const core = Math.hypot(px, py) - 0.19
      paint(pixel, ACCENT, (1 - smoothstep(-aa, aa, core)) * frame)
      const centre = Math.hypot(px + 0.025, py + 0.03) - 0.062
      paint(pixel, WHITE, (1 - smoothstep(-aa, aa, centre)) * frame * 0.9)
    }
  }
  return data
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n += 1) {
    let value = n
    for (let k = 0; k < 8; k += 1) {
      value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1
    }
    table[n] = value >>> 0
  }
  return table
})()

function crc32(buffer) {
  let value = 0xffffffff
  for (const byte of buffer) value = CRC_TABLE[(value ^ byte) & 0xff] ^ (value >>> 8)
  return (value ^ 0xffffffff) >>> 0
}

function chunk(type, body) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(body.length, 0)
  const label = Buffer.from(type, 'ascii')
  const checksum = Buffer.alloc(4)
  checksum.writeUInt32BE(crc32(Buffer.concat([label, body])), 0)
  return Buffer.concat([length, label, body, checksum])
}

function encodePng(pixels, size) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])
  const header = Buffer.alloc(13)
  header.writeUInt32BE(size, 0)
  header.writeUInt32BE(size, 4)
  header[8] = 8
  header[9] = 6
  const scanlines = Buffer.alloc(size * (size * 4 + 1))
  for (let y = 0; y < size; y += 1) {
    const rowStart = y * (size * 4 + 1)
    scanlines[rowStart] = 0
    Buffer.from(pixels.buffer, y * size * 4, size * 4).copy(scanlines, rowStart + 1)
  }
  return Buffer.concat([
    signature,
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(scanlines, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

const densities = [
  ['mdpi', 48],
  ['hdpi', 72],
  ['xhdpi', 96],
  ['xxhdpi', 144],
  ['xxxhdpi', 192]
]

for (const [density, size] of densities) {
  const directory = resolve(RES, `mipmap-${density}`)
  mkdirSync(directory, { recursive: true })
  writeFileSync(resolve(directory, 'ic_launcher.png'), encodePng(render(size, false), size))
  writeFileSync(resolve(directory, 'ic_launcher_round.png'), encodePng(render(size, true), size))
}

console.log('[pulse] regenerated Android launcher icons at 5 densities')
