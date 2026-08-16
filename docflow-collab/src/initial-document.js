import { TiptapTransformer } from '@hocuspocus/transformer'
import { generateHTML, generateJSON } from '@tiptap/html/server'
import { marked } from 'marked'
import sanitizeHtml from 'sanitize-html'
import { editorExtensions } from './schema.js'

const allowedTags = [
  'p', 'br', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'strong', 'b', 'em', 'i', 'u',
  's', 'strike', 'del', 'ul', 'ol', 'li', 'a', 'blockquote', 'code', 'pre', 'table',
  'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'img', 'hr', 'span', 'div', 'input', 'sup'
]

export function createInitialDocument(access) {
  const source = access.initialContent || ''
  const html = access.initialContentFormat === 'MARKDOWN' ? marked.parse(source) : source
  const safe = sanitizeHtml(html, {
    allowedTags,
    allowedAttributes: {
      a: ['href', 'target', 'rel'], img: ['src', 'alt', 'title', 'loading', 'decoding'],
      th: ['colspan', 'rowspan', 'data-colwidth'], td: ['colspan', 'rowspan', 'data-colwidth'],
      input: ['type', 'checked', 'disabled'], '*': ['class', 'title', 'data-type', 'data-latex', 'data-mathml', 'data-comment-id', 'data-change-id', 'data-change-type', 'data-author-id', 'data-author-name', 'data-last-edit-by', 'data-last-edit-name', 'data-docflow-style', 'data-font-family', 'data-font-size', 'data-text-color', 'data-text-align', 'data-line-height', 'data-indent', 'data-collapsed', 'data-equation-label', 'data-equation-number', 'data-formula-kind', 'data-footnote-id', 'data-footnote-text', 'data-citation-key', 'data-citation-title', 'data-citation-url']
    },
    allowedSchemes: ['http', 'https', 'mailto'],
    allowProtocolRelative: false
  })
  const json = generateJSON(safe || '<p></p>', editorExtensions)
  return TiptapTransformer.toYdoc(json, 'default', editorExtensions)
}

export function documentToHtml(document) {
  const json = TiptapTransformer.fromYdoc(document, 'default')
  return sanitizeHtml(generateHTML(json, editorExtensions), {
    allowedTags,
    allowedAttributes: {
      a: ['href', 'target', 'rel'], img: ['src', 'alt', 'title', 'loading', 'decoding'],
      th: ['colspan', 'rowspan', 'data-colwidth'], td: ['colspan', 'rowspan', 'data-colwidth'],
      input: ['type', 'checked', 'disabled'], '*': ['class', 'title', 'data-type', 'data-latex', 'data-mathml', 'data-comment-id', 'data-change-id', 'data-change-type', 'data-author-id', 'data-author-name', 'data-last-edit-by', 'data-last-edit-name', 'data-docflow-style', 'data-font-family', 'data-font-size', 'data-text-color', 'data-text-align', 'data-line-height', 'data-indent', 'data-collapsed', 'data-equation-label', 'data-equation-number', 'data-formula-kind', 'data-footnote-id', 'data-footnote-text', 'data-citation-key', 'data-citation-title', 'data-citation-url']
    },
    allowedSchemes: ['http', 'https', 'mailto'],
    allowProtocolRelative: false
  })
}
