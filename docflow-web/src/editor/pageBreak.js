import { Node, mergeAttributes } from '@tiptap/core'

// 手动分页符是正文中的原子节点，具体空白高度由分页扩展根据当前纸张设置计算。
export const PageBreak = Node.create({
  name: 'pageBreak',
  group: 'block',
  atom: true,
  selectable: true,
  parseHTML: () => [{ tag: 'div[data-type="page-break"]' }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes, {
    'data-type': 'page-break', class: 'page-break', contenteditable: 'false'
  })],
  addCommands() {
    return { insertPageBreak: () => ({ commands }) => commands.insertContent({ type: this.name }) }
  }
})
