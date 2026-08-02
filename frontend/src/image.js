export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

const MAX_EDGE = 2048
const MAX_BYTES = 10 * 1024 * 1024

const toBlob = (canvas, type, quality) => new Promise((resolve, reject) =>
  canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not process that image'))), type, quality))

function check(file) {
  if (!IMAGE_TYPES.includes(file.type)) throw new Error('Only JPEG, PNG, GIF and WebP images are supported')
}

function checkSize(blob) {
  if (blob.size > MAX_BYTES) throw new Error('Images can be up to 10 MB')
  return blob
}

// big photos are scaled down before upload. gifs stay as they are so they keep animating
export async function prepareImage(file) {
  check(file)
  const bitmap = await createImageBitmap(file)
  const { width, height } = bitmap
  const scale = Math.min(1, MAX_EDGE / Math.max(width, height))
  if (scale === 1 || file.type === 'image/gif') {
    bitmap.close()
    return { blob: checkSize(file), width, height }
  }

  const canvas = document.createElement('canvas')
  canvas.width = Math.round(width * scale)
  canvas.height = Math.round(height * scale)
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const blob = await toBlob(canvas, file.type === 'image/png' ? 'image/png' : 'image/jpeg', 0.85)
  return { blob: checkSize(blob), width: canvas.width, height: canvas.height }
}

// centre square, so the round avatar shows what you expect
export async function squareAvatar(file, size = 512) {
  check(file)
  const bitmap = await createImageBitmap(file)
  const side = Math.min(bitmap.width, bitmap.height)
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, size, size)
  ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size)
  bitmap.close()
  return toBlob(canvas, 'image/jpeg', 0.9)
}
