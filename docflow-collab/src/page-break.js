import { Node, mergeAttributes } from '@tiptap/core'

// 手动分页符作为原子节点参与 CRDT 同步，视觉高度由前端分页器决定。
export const PageBreak = Node.create({
  name: 'pageBreak', group: 'block', atom: true, selectable: true,
  parseHTML: () => [{ tag: 'div[data-type="page-break"]' }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes, {
    'data-type': 'page-break', class: 'page-break', contenteditable: 'false'
  })]
})
