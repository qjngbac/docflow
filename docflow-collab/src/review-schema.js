import { Extension, Mark, mergeAttributes } from '@tiptap/core'

export const ReviewChange = Mark.create({
  name: 'reviewChange', inclusive: false,
  addAttributes: () => ({
    changeId: { default: null, parseHTML: el => el.dataset.changeId, renderHTML: a => ({ 'data-change-id': a.changeId }) },
    changeType: { default: 'insert', parseHTML: el => el.dataset.changeType, renderHTML: a => ({ 'data-change-type': a.changeType }) },
    authorId: { default: null, parseHTML: el => el.dataset.authorId, renderHTML: a => ({ 'data-author-id': a.authorId }) },
    authorName: { default: null, parseHTML: el => el.dataset.authorName, renderHTML: a => ({ 'data-author-name': a.authorName }) }
  }),
  parseHTML: () => [{ tag: 'span[data-change-id]' }],
  renderHTML: ({ HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes), 0]
})

export const ParagraphAttribution = Extension.create({
  name: 'paragraphAttribution',
  addGlobalAttributes: () => [{
    types: ['paragraph', 'heading'],
    attributes: {
      lastEditBy: { default: null, parseHTML: el => el.dataset.lastEditBy, renderHTML: a => ({ 'data-last-edit-by': a.lastEditBy }) },
      lastEditName: { default: null, parseHTML: el => el.dataset.lastEditName, renderHTML: a => ({ 'data-last-edit-name': a.lastEditName }) }
    }
  }]
})
