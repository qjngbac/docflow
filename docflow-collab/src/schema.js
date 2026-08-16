import StarterKit from '@tiptap/starter-kit'
import { TableKit } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { CommentMark } from './comment-mark.js'
import { PageBreak } from './page-break.js'
import { ParagraphAttribution, ReviewChange } from './review-schema.js'
import { BlockFormatting, Citation, DocflowTextStyle, EnhancedBlockMath, EnhancedInlineMath, EquationReference, Footnote, LazyImage } from './advanced-schema.js'

export const editorExtensions = [
  StarterKit.configure({ link: { openOnClick: false }, underline: {} }),
  LazyImage.configure({ inline: false, allowBase64: false }),
  TableKit,
  TaskList,
  TaskItem.configure({ nested: true }),
  EnhancedInlineMath.configure({ katexOptions: { throwOnError: false, strict: false } }),
  EnhancedBlockMath.configure({ katexOptions: { throwOnError: false, strict: false } }),
  DocflowTextStyle,
  BlockFormatting,
  EquationReference,
  Footnote,
  Citation,
  CommentMark
  ,PageBreak
  ,ReviewChange
  ,ParagraphAttribution
]
