<template>
  <div class="static-paged-document">
    <div class="spd-scroll">
      <!-- 必须保留 document-paper 这个类名：分页扩展用 view.dom.closest('.document-paper')
           定位纸张来测量页高，找不到就直接返回"不分页"。编辑器里的 .document-paper 样式是 scoped 的
           （带 [data-v-*] 属性），所以这里只借用类名、不会继承到编辑器样式。 -->
      <div class="spd-paper document-paper" :style="paperVariables">
        <div v-if="paginated" class="spd-furniture" aria-hidden="true">
          <div v-for="page in pageCount" :key="page" class="spd-furniture-item" :style="furnitureStyle(page)">
            <div class="spd-mask spd-mask-top"></div>
            <div class="spd-mask spd-mask-bottom"></div>
            <div class="spd-header-text">{{ pageSettings?.pageHeader || '' }}</div>
            <div class="spd-footer-text"><span>{{ pageSettings?.pageFooter || '' }}</span><span>{{ page }} / {{ pageCount }}</span></div>
          </div>
        </div>
        <EditorContent :editor="editor" class="spd-host" />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 分享页专用的只读渲染器：复用编辑器的分页算法，但不建立任何协作连接。
 *
 * 为什么要单独一份（而不是给 CrdtRichTextEditor 加只读模式）：编辑器本体依赖 Hocuspocus
 * provider 与 IndexedDB，做成可选会在这两个核心对象上引入多处空值分支。这里刻意把扩展列表
 * 与页面样式复制一份，让编辑器保持零改动；代价是两边需要同步维护。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { TableKit } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { CommentMark } from '../editor/commentMark'
import { PageBreak } from '../editor/pageBreak'
import { PaginationLayout } from '../editor/paginationExtension'
import { ReviewChange } from '../editor/reviewExtensions'
import { BlockFormatting, Citation, DocflowTextStyle, EnhancedBlockMath, EnhancedInlineMath, EquationReference, Footnote, LazyImage, ResizableTableRow, TabIndent } from '../editor/advancedExtensions'
import katex from 'katex'
import 'katex/contrib/mhchem'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' },
  pageSettings: { type: Object, default: () => ({}) }
})

const pageCount = ref(1)
// 与编辑器保持一致：窄屏不启用分页（编辑器的分页扩展在 <=700px 时也关闭）。
const paginated = ref(typeof window === 'undefined' ? false : !window.matchMedia('(max-width: 700px)').matches)
const narrowQuery = typeof window === 'undefined' ? null : window.matchMedia('(max-width: 700px)')
if (narrowQuery) {
  const syncNarrow = () => { paginated.value = !narrowQuery.matches }
  narrowQuery.addEventListener?.('change', syncNarrow)
  onBeforeUnmount(() => narrowQuery.removeEventListener?.('change', syncNarrow))
}

const paperVariables = computed(() => {
  const letter = props.pageSettings?.pageFormat === 'LETTER'
  return {
    '--paper-width': `${letter ? 216 : 210}mm`,
    '--paper-height': `${letter ? 279 : 297}mm`,
    '--margin-top': `${Number(props.pageSettings?.marginTop ?? 20)}mm`,
    '--margin-right': `${Number(props.pageSettings?.marginRight ?? 20)}mm`,
    '--margin-bottom': `${Number(props.pageSettings?.marginBottom ?? 20)}mm`,
    '--margin-left': `${Number(props.pageSettings?.marginLeft ?? 20)}mm`,
    '--page-gap': '18px',
    '--paged-min-height': `calc(${(letter ? 279 : 297) * pageCount.value}mm + ${(pageCount.value - 1) * 18}px)`
  }
})

function furnitureStyle(page) {
  const paperHeight = props.pageSettings?.pageFormat === 'LETTER' ? 279 : 297
  return { top: `calc(${(page - 1) * paperHeight}mm + ${(page - 1) * 18}px)`, height: `${paperHeight}mm` }
}

const editor = useEditor({
  editable: false,
  content: props.content || '<p></p>',
  extensions: [
    StarterKit.configure({ undoRedo: false, link: { openOnClick: true }, underline: {} }),
    LazyImage.configure({ inline: false, allowBase64: false }),
    TableKit.configure({ table: { resizable: false }, tableRow: false }),
    ResizableTableRow,
    TaskList,
    TaskItem.configure({ nested: true }),
    EnhancedInlineMath.configure({ katexOptions: { throwOnError: false, strict: false } }),
    EnhancedBlockMath.configure({ katexOptions: { throwOnError: false, strict: false } }),
    DocflowTextStyle,
    BlockFormatting,
    EquationReference,
    Footnote,
    Citation,
    // 只读视图不响应点击，但保留批注标记的解析，让批注范围能显示出来。
    CommentMark.configure({ onClick: () => false }),
    TabIndent,
    PageBreak,
    PaginationLayout.configure({
      isEnabled: () => paginated.value,
      getPageSettings: () => props.pageSettings,
      onPageCount: count => { pageCount.value = count }
    }),
    ReviewChange
  ]
})

