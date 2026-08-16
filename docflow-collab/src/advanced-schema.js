import { Extension, Mark, Node, mergeAttributes } from '@tiptap/core'
import Image from '@tiptap/extension-image'
import { BlockMath, InlineMath } from '@tiptap/extension-mathematics'

export const DocflowTextStyle = Mark.create({
  name: 'docflowTextStyle',
  addAttributes: () => ({
    fontFamily: { default: null, parseHTML: element => element.getAttribute('data-font-family'), renderHTML: attributes => attributes.fontFamily ? { 'data-font-family': attributes.fontFamily } : {} },
    fontSize: { default: null, parseHTML: element => element.getAttribute('data-font-size'), renderHTML: attributes => attributes.fontSize ? { 'data-font-size': attributes.fontSize } : {} },
    textColor: { default: null, parseHTML: element => element.getAttribute('data-text-color'), renderHTML: attributes => attributes.textColor ? { 'data-text-color': attributes.textColor } : {} }
  }),
  parseHTML: () => [{ tag: 'span[data-docflow-style]' }],
  renderHTML: ({ HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes, { 'data-docflow-style': 'true' }), 0]
})

export const BlockFormatting = Extension.create({
  name: 'blockFormatting',
  addGlobalAttributes: () => [{ types: ['paragraph', 'heading', 'blockquote'], attributes: {
    textAlign: { default: null, parseHTML: e => e.getAttribute('data-text-align'), renderHTML: a => a.textAlign ? { 'data-text-align': a.textAlign } : {} },
    lineHeight: { default: null, parseHTML: e => e.getAttribute('data-line-height'), renderHTML: a => a.lineHeight ? { 'data-line-height': a.lineHeight } : {} },
    indent: { default: 0, parseHTML: e => Number(e.getAttribute('data-indent') || 0), renderHTML: a => a.indent ? { 'data-indent': a.indent } : {} }
  } }, { types: ['heading'], attributes: {
    collapsed: { default: false, parseHTML: e => e.getAttribute('data-collapsed') === 'true', renderHTML: a => a.collapsed ? { 'data-collapsed': 'true' } : {} }
  } }]
})

export const EnhancedBlockMath = BlockMath.extend({
  addAttributes() {
    return { ...this.parent?.(),
      mathml: { default: null, parseHTML: e => e.getAttribute('data-mathml'), renderHTML: a => a.mathml ? { 'data-mathml': a.mathml } : {} },
      equationLabel: { default: null, parseHTML: e => e.getAttribute('data-equation-label'), renderHTML: a => a.equationLabel ? { 'data-equation-label': a.equationLabel } : {} },
      equationNumber: { default: null, parseHTML: e => e.getAttribute('data-equation-number'), renderHTML: a => a.equationNumber ? { 'data-equation-number': a.equationNumber } : {} },
      formulaKind: { default: 'math', parseHTML: e => e.getAttribute('data-formula-kind') || 'math', renderHTML: a => ({ 'data-formula-kind': a.formulaKind || 'math' }) }
    }
  }
})

export const EnhancedInlineMath = InlineMath.extend({
  addAttributes() {
    return { ...this.parent?.(),
      mathml: { default: null, parseHTML: e => e.getAttribute('data-mathml'), renderHTML: a => a.mathml ? { 'data-mathml': a.mathml } : {} }
    }
  }
})

const inlineAtom = (name, tag, attrs, text) => Node.create({
  name, group: 'inline', inline: true, atom: true, selectable: true,
  addAttributes: () => attrs,
  parseHTML: () => [{ tag: `${tag}[data-type="${name.replace(/[A-Z]/g, m => `-${m.toLowerCase()}`)}"]` }],
  renderHTML: ({ node, HTMLAttributes }) => [tag, mergeAttributes(HTMLAttributes, { 'data-type': name.replace(/[A-Z]/g, m => `-${m.toLowerCase()}`) }), text(node)]
})

export const EquationReference = inlineAtom('equationReference', 'span', {
  label: { default: '', parseHTML: e => e.getAttribute('data-equation-label') || '', renderHTML: a => ({ 'data-equation-label': a.label }) },
  number: { default: '?', parseHTML: e => e.getAttribute('data-equation-number') || '?', renderHTML: a => ({ 'data-equation-number': a.number }) }
}, node => `式（${node.attrs.number || '?'}）`)
export const Footnote = inlineAtom('footnote', 'sup', {
  noteId: { default: '', parseHTML: e => e.getAttribute('data-footnote-id') || '', renderHTML: a => ({ 'data-footnote-id': a.noteId }) },
  noteText: { default: '', parseHTML: e => e.getAttribute('data-footnote-text') || '', renderHTML: a => ({ 'data-footnote-text': a.noteText }) }
}, node => `[${node.attrs.noteId}]`)
export const Citation = inlineAtom('citation', 'span', {
  key: { default: '', parseHTML: e => e.getAttribute('data-citation-key') || '', renderHTML: a => ({ 'data-citation-key': a.key }) },
  title: { default: '', parseHTML: e => e.getAttribute('data-citation-title') || '', renderHTML: a => ({ 'data-citation-title': a.title }) },
  url: { default: '', parseHTML: e => e.getAttribute('data-citation-url') || '', renderHTML: a => a.url ? { 'data-citation-url': a.url } : {} }
}, node => `[${node.attrs.key}]`)

export const LazyImage = Image.extend({
  addAttributes() { return { ...this.parent?.(), loading: { default: 'lazy', renderHTML: () => ({ loading: 'lazy' }) }, decoding: { default: 'async', renderHTML: () => ({ decoding: 'async' }) } } }
})
