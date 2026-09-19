import DOMPurify from 'dompurify'
import { marked } from 'marked'
import TurndownService from 'turndown'
import { gfm } from 'turndown-plugin-gfm'

marked.setOptions({ gfm: true, breaks: true })

function createTurndown() {
  // HTML 无法完全无损还原为 Markdown；这里固定降级规则以保证结果可预测。
  const service = new TurndownService({
    headingStyle: 'atx',
    bulletListMarker: '-',
    codeBlockStyle: 'fenced',
    emDelimiter: '*',
    strongDelimiter: '**'
  })
  service.use(gfm)
  service.addRule('strikethrough', {
    filter: ['del', 's', 'strike'],
    replacement: content => `~~${content}~~`
  })
  // Markdown has no standard underline syntax, so safe <u> markup is retained predictably.
  service.keep(['u'])
  service.addRule('emptyParagraph', {
    filter: node => node.nodeName === 'P' && !node.textContent && !node.querySelector('img'),
    replacement: () => '\n\n'
  })
  return service
}

export function sanitizeHtml(html) {
  // 仅额外放行编辑器扩展所需属性，事件处理器和危险标签仍由 DOMPurify 移除。
  return DOMPurify.sanitize(html || '', {
    ADD_ATTR: ['target', 'rel', 'loading', 'decoding', 'data-type', 'data-latex', 'data-comment-id', 'data-change-id',
      'data-change-type', 'data-author-id', 'data-author-name', 'data-last-edit-by', 'data-last-edit-name',
      'data-docflow-style', 'data-font-family', 'data-font-size', 'data-text-color', 'data-text-align', 'data-mathml',
      'data-line-height', 'data-indent', 'data-collapsed', 'data-equation-label', 'data-equation-number',
      'data-formula-kind', 'data-colwidth', 'data-footnote-id', 'data-footnote-text', 'data-citation-key', 'data-citation-title', 'data-citation-url'],
    USE_PROFILES: { html: true }
  })
}

export function markdownToHtml(markdown) {
  return sanitizeHtml(marked.parse(markdown || ''))
}

export function htmlToMarkdown(html) {
  return createTurndown().turndown(sanitizeHtml(html || ''))
}
