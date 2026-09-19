import StarterKit from '@tiptap/starter-kit'
import { TableKit } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { CommentMark } from './comment-mark.js'
import { PageBreak } from './page-break.js'
import { ParagraphAttribution, ReviewChange } from './review-schema.js'
import { BlockFormatting, Citation, DocflowTextStyle, EnhancedBlockMath, EnhancedInlineMath, EquationReference, Footnote, LazyImage } from './advanced-schema.js'

// 服务端 schema 必须与前端 CRDT 编辑器一致，否则未知节点会在转换或 checkpoint 时丢失。
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
