import { describe, expect, it } from 'vitest'
import { clipboardImageFiles } from './clipboardImages'

describe('clipboardImageFiles', () => {
  it('extracts only image files and removes duplicate clipboard entries', () => {
    const image = new File(['image'], 'screen.png', { type: 'image/png', lastModified: 7 })
    const text = new File(['text'], 'note.txt', { type: 'text/plain', lastModified: 8 })
    const clipboardData = {
      items: [
        { kind: 'file', type: 'image/png', getAsFile: () => image },
        { kind: 'file', type: 'image/png', getAsFile: () => image },
        { kind: 'file', type: 'text/plain', getAsFile: () => text }
      ],
      files: [image]
    }

    expect(clipboardImageFiles(clipboardData)).toEqual([image])
  })
})
