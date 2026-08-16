import { Extension, Mark, mergeAttributes } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'

export const ReviewChange = Mark.create({
  name: 'reviewChange',
  inclusive: false,
  addAttributes() {
    return {
      changeId: { default: null, parseHTML: el => el.dataset.changeId, renderHTML: a => ({ 'data-change-id': a.changeId }) },
      changeType: { default: 'insert', parseHTML: el => el.dataset.changeType, renderHTML: a => ({ 'data-change-type': a.changeType }) },
      authorId: { default: null, parseHTML: el => el.dataset.authorId, renderHTML: a => ({ 'data-author-id': a.authorId }) },
      authorName: { default: null, parseHTML: el => el.dataset.authorName, renderHTML: a => ({ 'data-author-name': a.authorName, title: `${a.authorName || '协作者'}的修改` }) }
    }
  },
  parseHTML: () => [{ tag: 'span[data-change-id]' }],
  renderHTML: ({ HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes), 0]
})

export const ParagraphAttribution = Extension.create({
  name: 'paragraphAttribution',
  addOptions: () => ({ user: () => null }),
  addGlobalAttributes() {
    return [{
      types: ['paragraph', 'heading'],
      attributes: {
        lastEditBy: { default: null, parseHTML: el => el.dataset.lastEditBy, renderHTML: a => ({ 'data-last-edit-by': a.lastEditBy }) },
        lastEditName: { default: null, parseHTML: el => el.dataset.lastEditName, renderHTML: a => ({ 'data-last-edit-name': a.lastEditName, title: a.lastEditName ? `最后修改：${a.lastEditName}` : null }) }
      }
    }]
  },
  addProseMirrorPlugins() {
    const typeNames = new Set(['paragraph', 'heading'])
    return [new Plugin({
      key: new PluginKey('paragraph-attribution'),
      appendTransaction: (transactions, oldState, newState) => {
        if (!transactions.some(t => t.docChanged) || transactions.some(t => t.getMeta('attribution') || t.getMeta('y-sync$'))) return null
        const user = this.options.user?.()
        if (!user?.id) return null
        const start = oldState.doc.content.findDiffStart(newState.doc.content)
        if (start == null) return null
        const end = oldState.doc.content.findDiffEnd(newState.doc.content)
        const to = Math.max(start, end?.b ?? start)
        const tr = newState.tr.setMeta('attribution', true)
        newState.doc.nodesBetween(Math.max(0, start - 1), Math.min(newState.doc.content.size, to + 1), (node, pos) => {
          if (!typeNames.has(node.type.name)) return
          tr.setNodeMarkup(pos, undefined, { ...node.attrs, lastEditBy: String(user.id), lastEditName: user.name || '协作者' })
        })
        return tr.docChanged ? tr : null
      }
    })]
  }
})

export const ChangeTracking = Extension.create({
  name: 'changeTracking',
  addOptions: () => ({ enabled: () => false, user: () => null }),
  addProseMirrorPlugins() {
    const changeType = this.editor.schema.marks.reviewChange
    const attrs = type => {
      const user = this.options.user?.() || {}
      return { changeId: crypto.randomUUID(), changeType: type, authorId: String(user.id || ''), authorName: user.name || '协作者' }
    }
    return [new Plugin({
      key: new PluginKey('change-tracking'),
      appendTransaction: (transactions, oldState, newState) => {
        if (!this.options.enabled?.() || !transactions.some(t => t.docChanged)
          || transactions.some(t => t.getMeta('review-change') || t.getMeta('y-sync$'))) return null
        const start = oldState.doc.content.findDiffStart(newState.doc.content)
        const end = oldState.doc.content.findDiffEnd(newState.doc.content)
        if (start == null || !end || end.b <= start) return null
        return newState.tr.addMark(start, end.b, changeType.create(attrs('insert'))).setMeta('review-change', true)
      },
      props: {
        handleKeyDown: (view, event) => {
          if (!this.options.enabled?.() || !['Backspace', 'Delete'].includes(event.key)) return false
          const { from, to, empty } = view.state.selection
          let start = from; let end = to
          if (empty && event.key === 'Backspace' && from > 1) start = from - 1
          if (empty && event.key === 'Delete' && to < view.state.doc.content.size) end = to + 1
          if (start >= end) return false
          view.dispatch(view.state.tr.addMark(start, end, changeType.create(attrs('delete'))).setMeta('review-change', true))
          return true
        }
      }
    })]
  }
})
