import { Mark, mergeAttributes } from '@tiptap/core'

// 批注内容保存在 Java 服务，Yjs 正文只保存批注 ID 对应的文字范围。
export const CommentMark = Mark.create({
  name: 'comment',
  inclusive: false,
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
  }
})
