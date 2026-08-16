import { Extension, Mark, Node, mergeAttributes } from '@tiptap/core'
import Image from '@tiptap/extension-image'
import { BlockMath, InlineMath } from '@tiptap/extension-mathematics'
import { TableRow } from '@tiptap/extension-table'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

const styleAttributes = {
  fontFamily: { default: null, parseHTML: element => element.getAttribute('data-font-family'), renderHTML: attributes => attributes.fontFamily ? { 'data-font-family': attributes.fontFamily } : {} },
  fontSize: { default: null, parseHTML: element => element.getAttribute('data-font-size'), renderHTML: attributes => attributes.fontSize ? { 'data-font-size': attributes.fontSize } : {} },
  textColor: { default: null, parseHTML: element => element.getAttribute('data-text-color'), renderHTML: attributes => attributes.textColor ? { 'data-text-color': attributes.textColor } : {} }
}

export const DocflowTextStyle = Mark.create({
  name: 'docflowTextStyle',
  addAttributes: () => styleAttributes,
  parseHTML: () => [{ tag: 'span[data-docflow-style]' }],
  renderHTML: ({ HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes, { 'data-docflow-style': 'true' }), 0]
})

export const BlockFormatting = Extension.create({
  name: 'blockFormatting',
  addGlobalAttributes() {
    return [{
      types: ['paragraph', 'heading', 'blockquote'],
      attributes: {
        textAlign: { default: null, parseHTML: element => element.getAttribute('data-text-align'), renderHTML: attributes => attributes.textAlign ? { 'data-text-align': attributes.textAlign } : {} },
        lineHeight: { default: null, parseHTML: element => element.getAttribute('data-line-height'), renderHTML: attributes => attributes.lineHeight ? { 'data-line-height': attributes.lineHeight } : {} },
        indent: { default: 0, parseHTML: element => Number(element.getAttribute('data-indent') || 0), renderHTML: attributes => attributes.indent ? { 'data-indent': Math.min(8, Math.max(0, Number(attributes.indent))) } : {} }
      }
    }, {
      types: ['heading'],
      attributes: {
        collapsed: { default: false, parseHTML: element => element.getAttribute('data-collapsed') === 'true', renderHTML: attributes => attributes.collapsed ? { 'data-collapsed': 'true' } : {} }
      }
    }]
  },
  addCommands() {
    return {
      setBlockFormatting: attributes => ({ commands }) => {
        const type = this.editor.state.selection.$from.parent.type.name
        return ['paragraph', 'heading', 'blockquote'].includes(type) && commands.updateAttributes(type, attributes)
      },
      changeIndent: delta => ({ commands, editor }) => {
        const node = editor.state.selection.$from.parent
        return commands.updateAttributes(node.type.name, { indent: Math.min(8, Math.max(0, Number(node.attrs.indent || 0) + delta)) })
      },
      toggleHeadingCollapse: () => ({ commands, editor }) => {
        const node = editor.state.selection.$from.parent
        return node.type.name === 'heading' && commands.updateAttributes('heading', { collapsed: !node.attrs.collapsed })
      }
    }
  },
  addProseMirrorPlugins() {
    return [new Plugin({
      key: new PluginKey('docflowHeadingCollapse'),
      props: {
        decorations(state) {
          const decorations = []
          let collapsedLevel = null
          state.doc.forEach((node, offset) => {
            if (node.type.name === 'heading') {
              if (collapsedLevel !== null && Number(node.attrs.level) <= collapsedLevel) collapsedLevel = null
              if (node.attrs.collapsed) collapsedLevel = Number(node.attrs.level)
              return
            }
            if (collapsedLevel !== null) decorations.push(Decoration.node(offset, offset + node.nodeSize, { class: 'heading-collapsed-content' }))
          })
          return DecorationSet.create(state.doc, decorations)
        }
      }
    })]
  }
})

export const TabIndent = Extension.create({
  name: 'tabIndent',
  priority: 110,
  addKeyboardShortcuts() {
    return {
      Tab: () => {
        if (this.editor.isActive('table')) return false
        const indentation = this.editor.isActive('codeBlock') ? '    ' : '\u3000\u3000\u3000\u3000'
        return this.editor.commands.insertContent(indentation)
      }
    }
  }
})