watch(() => props.content, html => {
  if (!editor.value) return
  if ((html || '') !== editor.value.getHTML()) editor.value.commands.setContent(html || '<p></p>', false)
})

onBeforeUnmount(() => editor.value?.destroy())
</script>

<!-- 刻意不加 scoped：内容由 Tiptap 动态渲染，scoped 选择器进不去。
     所有规则都挂在 .static-paged-document 前缀下，避免影响其它页面。 -->
<style>
.static-paged-document { height: 100%; }
.spd-scroll { box-sizing: border-box; height: 100%; overflow: auto; padding: 20px; background: #eef2f6; }
.spd-paper { box-sizing: border-box; position: relative; width: var(--paper-width); min-width: var(--paper-width); min-height: var(--paged-min-height); margin: 0 auto; padding: var(--margin-top) var(--margin-right) var(--margin-bottom) var(--margin-left); background-color: #fff; background-image: repeating-linear-gradient(to bottom, #fff 0, #fff calc(var(--paper-height) - 1px), #cbd5e1 calc(var(--paper-height) - 1px), #cbd5e1 var(--paper-height), #dfe7f0 var(--paper-height), #dfe7f0 calc(var(--paper-height) + var(--page-gap) - 1px), #cbd5e1 calc(var(--paper-height) + var(--page-gap) - 1px), #cbd5e1 calc(var(--paper-height) + var(--page-gap))); box-shadow: 0 2px 10px rgba(15,23,42,.10); }
.spd-host { position: relative; z-index: 2; width: 100%; height: auto; min-height: calc(var(--paper-height) - var(--margin-top) - var(--margin-bottom)); }
.spd-furniture { position: absolute; inset: 0; z-index: 3; pointer-events: none; color: #64748b; font-size: 10px; }
.spd-furniture-item { position: absolute; left: 0; width: 100%; }
.spd-mask { position: absolute; z-index: 1; left: 0; width: 100%; background: #fff; }
.spd-mask-top { top: 0; height: var(--margin-top); }
.spd-mask-bottom { bottom: calc(var(--page-gap) * -1); height: calc(var(--margin-bottom) + var(--page-gap)); background: linear-gradient(to bottom, #fff 0, #fff calc(var(--margin-bottom) - 1px), #cbd5e1 calc(var(--margin-bottom) - 1px), #cbd5e1 var(--margin-bottom), #dfe7f0 var(--margin-bottom), #dfe7f0 calc(100% - 1px), #cbd5e1 calc(100% - 1px), #cbd5e1 100%); }
.spd-header-text { position: absolute; top: 7mm; left: var(--margin-left); right: var(--margin-right); z-index: 2; overflow: hidden; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.spd-footer-text { position: absolute; left: var(--margin-left); right: var(--margin-right); bottom: 6mm; z-index: 2; display: flex; justify-content: center; gap: 10px; }
.spd-footer-text span:first-child:not(:empty)::after { content: ' ·'; }

/* 分页占位块：只占布局高度，不参与交互 */
.spd-host .pagination-page-spacer { display: block; width: 100%; margin: 0; padding: 0; pointer-events: none; user-select: none; overflow-anchor: none; }
.spd-host .pagination-inline-spacer { display: block; max-width: 100%; line-height: 0; overflow-anchor: none; }
.spd-host .pagination-table-cell-spacer { min-width: 0; border: 0; background: transparent; overflow-anchor: none; }

/* 正文排版：与编辑器里的 .tiptap 规则保持一致 */
.spd-host .tiptap { box-sizing: border-box; min-height: calc(var(--paper-height) - var(--margin-top) - var(--margin-bottom) - 1px); outline: none; color: #1f2937; line-height: 1.75; overflow-wrap: anywhere; }
.spd-host .tiptap > * { content-visibility: visible; contain: none; }
.spd-host .tiptap h1 { font-size: 2em; margin: 1em 0 .5em; }
.spd-host .tiptap h2 { font-size: 1.5em; margin: 1em 0 .5em; }
.spd-host .tiptap h3 { font-size: 1.25em; margin: 1em 0 .5em; }
.spd-host .tiptap p { margin: .55em 0; }
.spd-host .tiptap blockquote { margin: 1em 0; padding-left: 14px; border-left: 3px solid #93b4e8; color: #64748b; }
.spd-host .tiptap img { max-width: 100%; height: auto; }
.spd-host .tiptap ul, .spd-host .tiptap ol { padding-left: 24px; }
.spd-host .tiptap .tableWrapper { max-width: 100%; overflow: hidden; }
.spd-host .tiptap table { width: 100%; margin: 1em 0; border-collapse: collapse; table-layout: fixed; }
.spd-host .tiptap th, .spd-host .tiptap td { position: relative; min-width: 0; padding: 7px 9px; border: 1px solid #e2e8f0; vertical-align: top; }
.spd-host .tiptap th { background: #f1f5f9; }
.spd-host .tiptap pre { overflow: auto; padding: 12px; border-radius: 6px; background: #111827; color: #f8fafc; }
.spd-host .tiptap code { padding: 1px 4px; border-radius: 3px; background: #eef2f7; }
.spd-host .tiptap pre code { padding: 0; background: transparent; }
.spd-host .tiptap hr { margin: 18px 0; border: 0; border-top: 1px solid #e2e8f0; }
.spd-host .tiptap ul[data-type='taskList'] { padding-left: 0; list-style: none; }
.spd-host .tiptap ul[data-type='taskList'] li { display: flex; gap: 8px; }
.spd-host .tiptap ul[data-type='taskList'] li > label { flex: 0 0 auto; }

/* 批注范围、修订痕迹、公式、引用与脚注 */
.spd-host .comment-highlight { background: #fef3c7; border-bottom: 2px solid #f59e0b; }
.spd-host [data-change-type='insert'] { border-bottom: 2px solid #22c55e; background: #dcfce7; }
.spd-host [data-change-type='delete'] { color: #b91c1c; background: #fee2e2; text-decoration: line-through; }
.spd-host [data-type='block-math'] { position: relative; margin: 18px 0; padding: 4px 54px; text-align: center; }
.spd-host [data-type='block-math'][data-equation-number]::after { content: '(' attr(data-equation-number) ')'; position: absolute; right: 10px; top: 50%; transform: translateY(-50%); color: #64748b; font-size: 12px; }
.spd-host [data-type='inline-math'] { display: inline-block; padding: 0 2px; }
.spd-host .equation-reference, .spd-host .docflow-citation { color: #2563eb; }
.spd-host .docflow-footnote { position: relative; top: -.35em; margin: 0 1px; color: #2563eb; font-size: .72em; font-weight: 700; line-height: 0; }
.spd-host .page-break { height: 20px; margin: 24px calc(var(--margin-right) * -1) 24px calc(var(--margin-left) * -1); border-top: 2px dashed #94a3b8; border-bottom: 2px dashed #94a3b8; background: #eef2f6; }
.spd-host .katex-html { display: none; }
.spd-host .katex-mathml { position: static !important; width: auto !important; height: auto !important; overflow: visible !important; clip: auto !important; white-space: normal !important; }

/* 行内样式属性（字体、字号、颜色、对齐、行高、缩进） */
.spd-host [data-font-family='sans'] { font-family: "Microsoft YaHei", Arial, sans-serif; }
.spd-host [data-font-family='serif'] { font-family: SimSun, "Songti SC", serif; }
.spd-host [data-font-family='kai'] { font-family: KaiTi, "Kaiti SC", serif; }
.spd-host [data-font-family='mono'] { font-family: Consolas, monospace; }
.spd-host [data-font-size='12'] { font-size: 12px; }
.spd-host [data-font-size='14'] { font-size: 14px; }
.spd-host [data-font-size='16'] { font-size: 16px; }
.spd-host [data-font-size='18'] { font-size: 18px; }
.spd-host [data-font-size='20'] { font-size: 20px; }
.spd-host [data-font-size='24'] { font-size: 24px; }
.spd-host [data-font-size='28'] { font-size: 28px; }
.spd-host [data-font-size='32'] { font-size: 32px; }
.spd-host [data-text-color='red'] { color: #dc2626; }
.spd-host [data-text-color='blue'] { color: #2563eb; }
.spd-host [data-text-color='green'] { color: #15803d; }
.spd-host [data-text-color='gray'] { color: #64748b; }
.spd-host [data-text-color='purple'] { color: #7e22ce; }
.spd-host [data-text-align='left'] { text-align: left; }
.spd-host [data-text-align='center'] { text-align: center; }
.spd-host [data-text-align='right'] { text-align: right; }
.spd-host [data-text-align='justify'] { text-align: justify; }
.spd-host [data-line-height='1.25'] { line-height: 1.25; }
.spd-host [data-line-height='1.5'] { line-height: 1.5; }
.spd-host [data-line-height='1.75'] { line-height: 1.75; }
.spd-host [data-line-height='2'] { line-height: 2; }
.spd-host [data-indent='1'] { margin-left: 2em; }
.spd-host [data-indent='2'] { margin-left: 4em; }
.spd-host [data-indent='3'] { margin-left: 6em; }
.spd-host [data-indent='4'] { margin-left: 8em; }
.spd-host [data-indent='5'] { margin-left: 10em; }
.spd-host [data-indent='6'] { margin-left: 12em; }
.spd-host [data-indent='7'] { margin-left: 14em; }
.spd-host [data-indent='8'] { margin-left: 16em; }
.spd-host h1[data-collapsed='true']::after, .spd-host h2[data-collapsed='true']::after, .spd-host h3[data-collapsed='true']::after { content: '  …'; color: #94a3b8; font-size: .7em; }
.spd-host .heading-collapsed-content { display: none; }

@media (max-width: 700px) {
  .spd-scroll { padding: 10px 8px 30px; }
  .spd-paper { width: 100%; min-width: 0; min-height: auto; padding: 20px 18px; background-image: none; }
  .spd-host, .spd-host .tiptap { min-height: auto; }
  .spd-furniture { display: none; }
}
</style>
