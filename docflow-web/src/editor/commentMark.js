import { Mark, mergeAttributes } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'

export const CommentMark = Mark.create({
  name: 'comment',
  inclusive: false,

  addOptions() {
    return { onClick: () => false }
  },

  addAttributes() {
    return {
      commentId: {
        default: null,
        parseHTML: element => element.getAttribute('data-comment-id'),
        renderHTML: attributes => attributes.commentId
          ? { 'data-comment-id': String(attributes.commentId) }
          : {}
      }
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-comment-id]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { class: 'comment-highlight' }), 0]
  },

  addProseMirrorPlugins() {
    const options = this.options
    return [new Plugin({
      key: new PluginKey('docflowCommentClick'),
      props: {
        handleClick(_view, _position, event) {
          const target = event.target instanceof Element ? event.target.closest('[data-comment-id]') : null
          if (!target) return false
          return Boolean(options.onClick?.(target.getAttribute('data-comment-id')))
        }
      }
    })]
  }
})