export const EnhancedBlockMath = BlockMath.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      mathml: { default: null, parseHTML: element => element.getAttribute('data-mathml'), renderHTML: attributes => attributes.mathml ? { 'data-mathml': attributes.mathml } : {} },
      equationLabel: { default: null, parseHTML: element => element.getAttribute('data-equation-label'), renderHTML: attributes => attributes.equationLabel ? { 'data-equation-label': attributes.equationLabel } : {} },
      equationNumber: { default: null, parseHTML: element => element.getAttribute('data-equation-number'), renderHTML: attributes => attributes.equationNumber ? { 'data-equation-number': attributes.equationNumber } : {} },
      formulaKind: { default: 'math', parseHTML: element => element.getAttribute('data-formula-kind') || 'math', renderHTML: attributes => ({ 'data-formula-kind': attributes.formulaKind || 'math' }) }
    }
  }
})

export const EnhancedInlineMath = InlineMath.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      mathml: { default: null, parseHTML: element => element.getAttribute('data-mathml'), renderHTML: attributes => attributes.mathml ? { 'data-mathml': attributes.mathml } : {} }
    }
  }
})

export const EquationReference = Node.create({
  name: 'equationReference', group: 'inline', inline: true, atom: true, selectable: true,
  addAttributes: () => ({
    label: { default: '', parseHTML: element => element.getAttribute('data-equation-label') || '', renderHTML: attributes => ({ 'data-equation-label': attributes.label }) },
    number: { default: '?', parseHTML: element => element.getAttribute('data-equation-number') || '?', renderHTML: attributes => ({ 'data-equation-number': attributes.number }) }
  }),
  parseHTML: () => [{ tag: 'span[data-type="equation-reference"]' }],
  renderHTML: ({ node, HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes, { 'data-type': 'equation-reference', class: 'equation-reference' }), `式（${node.attrs.number || '?'}）`]
})

export const Footnote = Node.create({
  name: 'footnote', group: 'inline', inline: true, atom: true, selectable: true,
  addAttributes: () => ({
    noteId: { default: '', parseHTML: element => element.getAttribute('data-footnote-id') || '', renderHTML: attributes => ({ 'data-footnote-id': attributes.noteId }) },
    noteText: { default: '', parseHTML: element => element.getAttribute('data-footnote-text') || '', renderHTML: attributes => ({ 'data-footnote-text': attributes.noteText }) }
  }),
  parseHTML: () => [{ tag: 'sup[data-type="footnote"]' }],
  renderHTML: ({ node, HTMLAttributes }) => ['sup', mergeAttributes(HTMLAttributes, { 'data-type': 'footnote', class: 'docflow-footnote', title: `脚注 ${node.attrs.noteId}：${node.attrs.noteText}` }), `${node.attrs.noteId}`]
})

export const ResizableTableRow = TableRow.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      rowHeight: {
        default: 0,
        parseHTML: element => Number.parseInt(element.getAttribute('data-row-height') || '0', 10) || 0,
        renderHTML: attributes => {
          const height = Math.min(240, Math.max(0, Number(attributes.rowHeight || 0)))
          return height ? { 'data-row-height': height, style: `height:${height}px` } : {}
        }
      }
    }
  }
})

export const Citation = Node.create({
  name: 'citation', group: 'inline', inline: true, atom: true, selectable: true,
  addAttributes: () => ({
    key: { default: '', parseHTML: element => element.getAttribute('data-citation-key') || '', renderHTML: attributes => ({ 'data-citation-key': attributes.key }) },
    title: { default: '', parseHTML: element => element.getAttribute('data-citation-title') || '', renderHTML: attributes => ({ 'data-citation-title': attributes.title }) },
    url: { default: '', parseHTML: element => element.getAttribute('data-citation-url') || '', renderHTML: attributes => attributes.url ? { 'data-citation-url': attributes.url } : {} }
  }),
  parseHTML: () => [{ tag: 'span[data-type="citation"]' }],
  renderHTML: ({ node, HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes, { 'data-type': 'citation', class: 'docflow-citation', title: node.attrs.title }), `[${node.attrs.key}]`]
})

export const LazyImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      loading: { default: 'lazy', parseHTML: element => element.getAttribute('loading') || 'lazy', renderHTML: () => ({ loading: 'lazy' }) },
      decoding: { default: 'async', parseHTML: element => element.getAttribute('decoding') || 'async', renderHTML: () => ({ decoding: 'async' }) }
    }
  }
})
