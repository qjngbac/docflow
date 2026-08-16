<template>
  <div class="rich-editor" :style="paperStyle">
    <div v-if="editor" class="rich-toolbar">
      <button type="button" :class="{active:editor.isActive('bold')}" title="加粗" :disabled="!editable" @click="editor.chain().focus().toggleBold().run()"><Bold :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('italic')}" title="斜体" :disabled="!editable" @click="editor.chain().focus().toggleItalic().run()"><Italic :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('underline')}" title="下划线" :disabled="!editable" @click="editor.chain().focus().toggleUnderline().run()"><UnderlineIcon :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('strike')}" title="删除线" :disabled="!editable" @click="editor.chain().focus().toggleStrike().run()"><Strikethrough :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('code')}" title="行内代码" :disabled="!editable" @click="editor.chain().focus().toggleCode().run()"><Code2 :size="17"/></button>
      <span/>
      <button type="button" :class="{active:editor.isActive('heading',{level:1})}" title="一级标题" :disabled="!editable" @click="editor.chain().focus().toggleHeading({level:1}).run()">H1</button>
      <button type="button" :class="{active:editor.isActive('heading',{level:2})}" title="二级标题" :disabled="!editable" @click="editor.chain().focus().toggleHeading({level:2}).run()">H2</button>
      <button type="button" :class="{active:editor.isActive('bulletList')}" title="无序列表" :disabled="!editable" @click="editor.chain().focus().toggleBulletList().run()"><List :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('orderedList')}" title="有序列表" :disabled="!editable" @click="editor.chain().focus().toggleOrderedList().run()"><ListOrdered :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('taskList')}" title="任务列表" :disabled="!editable" @click="editor.chain().focus().toggleTaskList().run()"><ListChecks :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('blockquote')}" title="引用" :disabled="!editable" @click="editor.chain().focus().toggleBlockquote().run()"><Quote :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('codeBlock')}" title="代码块" :disabled="!editable" @click="editor.chain().focus().toggleCodeBlock().run()"><SquareCode :size="17"/></button>
      <button type="button" title="插入链接" :disabled="!editable" @click="setLink"><LinkIcon :size="17"/></button>
      <button type="button" title="插入表格" :disabled="!editable" @click="editor.chain().focus().insertTable({rows:3,cols:3,withHeaderRow:true}).run()"><Table2 :size="17"/></button>
      <button type="button" title="插入图片" :disabled="!editable" @click="fileInput?.click()"><ImagePlus :size="17"/></button>
      <button type="button" title="公式与化学式" :disabled="!editable" @click="openFormulaPanel()"><Sigma :size="17"/></button>
      <button type="button" title="插入分页符" :disabled="!editable" @click="editor.chain().focus().insertPageBreak().run()"><BetweenVerticalStart :size="17"/></button>
      <button type="button" :class="{active:pagedEditing}" :title="pagedEditing?'关闭分页编辑':'开启分页编辑'" @click="togglePagedEditing"><PanelsTopLeft :size="17"/></button>
      <button type="button" :class="{active:spreadEditing}" title="双页并排显示" @click="toggleSpreadEditing"><BookOpen :size="17"/></button>
      <button type="button" :class="{active:trackChanges}" title="修订模式" :disabled="!editable" @click="trackChanges=!trackChanges"><FileDiff :size="17"/></button>
      <button type="button" title="接受全部修订" :disabled="!editable" @click="applyAllChanges('accept')"><CheckCheck :size="17"/></button>
      <button type="button" title="拒绝全部修订" :disabled="!editable" @click="applyAllChanges('reject')"><Undo2 :size="17"/></button>
      <button type="button" title="给选中文字添加批注" :disabled="!commentable||selectionEmpty" @click="requestComment"><MessageSquarePlus :size="17"/></button>
      <input ref="fileInput" class="hidden-input" type="file" accept="image/*" multiple @change="chooseImages"/>
    </div>
    <div v-if="editor" class="advanced-toolbar">
      <select class="page-size-select" title="页面大小" :value="pageSettings?.pageFormat||'A4'" :disabled="!editable" @change="changePageFormat($event.target.value)"><option value="A4">A4 纸张</option><option value="LETTER">Letter 纸张</option></select><small v-if="pagedEditing" class="page-count">{{spreadEditing?spreadPageCount:paginationPageCount}} 页</small><button type="button" title="页面、页边距、页眉和页脚" :disabled="!editable" @click="openPageSettings"><Settings2 :size="16"/></button><button class="zoom-indicator" type="button" title="Ctrl + 鼠标滚轮缩放；点击恢复适应宽度" @click="resetPaperZoom">{{Math.round(paperZoom*100)}}%</button>
      <select title="字体" :disabled="!editable" @change="setTextStyle('fontFamily',$event.target.value)"><option value="">默认字体</option><option value="sans">黑体</option><option value="serif">宋体</option><option value="kai">楷体</option><option value="mono">等宽</option></select>
      <select title="字号" :disabled="!editable" @change="setTextStyle('fontSize',$event.target.value)"><option value="">默认字号</option><option v-for="size in [12,14,16,18,20,24,28,32]" :key="size" :value="String(size)">{{size}} px</option></select>
      <select title="文字颜色" :disabled="!editable" @change="setTextStyle('textColor',$event.target.value)"><option value="">默认颜色</option><option value="red">红色</option><option value="blue">蓝色</option><option value="green">绿色</option><option value="gray">灰色</option><option value="purple">紫色</option></select>
      <button type="button" title="左对齐" :disabled="!editable" @click="setBlock('textAlign','left')"><AlignLeft :size="16"/></button><button type="button" title="居中" :disabled="!editable" @click="setBlock('textAlign','center')"><AlignCenter :size="16"/></button><button type="button" title="右对齐" :disabled="!editable" @click="setBlock('textAlign','right')"><AlignRight :size="16"/></button><button type="button" title="两端对齐" :disabled="!editable" @click="setBlock('textAlign','justify')"><AlignJustify :size="16"/></button>
      <select title="行距" :disabled="!editable" @change="setBlock('lineHeight',$event.target.value)"><option value="">默认行距</option><option value="1.25">1.25</option><option value="1.5">1.5</option><option value="1.75">1.75</option><option value="2">2.0</option></select>
      <button type="button" :class="{active:formatBrush}" title="格式刷：先复制当前格式，再选中文字应用" :disabled="!editable" @click="toggleFormatBrush"><Paintbrush :size="16"/></button>
      <select title="特殊字符" :disabled="!editable" @change="insertSpecialCharacter($event)"><option value="">特殊字符</option><option v-for="char in specialCharacters" :key="char" :value="char">{{char}}</option></select>
      <button type="button" title="脚注" :disabled="!editable" @click="insertFootnote"><Superscript :size="16"/></button><button type="button" title="文献引用" :disabled="!editable" @click="insertCitation()"><BookOpenText :size="16"/></button><button type="button" title="公式交叉引用" :disabled="!editable" @click="insertEquationReference"><Sigma :size="15"/>#</button>
      <button type="button" title="折叠或展开当前标题" :disabled="!editable||!editor.isActive('heading')" @click="editor.chain().focus().toggleHeadingCollapse().run()"><FoldVertical :size="16"/></button>
      <template v-if="editor.isActive('table')"><span/><button type="button" title="合并所选单元格" :disabled="!editable" @click="editor.chain().focus().mergeCells().run()"><Combine :size="16"/></button><button type="button" title="拆分单元格" :disabled="!editable" @click="editor.chain().focus().splitCell().run()"><Split :size="16"/></button><button type="button" title="在上方插入行" :disabled="!editable" @click="editor.chain().focus().addRowBefore().run()"><BetweenHorizontalStart :size="16"/></button><button type="button" title="在下方插入行" :disabled="!editable" @click="editor.chain().focus().addRowAfter().run()"><BetweenHorizontalEnd :size="16"/></button><button type="button" title="删除当前行" :disabled="!editable" @click="editor.chain().focus().deleteRow().run()"><Rows3 :size="16" class="delete-tool"/></button><button type="button" title="增大当前行高度" :disabled="!editable" @click="changeRowHeight(12)"><ChevronsDown :size="16"/></button><button type="button" title="减小当前行高度" :disabled="!editable" @click="changeRowHeight(-12)"><ChevronsUp :size="16"/></button><button type="button" title="在左侧插入列" :disabled="!editable" @click="editor.chain().focus().addColumnBefore().run()"><BetweenVerticalStart :size="16"/></button><button type="button" title="在右侧插入列" :disabled="!editable" @click="editor.chain().focus().addColumnAfter().run()"><BetweenVerticalEnd :size="16"/></button><button type="button" title="删除当前列" :disabled="!editable" @click="editor.chain().focus().deleteColumn().run()"><Columns3 :size="16" class="delete-tool"/></button><button type="button" title="删除整个表格" :disabled="!editable" @click="editor.chain().focus().deleteTable().run()"><Trash2 :size="16"/></button><button type="button" title="按当前列升序" :disabled="!editable" @click="sortCurrentTable(1)"><ArrowDownAZ :size="16"/></button><button type="button" title="按当前列降序" :disabled="!editable" @click="sortCurrentTable(-1)"><ArrowUpAZ :size="16"/></button></template>
    </div>
    <div class="rich-content" @wheel="handlePaperWheel">
      <div v-show="!spreadEditing" class="document-paper" :class="{ 'paged-editing': pagedEditing }" :style="{zoom:paperZoom}">
        <div v-if="pagedEditing" class="page-furniture" aria-hidden="true"><div v-for="page in paginationPageCount" :key="page" class="page-furniture-item" :style="pageFurnitureStyle(page)"><div class="page-margin-mask page-margin-mask-top"></div><div class="page-margin-mask page-margin-mask-bottom"></div><div class="page-header-text">{{pageSettings?.pageHeader||''}}</div><div class="page-footer-text"><span>{{pageSettings?.pageFooter||''}}</span><span>{{page}} / {{paginationPageCount}}</span></div></div></div>
        <EditorContent :editor="editor" class="editor-host"/>
      </div>
      <div v-if="spreadEditing" class="spread-grid" :style="{zoom:paperZoom}">
        <section v-for="page in spreadPageCount" :key="page" class="spread-page">
          <div class="spread-page-header">{{pageSettings?.pageHeader||''}}</div>
          <div class="spread-page-body"><div class="tiptap spread-page-clone" :style="spreadPageContentStyle(page)" v-html="spreadHtml"></div></div>
          <div class="spread-page-footer"><span>{{pageSettings?.pageFooter||''}}</span><span>{{page}} / {{spreadPageCount}}</span></div>
        </section>
      </div>
    </div>
    <div v-if="formulaPanel.open" class="formula-backdrop" @click.self="formulaPanel.open=false"><section class="formula-panel"><header><strong>{{formulaPanel.editPos==null?'插入公式':'编辑公式'}}</strong><button type="button" title="关闭" @click="formulaPanel.open=false"><X :size="17"/></button></header><div class="formula-tabs"><button v-for="tab in formulaTabs" :key="tab.key" type="button" :class="{active:formulaPanel.tab===tab.key}" @click="formulaPanel.tab=tab.key">{{tab.label}}</button></div><div class="symbol-grid"><button v-for="symbol in activeFormulaSymbols" :key="symbol.latex" type="button" :title="symbol.title" @click="appendFormulaSymbol(symbol.latex)" v-html="symbol.preview"></button></div><label>结构化公式</label><MathFieldEditor v-model="formulaPanel.latex" :mathml="formulaPanel.mathml" @update:mathml="formulaPanel.mathml=$event"/><div class="formula-preview" v-html="formulaPreview"></div><div class="formula-options"><label><input v-model="formulaPanel.block" type="checkbox"/>独立公式</label><label><input v-model="formulaPanel.numbered" type="checkbox"/>自动编号</label><input v-if="formulaPanel.numbered" v-model.trim="formulaPanel.label" maxlength="80" placeholder="引用标签，例如 eq-energy"/></div><label>MathML 源码<textarea v-model="formulaPanel.mathml" rows="3" placeholder="粘贴 <math>...</math> 后点击导入"/></label><p v-if="formulaPanel.error" class="dialog-error">{{formulaPanel.error}}</p><div class="formula-actions"><button type="button" @click="importMathMl">导入 MathML</button><button type="button" @click="exportMathMl">导出全部 MathML</button><button class="primary" type="button" :disabled="!formulaPanel.latex.trim()" @click="saveFormula">确定</button></div></section></div>
    <div v-if="referencePanel.open" class="formula-backdrop" @click.self="referencePanel.open=false"><section class="formula-panel reference-panel"><header><strong>文献引用</strong><button type="button" title="关闭" @click="referencePanel.open=false"><X :size="17"/></button></header><div class="reference-import"><input v-model.trim="referencePanel.doi" placeholder="输入 DOI，例如 10.xxxx/xxxx"/><button type="button" @click="importReferenceDoi">导入 DOI</button></div><div class="reference-import manual-reference"><input v-model.trim="referencePanel.citeKey" placeholder="引用键，例如 Zhang2026"/><input v-model.trim="referencePanel.title" placeholder="文献标题"/><input v-model.trim="referencePanel.url" placeholder="文献链接（可选）"/><button type="button" @click="createReference">加入文献库</button></div><p v-if="referencePanel.error" class="dialog-error">{{referencePanel.error}}</p><p v-if="referencePanel.loading" class="reference-note">正在加载文献库…</p><div class="reference-items"><article v-for="item in referencePanel.items" :key="item.id"><div><strong>{{item.citeKey}}</strong><span>{{referenceTitle(item)}}</span><small v-if="item.doi">doi: {{item.doi}}</small></div><div><button type="button" @click="insertCitation(item)">引用</button><button type="button" class="danger" @click="removeReference(item)"><Trash2 :size="14"/></button></div></article><p v-if="!referencePanel.loading&&!referencePanel.items.length" class="reference-note">暂无文献。可由 DOI 导入，或在上方完整填写后创建。</p></div><div class="footnote-manager"><h3>脚注</h3><p v-for="item in referencePanel.footnotes" :key="item.id"><strong>{{item.id}}</strong>{{item.text}}</p><p v-if="!referencePanel.footnotes.length" class="reference-note">当前文档没有脚注。</p></div></section></div>
    <div v-if="quickDialog.open" class="formula-backdrop" @click.self="closeQuickDialog"><form class="quick-dialog" @submit.prevent="submitQuickDialog"><header><strong>{{quickDialog.title}}</strong><button type="button" title="关闭" @click="closeQuickDialog"><X :size="17"/></button></header><p v-if="quickDialog.message">{{quickDialog.message}}</p><label v-if="quickDialog.mode==='footnote'">脚注正文<textarea v-model.trim="quickDialog.value" rows="4" autofocus placeholder="输入脚注内容"></textarea></label><label v-if="quickDialog.mode==='equation'&&quickDialog.options.length">选择编号公式<select v-model="quickDialog.value" autofocus><option v-for="item in quickDialog.options" :key="item.label" :value="item.label">式（{{item.equationNumber}}） · {{item.label}}</option></select></label><footer><button type="button" @click="closeQuickDialog">取消</button><button v-if="quickDialog.mode!=='message'" class="primary" type="submit" :disabled="!quickDialog.value">插入</button></footer></form></div>
    <div v-if="pageSettingsPanel.open" class="formula-backdrop" @click.self="pageSettingsPanel.open=false"><form class="page-settings-dialog" @submit.prevent="savePageSettings"><header><strong>页面设置</strong><button type="button" title="关闭" @click="pageSettingsPanel.open=false"><X :size="17"/></button></header><label>纸张<select v-model="pageSettingsPanel.pageFormat"><option value="A4">A4</option><option value="LETTER">Letter</option></select></label><div class="page-margin-grid"><label>上边距（mm）<input v-model.number="pageSettingsPanel.marginTop" type="number" min="5" max="60"/></label><label>右边距（mm）<input v-model.number="pageSettingsPanel.marginRight" type="number" min="5" max="60"/></label><label>下边距（mm）<input v-model.number="pageSettingsPanel.marginBottom" type="number" min="5" max="60"/></label><label>左边距（mm）<input v-model.number="pageSettingsPanel.marginLeft" type="number" min="5" max="60"/></label></div><label>页眉<input v-model.trim="pageSettingsPanel.pageHeader" maxlength="500" placeholder="可留空"/></label><label>页脚<input v-model.trim="pageSettingsPanel.pageFooter" maxlength="500" placeholder="页码始终显示"/></label><footer><button type="button" @click="pageSettingsPanel.open=false">取消</button><button class="primary" type="submit">保存并应用</button></footer></form></div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { TableKit } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCaret from '@tiptap/extension-collaboration-caret'
import { HocuspocusProvider } from '@hocuspocus/provider'
import { IndexeddbPersistence } from 'y-indexeddb'
import * as Y from 'yjs'
import { AlignCenter, AlignJustify, AlignLeft, AlignRight, ArrowDownAZ, ArrowUpAZ, BetweenHorizontalEnd, BetweenHorizontalStart, BetweenVerticalEnd, BetweenVerticalStart, Bold, BookOpen, BookOpenText, CheckCheck, ChevronsDown, ChevronsUp, Code2, Columns3, Combine, FileDiff, FoldVertical, ImagePlus, Italic, Link as LinkIcon, List, ListChecks, ListOrdered, MessageSquarePlus, Paintbrush, PanelsTopLeft, Quote, Rows3, Settings2, Sigma, Split, SquareCode, Strikethrough, Superscript, Table2, Trash2, Underline as UnderlineIcon, Undo2, X } from 'lucide-vue-next'
import { CommentMark } from '../editor/commentMark'
import { PageBreak } from '../editor/pageBreak'
import { PaginationLayout } from '../editor/paginationExtension'
import { ChangeTracking, ParagraphAttribution, ReviewChange } from '../editor/reviewExtensions'
import { BlockFormatting, Citation, DocflowTextStyle, EnhancedBlockMath, EnhancedInlineMath, EquationReference, Footnote, LazyImage, ResizableTableRow, TabIndent } from '../editor/advancedExtensions'
import { getRealtimeAccessToken, referenceApi } from '../api'
import { confirmDialog, promptDialog } from '../utils/dialog'
import { friendlyMessage } from '../utils/friendlyMessage'
import MathFieldEditor from './MathFieldEditor.vue'
import katex from 'katex'
import 'katex/contrib/mhchem'
import 'katex/dist/katex.min.css'

const props = defineProps({
  docId: { type: Number, required: true },
  user: { type: Object, required: true },
  editable: { type: Boolean, default: true },
  commentable: { type: Boolean, default: true },
  commentsOpen: { type: Boolean, default: false },
  pageSettings: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['status', 'users', 'change', 'upload-images', 'add-comment', 'comment-click', 'page-format-change', 'page-settings-change'])
const fileInput = ref(null)
const selectionEmpty = ref(true)
const trackChanges = ref(false)
const formatBrush = ref(null)
const pendingChanges = ref(0)
let reconnectAttempt = 0
let reconnectProgress = 0
let reconnectTimer = null
let changeEmitTimer = null
let renumbering = false
const specialCharacters = ['©', '®', '™', '±', '×', '÷', '∞', '≈', '≠', '≤', '≥', '°', '℃', '→', '←', '※', '§']
const formulaTabs = [{ key: 'common', label: '常用' }, { key: 'greek', label: '希腊字母' }, { key: 'matrix', label: '矩阵与多行' }, { key: 'chem', label: '化学式' }]
const formulaSymbols = {
  common: [{ latex: '\\frac{a}{b}', title: '分数', preview: 'a/b' }, { latex: '\\sqrt{x}', title: '根号', preview: '√x' }, { latex: '\\sum_{i=1}^{n}', title: '求和', preview: 'Σ' }, { latex: '\\int_a^b', title: '积分', preview: '∫' }, { latex: 'x^{2}', title: '上标', preview: 'x²' }, { latex: 'x_{i}', title: '下标', preview: 'xᵢ' }],
  greek: ['alpha', 'beta', 'gamma', 'delta', 'theta', 'lambda', 'mu', 'pi', 'rho', 'sigma', 'phi', 'omega'].map(name => ({ latex: `\\${name}`, title: name, preview: `&${name === 'lambda' ? 'lambda' : name};` })),
  matrix: [{ latex: '\\begin{matrix}a & b \\\\ c & d\\end{matrix}', title: '矩阵', preview: '[a b; c d]' }, { latex: '\\begin{pmatrix}a & b \\\\ c & d\\end{pmatrix}', title: '圆括号矩阵', preview: '(a b; c d)' }, { latex: '\\begin{aligned}a&=b+c\\\\d&=e-f\\end{aligned}', title: '多行对齐', preview: 'a=b+c · d=e-f' }, { latex: '\\begin{cases}x+y=1\\\\x-y=0\\end{cases}', title: '方程组', preview: '{ x+y=1' }],
  chem: [{ latex: '\\ce{H2O}', title: '水', preview: 'H₂O' }, { latex: '\\ce{CO2 + H2O -> H2CO3}', title: '化学反应', preview: 'CO₂ + H₂O → H₂CO₃' }, { latex: '\\ce{2H2 + O2 -> 2H2O}', title: '配平反应式', preview: '2H₂ + O₂ → 2H₂O' }, { latex: '\\ce{SO4^2-}', title: '离子', preview: 'SO₄²⁻' }]
}
const formulaPanel = reactive({ open: false, tab: 'common', latex: '', mathml: '', block: true, numbered: false, label: '', editPos: null, error: '' })
const referencePanel = reactive({ open: false, loading: false, items: [], doi: '', citeKey: '', title: '', url: '', footnotes: [], error: '' })
const quickDialog = reactive({ open: false, mode: '', title: '', message: '', value: '', options: [] })
const pageSettingsPanel = reactive({ open: false, pageFormat: 'A4', marginTop: 20, marginRight: 20, marginBottom: 20, marginLeft: 20, pageHeader: '', pageFooter: '' })
const pagedEditing = ref(true)
const spreadEditing = ref(false)
const paginationPageCount = ref(1)
const spreadSourcePageCount = ref(1)
const spreadHtml = ref('')
const manualPaperZoom = ref(null)
const spreadPageCount = computed(() => Math.max(1, spreadSourcePageCount.value))
const paperZoom = computed(() => {
  return manualPaperZoom.value == null ? (spreadEditing.value ? .8 : 1) : manualPaperZoom.value
})
const activeFormulaSymbols = computed(() => formulaSymbols[formulaPanel.tab] || [])
const formulaPreview = computed(() => {
  if (!formulaPanel.latex.trim()) return '<span>公式预览</span>'
  try { return katex.renderToString(formulaPanel.latex, { throwOnError: false, displayMode: formulaPanel.block, strict: false }) } catch { return '<span>公式格式有误</span>' }
})
const paperStyle = computed(() => {
  const letter = props.pageSettings?.pageFormat === 'LETTER'
  const paperHeight = letter ? 279 : 297
  const pageCount = Math.max(1, paginationPageCount.value)
  const displayedPages = spreadEditing.value ? spreadPageCount.value : pageCount
  const marginLeft = Number(props.pageSettings?.marginLeft ?? 20)
  const marginRight = Number(props.pageSettings?.marginRight ?? 20)
  const marginTop = Number(props.pageSettings?.marginTop ?? 20)
  const marginBottom = Number(props.pageSettings?.marginBottom ?? 20)
  const paperWidth = letter ? 216 : 210
  const contentWidthMm = paperWidth - marginLeft - marginRight
  return {
    '--paper-width': `${paperWidth}mm`, '--paper-height': `${paperHeight}mm`,
    '--margin-top': `${marginTop}mm`, '--margin-right': `${marginRight}mm`,
    '--margin-bottom': `${marginBottom}mm`, '--margin-left': `${marginLeft}mm`,
    '--page-content-width': `${contentWidthMm}mm`, '--page-content-height': `${paperHeight - marginTop - marginBottom}mm`,
    '--spread-column-gap': `calc(${marginLeft + marginRight}mm + 18px)`,
    '--spread-page-count': String(displayedPages),
    '--spread-total-width': `calc(${paperWidth * displayedPages}mm + ${(displayedPages - 1) * 18}px)`,
    '--spread-content-width': `calc(${contentWidthMm * displayedPages + (marginLeft + marginRight) * (displayedPages - 1)}mm + ${(displayedPages - 1) * 18}px)`,
    '--page-gap': '18px', '--paged-min-height': `calc(${paperHeight * pageCount}mm + ${(pageCount - 1) * 18}px)`
  }
})
const ydoc = new Y.Doc()
const localPersistence = new IndexeddbPersistence(`docflow:crdt:${props.docId}`, ydoc)
const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
const url = import.meta.env.VITE_CRDT_URL || `${protocol}//${location.hostname}:1234`
const provider = new HocuspocusProvider({
  url,
  name: String(props.docId),
  document: ydoc,
  token: () => getRealtimeAccessToken(),
  flushDelay: 80,
  onStatus: ({ status }) => handleProviderStatus(status),
  onSynced: ({ state }) => { if (state !== false) { pendingChanges.value = 0; emitReliability('synced') } },
  onUnsyncedChanges: ({ number }) => { pendingChanges.value = number; emitReliability(provider.status || 'connected') },
  onAwarenessUpdate: ({ states }) => emit('users', states.map(state => state.user).filter(Boolean)),
  onAuthenticationFailed: () => emitReliability('unauthorized')
})

provider.setAwarenessField('user', props.user)

function editFormula(node, pos, block) {
  if (!props.editable) return
  openFormulaPanel({ ...node.attrs, block, editPos: pos })
}

const editor = useEditor({
  editable: props.editable,
  extensions: [
    StarterKit.configure({ undoRedo: false, link: { openOnClick: false }, underline: {} }),
    LazyImage.configure({ inline: false, allowBase64: false }),
    TableKit.configure({ table: { resizable: true }, tableRow: false }),
    ResizableTableRow,
    TaskList,
    TaskItem.configure({ nested: true }),
    EnhancedInlineMath.configure({
      katexOptions: { throwOnError: false, strict: false },
      onClick: (node, pos) => editFormula(node, pos, false)
    }),
    EnhancedBlockMath.configure({ katexOptions: { throwOnError: false, strict: false }, onClick: (node, pos) => editFormula(node, pos, true) }),
    DocflowTextStyle,
    BlockFormatting,
    EquationReference,
    Footnote,
    Citation,
    CommentMark.configure({ onClick: commentId => { emit('comment-click', Number(commentId)); return !props.commentsOpen } }),
    TabIndent,
    PageBreak,
    PaginationLayout.configure({
      isEnabled: () => pagedEditing.value && !spreadEditing.value && !(typeof window.matchMedia === 'function' && window.matchMedia('(max-width: 700px)').matches),
      getPageSettings: () => props.pageSettings,
      onPageCount: count => { paginationPageCount.value = count }
    }),
    ReviewChange,
    ParagraphAttribution.configure({ user: () => props.user }),
    ChangeTracking.configure({ enabled: () => trackChanges.value, user: () => props.user }),
    Collaboration.configure({ document: ydoc, field: 'default' }),
    CollaborationCaret.configure({ provider, user: props.user })
  ],
  onSelectionUpdate: ({ editor: instance }) => { selectionEmpty.value = instance.state.selection.empty; if (formatBrush.value && !instance.state.selection.empty) applyFormatBrush() },
  onUpdate: ({ editor: instance }) => { renumberEquations(instance); clearTimeout(changeEmitTimer); changeEmitTimer = setTimeout(() => emit('change', instance.getHTML()), 180) }
})

onBeforeUnmount(async () => {
  editor.value?.destroy()
  clearInterval(reconnectTimer)
  clearTimeout(changeEmitTimer)
  provider.destroy()
  await localPersistence.destroy()
  ydoc.destroy()
})

function changePageFormat(format) {
  if (!['A4', 'LETTER'].includes(format) || format === props.pageSettings?.pageFormat) return
  emit('page-format-change', format)
}

function togglePagedEditing() {
  pagedEditing.value = !pagedEditing.value
  if (!pagedEditing.value) spreadEditing.value = false
  manualPaperZoom.value = null
}
function toggleSpreadEditing() {
  const opening = !spreadEditing.value
  if (opening) {
    pagedEditing.value = true
    spreadSourcePageCount.value = Math.max(1, paginationPageCount.value)
    refreshSpreadSnapshot()
  }
  spreadEditing.value = opening
  manualPaperZoom.value = null
  requestAnimationFrame(() => {
    if (editor.value) editor.value.view.dispatch(editor.value.state.tr.setMeta('addToHistory', false))
  })
}
function refreshSpreadSnapshot() {
  spreadHtml.value = editor.value?.view.dom.innerHTML || ''
}
function handlePaperWheel(event) {
  if (!(event.ctrlKey || event.metaKey)) return
  event.preventDefault()
  const next = paperZoom.value + (event.deltaY < 0 ? .1 : -.1)
  manualPaperZoom.value = Math.min(2, Math.max(.4, Math.round(next * 10) / 10))
}
function resetPaperZoom() { manualPaperZoom.value = null }

function pageFurnitureStyle(page) {
  const letter = props.pageSettings?.pageFormat === 'LETTER'
  const paperHeight = letter ? 279 : 297
  const paperWidth = letter ? 216 : 210
  return { top: `calc(${(page - 1) * paperHeight}mm + ${(page - 1) * 18}px)`, height: `${paperHeight}mm` }
}
function spreadPageContentStyle(page) {
  const paperHeight = props.pageSettings?.pageFormat === 'LETTER' ? 279 : 297
  const offset = page - 1
  return { top: `calc(-${offset * paperHeight}mm - ${offset * 18}px)` }
}
function openPageSettings() {
  Object.assign(pageSettingsPanel, {
    open: true,
    pageFormat: props.pageSettings?.pageFormat || 'A4',
    marginTop: props.pageSettings?.marginTop ?? 20,
    marginRight: props.pageSettings?.marginRight ?? 20,
    marginBottom: props.pageSettings?.marginBottom ?? 20,
    marginLeft: props.pageSettings?.marginLeft ?? 20,
    pageHeader: props.pageSettings?.pageHeader || '',
    pageFooter: props.pageSettings?.pageFooter || ''
  })
}
function savePageSettings() {
  const margins = ['marginTop', 'marginRight', 'marginBottom', 'marginLeft']
  if (margins.some(key => Number(pageSettingsPanel[key]) < 5 || Number(pageSettingsPanel[key]) > 60)) return
  emit('page-settings-change', {
    pageFormat: pageSettingsPanel.pageFormat,
    marginTop: Number(pageSettingsPanel.marginTop), marginRight: Number(pageSettingsPanel.marginRight),
    marginBottom: Number(pageSettingsPanel.marginBottom), marginLeft: Number(pageSettingsPanel.marginLeft),
    pageHeader: pageSettingsPanel.pageHeader || '', pageFooter: pageSettingsPanel.pageFooter || ''
  })
  pageSettingsPanel.open = false
}

function emitReliability(status) {
  emit('status', { status, pending: pendingChanges.value, reconnectAttempt, reconnectProgress })
}
function handleProviderStatus(status) {
  if (status === 'connected') {
    reconnectAttempt = 0
    reconnectProgress = 100
    clearInterval(reconnectTimer)
  } else if (status === 'connecting' || status === 'disconnected') {
    reconnectAttempt += status === 'disconnected' ? 1 : 0
    reconnectProgress = 10
    clearInterval(reconnectTimer)
    reconnectTimer = setInterval(() => {
      reconnectProgress = Math.min(90, reconnectProgress + 5)
      emitReliability('connecting')
    }, 500)
  }
  emitReliability(status)
}

function openFormulaPanel(options = {}) {
  Object.assign(formulaPanel, {
    open: true,
    tab: options.formulaKind === 'chem' ? 'chem' : 'common',
    latex: options.latex || '',
    mathml: options.mathml || '',
    block: options.block ?? true,
    numbered: Boolean(options.equationLabel || options.equationNumber),
    label: options.equationLabel || '',
    editPos: options.editPos ?? null,
    error: ''
  })
}
function appendFormulaSymbol(latex) { formulaPanel.latex += `${formulaPanel.latex ? ' ' : ''}${latex}` }
function saveFormula() {
  if (!editor.value || !formulaPanel.latex.trim()) return
  const attrs = {
    latex: formulaPanel.latex.trim(),
    mathml: formulaPanel.mathml?.trim() || null,
    equationLabel: formulaPanel.block && formulaPanel.numbered ? (formulaPanel.label || `eq-${Date.now().toString(36)}`) : null,
    equationNumber: formulaPanel.block && formulaPanel.numbered ? '1' : null,
    formulaKind: formulaPanel.tab === 'chem' ? 'chem' : 'math'
  }
  if (formulaPanel.editPos != null) {
    const node = editor.value.state.doc.nodeAt(formulaPanel.editPos)
    if (node) editor.value.view.dispatch(editor.value.state.tr.setNodeMarkup(formulaPanel.editPos, undefined, { ...node.attrs, ...attrs }))
  } else if (formulaPanel.block) {
    editor.value.chain().focus().insertContent({ type: 'blockMath', attrs }).run()
  } else {
    editor.value.chain().focus().insertInlineMath(attrs).run()
  }
  formulaPanel.open = false
  renumberEquations(editor.value)
}
function mathMlNodeToLatex(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue?.trim() || ''
  const children = [...node.childNodes].map(mathMlNodeToLatex)
  const [a = '', b = ''] = children
  switch (node.localName) {
    case 'math': case 'mrow': case 'semantics': return children.join(' ')
    case 'mi': case 'mn': case 'mo': case 'mtext': return node.textContent?.trim() || ''
    case 'mfrac': return `\\frac{${a}}{${b}}`
    case 'msup': return `{${a}}^{${b}}`
    case 'msub': return `{${a}}_{${b}}`
    case 'msubsup': return `{${a}}_{${b}}^{${children[2] || ''}}`
    case 'msqrt': return `\\sqrt{${children.join(' ')}}`
    case 'mroot': return `\\sqrt[${b}]{${a}}`
    case 'mtr': return children.join(' & ')
    case 'mtable': return `\\begin{matrix}${children.join(' \\\\ ')}\\end{matrix}`
    case 'annotation': return ''
    default: return children.join(' ')
  }
}
function importMathMl() {
  const parsed = new DOMParser().parseFromString(formulaPanel.mathml, 'application/xml')
  if (parsed.querySelector('parsererror') || !parsed.documentElement?.localName?.includes('math')) {
    formulaPanel.error = 'MathML 格式无效，请粘贴完整的 <math>...</math> 内容。'
    return
  }
  const latex = mathMlNodeToLatex(parsed.documentElement).replace(/\s+/g, ' ').trim()
  if (latex) { formulaPanel.latex = latex; formulaPanel.error = '' }
}
function download(name, content, type) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 500)
}
function exportMathMl() {
  if (!editor.value) return
  const formulas = []
  editor.value.state.doc.descendants(node => { if (node.type.name === 'blockMath' || node.type.name === 'inlineMath') formulas.push(node.attrs.mathml || katex.renderToString(node.attrs.latex, { throwOnError: false, output: 'mathml' }).replace(/^.*?<math/s, '<math').replace(/<\/math>.*$/s, '</math>')) })
  const body = formulas.join('\n')
  download(`docflow-formulas-${props.docId}.mml`, `<?xml version="1.0" encoding="UTF-8"?>\n<math-list xmlns="http://www.w3.org/1998/Math/MathML">\n${body}\n</math-list>`, 'application/mathml+xml;charset=utf-8')
}
function renumberEquations(instance) {
  if (renumbering) return
  let number = 0
  const labels = new Map()
  const changes = []
  instance.state.doc.descendants((node, pos) => {
    if (node.type.name !== 'blockMath' || !node.attrs.equationLabel) return
    number += 1
    labels.set(node.attrs.equationLabel, String(number))
    if (String(node.attrs.equationNumber || '') !== String(number)) changes.push({ pos, attrs: { ...node.attrs, equationNumber: String(number) } })
  })
  instance.state.doc.descendants((node, pos) => {
    if (node.type.name !== 'equationReference') return
    const resolved = labels.get(node.attrs.label) || '?'
    if (node.attrs.number !== resolved) changes.push({ pos, attrs: { ...node.attrs, number: resolved } })
  })
  if (!changes.length) return
  renumbering = true
  const tr = instance.state.tr.setMeta('addToHistory', false)
  changes.reverse().forEach(change => tr.setNodeMarkup(change.pos, undefined, change.attrs))
  instance.view.dispatch(tr)
  renumbering = false
}
function insertEquationReference() {
  if (!editor.value) return
  const equations = []
  editor.value.state.doc.descendants(node => { if (node.type.name === 'blockMath' && node.attrs.equationLabel) equations.push(node.attrs) })
  if (!equations.length) {
    Object.assign(quickDialog, { open: true, mode: 'message', title: '公式交叉引用', message: '当前文档还没有带编号的独立公式。请先插入公式并勾选“自动编号”。', value: '', options: [] })
    return
  }
  Object.assign(quickDialog, { open: true, mode: 'equation', title: '插入公式交叉引用', message: '', value: equations[0].equationLabel, options: equations })
}

function setTextStyle(name, value) {
  if (!editor.value) return
  if (!value) editor.value.chain().focus().unsetMark('docflowTextStyle').run()
  else editor.value.chain().focus().setMark('docflowTextStyle', { [name]: value }).run()
}
function setBlock(name, value) { editor.value?.chain().focus().setBlockFormatting({ [name]: value || null }).run() }
function toggleFormatBrush() {
  if (formatBrush.value) { formatBrush.value = null; return }
  const instance = editor.value
  if (!instance) return
  const parent = instance.state.selection.$from.parent
  formatBrush.value = { marks: instance.state.storedMarks || instance.state.selection.$from.marks(), block: { textAlign: parent.attrs.textAlign, lineHeight: parent.attrs.lineHeight, indent: parent.attrs.indent } }
}
function applyFormatBrush() {
  const instance = editor.value
  if (!instance || !formatBrush.value) return
  let chain = instance.chain().focus().unsetAllMarks()
  formatBrush.value.marks.forEach(mark => { chain = chain.setMark(mark.type.name, mark.attrs) })
  chain.setBlockFormatting(formatBrush.value.block).run()
  formatBrush.value = null
}
function insertSpecialCharacter(event) { const value = event.target.value; event.target.value = ''; if (value) editor.value?.chain().focus().insertContent(value).run() }
function insertFootnote() {
  Object.assign(quickDialog, { open: true, mode: 'footnote', title: '插入脚注', message: '正文会显示自动编号的上标，输入内容保存在该编号对应的脚注中。', value: '', options: [] })
}
function closeQuickDialog() { quickDialog.open = false }
function submitQuickDialog() {
  if (!editor.value || !quickDialog.value) return
  if (quickDialog.mode === 'footnote') {
    let count = 0; editor.value.state.doc.descendants(node => { if (node.type.name === 'footnote') count += 1 })
    editor.value.chain().focus().insertContent({ type: 'footnote', attrs: { noteId: String(count + 1), noteText: quickDialog.value.trim() } }).run()
  } else if (quickDialog.mode === 'equation') {
    const target = quickDialog.options.find(item => item.equationLabel === quickDialog.value)
    if (target) editor.value.chain().focus().insertContent({ type: 'equationReference', attrs: { label: target.equationLabel, number: target.equationNumber } }).run()
  }
  closeQuickDialog()
}
async function insertCitation(reference = null) {
  if (!reference) {
    referencePanel.error = ''
    referencePanel.open = true
    await loadReferenceLibrary()
    return
  }
  const key = reference.citeKey?.trim()
  const title = reference.title || referenceTitle(reference)
  const url = reference.url || referenceUrl(reference)
  if (!key || !title) {
    referencePanel.error = '该文献缺少引用键或标题，请完善后重新加入文献库。'
    return
  }
  editor.value?.chain().focus().insertContent({ type: 'citation', attrs: { key, title: title.trim(), url: url.trim() } }).run()
  referencePanel.open = false
}
function referenceTitle(reference) { try { const csl = typeof reference?.cslJson === 'string' ? JSON.parse(reference.cslJson) : reference; return csl?.title || ''; } catch { return ''; } }
function referenceUrl(reference) { try { const csl = typeof reference?.cslJson === 'string' ? JSON.parse(reference.cslJson) : reference; return csl?.URL || (reference?.doi ? `https://doi.org/${reference.doi}` : ''); } catch { return ''; } }
async function loadReferenceLibrary() { referencePanel.loading = true; referencePanel.error = ''; try { const response = await referenceApi.list(props.docId); referencePanel.items = response.data || []; syncFootnotes() } catch (exception) { referencePanel.error = exception.message || '文献库加载失败' } finally { referencePanel.loading = false } }
async function importReferenceDoi() { if (!referencePanel.doi) return; referencePanel.error = ''; try { const response = await referenceApi.importDoi(props.docId, { doi: referencePanel.doi, citeKey: referencePanel.citeKey || null }); referencePanel.items = [...referencePanel.items, response.data]; referencePanel.doi = ''; referencePanel.citeKey = '' } catch (exception) { referencePanel.error = exception.message || 'DOI 导入失败' } }
async function createReference() {
  if (!referencePanel.citeKey || !referencePanel.title) { referencePanel.error = '请填写引用键和文献标题。'; return }
  referencePanel.error = ''
  try {
    const csl = { id: referencePanel.citeKey, type: 'article', title: referencePanel.title }
    if (referencePanel.url) csl.URL = referencePanel.url
    const response = await referenceApi.create(props.docId, { citeKey: referencePanel.citeKey, cslJson: JSON.stringify(csl) })
    referencePanel.items = [...referencePanel.items, response.data]
    referencePanel.citeKey = ''; referencePanel.title = ''; referencePanel.url = ''
  } catch (exception) { referencePanel.error = exception.message || '文献条目创建失败' }
}
async function removeReference(item) { if (!(await confirmDialog(`删除文献“${item.citeKey}”？已插入正文的引用标记不会自动删除。`, { title: '删除文献', confirmText: '删除', danger: true }))) return; try { await referenceApi.remove(props.docId, item.id); referencePanel.items = referencePanel.items.filter(value => value.id !== item.id) } catch (exception) { referencePanel.error = friendlyMessage(exception.message, '文献删除失败，请稍后重试') } }
function syncFootnotes() { const notes = []; editor.value?.state.doc.descendants(node => { if (node.type.name === 'footnote') notes.push({ id: node.attrs.noteId, text: node.attrs.noteText }) }); referencePanel.footnotes = notes }
function sortCurrentTable(direction) {
  const instance = editor.value
  if (!instance) return
  const { $from } = instance.state.selection
  let tableDepth = -1; let rowDepth = -1
  for (let depth = $from.depth; depth > 0; depth -= 1) { if ($from.node(depth).type.name === 'tableRow') rowDepth = depth; if ($from.node(depth).type.name === 'table') { tableDepth = depth; break } }
  if (tableDepth < 0 || rowDepth < 0) return
  const table = $from.node(tableDepth); const column = $from.index(rowDepth); const rows = []
  table.forEach(row => rows.push(row))
  const hasHeader = rows[0]?.child(0)?.type.name === 'tableHeader'; const header = hasHeader ? rows.shift() : null
  rows.sort((left, right) => direction * (left.child(Math.min(column, left.childCount - 1)).textContent || '').localeCompare(right.child(Math.min(column, right.childCount - 1)).textContent || '', 'zh-CN', { numeric: true }))
  const replacement = table.type.create(table.attrs, header ? [header, ...rows] : rows)
  const position = $from.before(tableDepth)
  instance.view.dispatch(instance.state.tr.replaceWith(position, position + table.nodeSize, replacement))
}

function changeRowHeight(delta) {
  const instance = editor.value
  if (!instance) return
  const { $from } = instance.state.selection
  for (let depth = $from.depth; depth > 0; depth -= 1) {
    const row = $from.node(depth)
    if (row.type.name !== 'tableRow') continue
    const position = $from.before(depth)
    const current = Number(row.attrs.rowHeight || 0) || 44
    const next = Math.min(240, Math.max(32, current + delta))
    instance.view.dispatch(instance.state.tr.setNodeMarkup(position, undefined, { ...row.attrs, rowHeight: next }))
    return
  }
}

function chooseImages(event) { const files=[...(event.target.files||[])];event.target.value='';if(files.length)emit('upload-images',files,urls=>urls.forEach(src=>editor.value?.chain().focus().setImage({src}).run())) }
async function setLink() { const current=editor.value?.getAttributes('link').href||'';const href=await promptDialog('插入链接',current||'https://',{message:'请输入完整的网页地址。',placeholder:'https://example.com',confirmText:'应用'});if(href===null)return;if(!href.trim())editor.value?.chain().focus().unsetLink().run();else editor.value?.chain().focus().setLink({href:href.trim(),target:'_blank'}).run() }
function requestComment() { if (!editor.value || editor.value.state.selection.empty) return; emit('add-comment', editor.value.state.doc.textBetween(editor.value.state.selection.from, editor.value.state.selection.to, ' ')) }
function applyAllChanges(action) {
  if (!editor.value || !props.editable) return
  const ranges = []
  editor.value.state.doc.descendants((node, pos) => {
    if (!node.isText) return
    node.marks.filter(mark => mark.type.name === 'reviewChange').forEach(mark => ranges.push({ from: pos, to: pos + node.nodeSize, mark }))
  })
  const tr = editor.value.state.tr.setMeta('review-change', true)
  ranges.sort((a, b) => b.from - a.from).forEach(({ from, to, mark }) => {
    const removeText = (action === 'accept' && mark.attrs.changeType === 'delete') || (action === 'reject' && mark.attrs.changeType === 'insert')
    if (removeText) tr.delete(from, to)
    else tr.removeMark(from, to, mark)
  })
  if (tr.docChanged) editor.value.view.dispatch(tr)
}
function addComment(commentId) { if (!commentId || !editor.value || editor.value.state.selection.empty) return false; return editor.value.chain().focus().setMark('comment', { commentId: String(commentId) }).run() }
function removeComment(commentId) { if (!editor.value) return; const id=String(commentId);const { tr, doc }=editor.value.state;doc.descendants((node,pos)=>{if(!node.isText)return;node.marks.filter(mark=>mark.type.name==='comment'&&String(mark.attrs.commentId)===id).forEach(mark=>tr.removeMark(pos,pos+node.nodeSize,mark))});if(tr.docChanged)editor.value.view.dispatch(tr) }
function focusComment(commentId) { if (!editor.value) return false;const id=String(commentId);let found=null;editor.value.state.doc.descendants((node,pos)=>{if(found||!node.isText)return;const match=node.marks.some(mark=>mark.type.name==='comment'&&String(mark.attrs.commentId)===id);if(match)found={from:pos,to:pos+node.nodeSize}});if(!found)return false;editor.value.chain().focus().setTextSelection(found).scrollIntoView().run();return true }
function setContent(html) { if (!props.editable || !editor.value) return false; editor.value.commands.setContent(html || '<p></p>'); return true }
function replaceText(query,replacement,replaceAll=false){if(!props.editable||!query||!editor.value)return 0;const root=document.createElement('div');root.innerHTML=editor.value.getHTML();const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node,count=0;while((node=walker.nextNode())){if(!node.nodeValue?.includes(query))continue;if(replaceAll){count+=node.nodeValue.split(query).length-1;node.nodeValue=node.nodeValue.split(query).join(replacement)}else{node.nodeValue=node.nodeValue.replace(query,replacement);count=1;break}}if(count)editor.value.commands.setContent(root.innerHTML);return count}
function getOutline() { const result=[];editor.value?.state.doc.descendants((node,pos)=>{if(node.type.name==='heading')result.push({pos,level:node.attrs.level,text:node.textContent,collapsed:Boolean(node.attrs.collapsed)})});return result }
function focusHeading(index){const item=getOutline()[index];if(!item)return false;editor.value?.chain().focus().setTextSelection(item.pos+1).scrollIntoView().run();return true}
function toggleHeading(index){const item=getOutline()[index];if(!item||!editor.value)return false;const node=editor.value.state.doc.nodeAt(item.pos);editor.value.view.dispatch(editor.value.state.tr.setNodeMarkup(item.pos,undefined,{...node.attrs,collapsed:!node.attrs.collapsed}));return true}
defineExpose({ addComment, focusComment, removeComment, replaceText, setContent, applyAllChanges, getOutline, focusHeading, toggleHeading, insertCitation, getHtml: () => editor.value?.getHTML() || '' })
</script>

<style scoped>
.rich-editor{height:100%;display:flex;flex-direction:column;background:white}.rich-toolbar,.advanced-toolbar{min-height:42px;display:flex;align-items:center;gap:3px;overflow-x:auto;padding:4px 8px;border-bottom:1px solid var(--border);background:white}.advanced-toolbar{min-height:38px;background:#fbfcfe}.rich-toolbar button,.advanced-toolbar button{width:32px;height:32px;flex:0 0 auto;display:grid;place-items:center;border:0;border-radius:4px;background:transparent;color:#4b5563;cursor:pointer;font-size:11px;font-weight:700}.rich-toolbar button:hover,.rich-toolbar button.active,.advanced-toolbar button:hover,.advanced-toolbar button.active{background:var(--primary-light);color:var(--primary)}.rich-toolbar button:disabled,.advanced-toolbar button:disabled,.advanced-toolbar select:disabled{opacity:.45;cursor:not-allowed}.rich-toolbar>span,.advanced-toolbar>span{width:1px;height:22px;flex:0 0 auto;margin:0 4px;background:var(--border)}.advanced-toolbar select{height:30px;flex:0 0 auto;border:1px solid var(--border);border-radius:4px;background:white;color:var(--text-secondary);font-size:11px}.rich-content{box-sizing:border-box;flex:1;min-height:0;overflow-x:auto;overflow-y:scroll;overscroll-behavior:contain;scrollbar-gutter:stable;padding:20px max(20px,calc((100% - var(--paper-width) - 15px)/2)) 48px;background:#eef2f6;scrollbar-color:#7c8da3 #dfe7f0;scrollbar-width:auto}.rich-content::-webkit-scrollbar{width:14px;height:14px}.rich-content::-webkit-scrollbar-track{background:#dfe7f0}.rich-content::-webkit-scrollbar-thumb{min-height:56px;border:3px solid #dfe7f0;border-radius:7px;background:#7c8da3}.rich-content::-webkit-scrollbar-thumb:hover{background:#52657c}.rich-content::-webkit-scrollbar-corner{background:#dfe7f0}.hidden-input{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
.page-count{flex:0 0 auto;padding-right:5px;color:var(--text-muted);font-size:11px;white-space:nowrap}
.advanced-toolbar .zoom-indicator{width:auto;min-width:46px;padding:0 6px;color:var(--text-secondary);font-weight:600}
.document-paper{position:relative;box-sizing:border-box;width:var(--paper-width);min-height:var(--paper-height);margin:0 auto;padding:var(--margin-top) var(--margin-right) var(--margin-bottom) var(--margin-left);border:1px solid #cbd5e1;background:white;box-shadow:0 2px 10px rgba(15,23,42,.08);transform-origin:top center}
.document-paper.paged-editing{width:var(--paper-width);min-width:var(--paper-width);min-height:var(--paged-min-height);padding:var(--margin-top) var(--margin-right) var(--margin-bottom) var(--margin-left);border:0;background-color:white;background-image:repeating-linear-gradient(to bottom,#fff 0,#fff calc(var(--paper-height) - 1px),#cbd5e1 calc(var(--paper-height) - 1px),#cbd5e1 var(--paper-height),#dfe7f0 var(--paper-height),#dfe7f0 calc(var(--paper-height) + var(--page-gap) - 1px),#cbd5e1 calc(var(--paper-height) + var(--page-gap) - 1px),#cbd5e1 calc(var(--paper-height) + var(--page-gap)));box-shadow:0 2px 10px rgba(15,23,42,.10)}
.document-paper.paged-editing .editor-host{position:relative;z-index:2;width:100%;height:auto;min-height:calc(var(--paper-height) - var(--margin-top) - var(--margin-bottom));background:transparent;box-shadow:none}
.document-paper.paged-editing :deep(.tiptap){height:auto;min-height:inherit;column-width:auto;column-count:1;column-gap:normal}
.document-paper.paged-editing :deep(.tiptap>*){content-visibility:visible;contain:none}
.document-paper.paged-editing :deep(.pagination-page-spacer){display:block;width:100%;margin:0;padding:0;pointer-events:none;user-select:none}
.document-paper.paged-editing :deep(.pagination-inline-spacer){display:block;max-width:100%;line-height:0}
.document-paper.paged-editing :deep(.pagination-table-cell-spacer){min-width:0;border:0;background:transparent}
.document-paper.paged-editing :deep(.page-break){box-sizing:border-box;margin:0 calc(var(--margin-right) * -1) 0 calc(var(--margin-left) * -1);border:0;background:transparent}
.spread-grid{box-sizing:border-box;display:grid;grid-template-columns:repeat(2,var(--paper-width));align-items:start;gap:var(--page-gap);width:max-content;margin:0 auto;transform-origin:top center}.spread-page{position:relative;box-sizing:border-box;width:var(--paper-width);height:var(--paper-height);overflow:hidden;border:1px solid #cbd5e1;background:white;box-shadow:0 2px 10px rgba(15,23,42,.10)}.spread-page:last-child:nth-child(odd){grid-column:1/-1;justify-self:center}.spread-page-body{position:absolute;top:var(--margin-top);right:var(--margin-right);bottom:var(--margin-bottom);left:var(--margin-left);overflow:hidden}.spread-page-clone{position:absolute;left:0;box-sizing:border-box;width:100%;max-width:100%;min-height:var(--page-content-height);margin:0;pointer-events:none;overflow-wrap:anywhere}.spread-page :deep(.spread-page-clone>* ){content-visibility:visible!important;contain:none!important}.spread-page-header{position:absolute;top:7mm;right:var(--margin-right);left:var(--margin-left);overflow:hidden;color:#64748b;font-size:10px;text-align:center;text-overflow:ellipsis;white-space:nowrap}.spread-page-footer{position:absolute;right:var(--margin-right);bottom:6mm;left:var(--margin-left);display:flex;justify-content:center;gap:10px;color:#64748b;font-size:10px}.spread-page-footer span:first-child:not(:empty)::after{content:' ·'}.spread-page :deep(.pagination-page-spacer){display:block;width:100%;margin:0;padding:0;pointer-events:none;user-select:none}.spread-page :deep(.pagination-inline-spacer){display:block;max-width:100%;line-height:0}.spread-page :deep(.pagination-table-cell-spacer){min-width:0;border:0;background:transparent}.spread-page :deep(.tableWrapper){max-width:100%;overflow:hidden}.spread-page :deep(table),.spread-page :deep(img),.spread-page :deep(pre){max-width:100%}.spread-page :deep(th),.spread-page :deep(td){min-width:0}
.page-furniture{position:absolute;inset:0;z-index:3;pointer-events:none;color:#64748b;font-size:10px}.page-furniture-item{position:absolute;left:0;width:100%}.page-margin-mask{position:absolute;z-index:1;left:0;width:100%;background:#fff}.page-margin-mask-top{top:0;height:var(--margin-top)}.page-margin-mask-bottom{bottom:calc(var(--page-gap) * -1);height:calc(var(--margin-bottom) + var(--page-gap));background:linear-gradient(to bottom,#fff 0,#fff calc(var(--margin-bottom) - 1px),#cbd5e1 calc(var(--margin-bottom) - 1px),#cbd5e1 var(--margin-bottom),#dfe7f0 var(--margin-bottom),#dfe7f0 calc(100% - 1px),#cbd5e1 calc(100% - 1px),#cbd5e1 100%)}.page-header-text,.page-footer-text{z-index:2}.page-header-text{position:absolute;top:7mm;left:var(--margin-left);right:var(--margin-right);overflow:hidden;text-align:center;text-overflow:ellipsis;white-space:nowrap}.page-footer-text{position:absolute;left:var(--margin-left);right:var(--margin-right);bottom:6mm;display:flex;justify-content:center;gap:10px}.page-footer-text span:first-child:not(:empty)::after{content:' ·'}
:deep(.tiptap){box-sizing:border-box;min-height:calc(var(--paper-height) - var(--margin-top) - var(--margin-bottom) - 1px);outline:none;color:#1f2937;line-height:1.75;overflow-wrap:anywhere}:deep(.tiptap>* ){content-visibility:auto;contain-intrinsic-size:auto 32px}:deep(.tiptap>p){break-inside:auto;page-break-inside:auto}:deep(.tiptap h1){font-size:2em;margin:1em 0 .5em}:deep(.tiptap h2){font-size:1.5em;margin:1em 0 .5em}:deep(.tiptap p){margin:.55em 0}:deep(.tiptap blockquote){margin:1em 0;padding-left:14px;border-left:3px solid #93b4e8;color:#64748b}:deep(.tiptap img){max-width:100%;height:auto}:deep(.tiptap ul),:deep(.tiptap ol){padding-left:24px}:deep(.tiptap .tableWrapper){overflow-x:auto}:deep(.tiptap table){width:100%;margin:1em 0;border-collapse:collapse;table-layout:fixed}:deep(.tiptap tr){min-height:32px}:deep(.tiptap th),:deep(.tiptap td){position:relative;min-width:80px;padding:7px 9px;border:1px solid var(--border);vertical-align:top}:deep(.tiptap th){background:#f1f5f9}:deep(.tiptap .selectedCell::after){content:'';position:absolute;inset:0;z-index:2;pointer-events:none;background:rgba(37,99,235,.16);outline:2px solid #2563eb;outline-offset:-2px}:deep(.tiptap .column-resize-handle){position:absolute;top:0;right:-2px;bottom:-2px;width:4px;background:#2563eb;pointer-events:none}:deep(.tiptap.resize-cursor){cursor:col-resize}:deep(.tiptap pre){overflow:auto;padding:12px;border-radius:6px;background:#111827;color:#f8fafc}:deep(.tiptap code){padding:1px 4px;border-radius:3px;background:#eef2f7}:deep(.tiptap pre code){padding:0;background:transparent}:deep(.comment-highlight){border-bottom:2px solid #f59e0b;background:#fef3c7;cursor:pointer}:deep([data-type='block-math']){position:relative;margin:18px 0;padding:4px 54px;text-align:center}:deep([data-type='block-math'][data-equation-number]::after){content:'(' attr(data-equation-number) ')';position:absolute;right:10px;top:50%;transform:translateY(-50%);color:#64748b;font-size:12px}:deep([data-type='inline-math']){display:inline-block;padding:0 2px}:deep(.equation-reference),:deep(.docflow-citation){color:#2563eb;cursor:pointer}:deep(.docflow-footnote){position:relative;top:-.35em;margin:0 1px;color:#2563eb;cursor:help;font-size:.72em;font-weight:700;line-height:0}:deep(.heading-collapsed-content){display:none}:deep(h1[data-collapsed='true']::after),:deep(h2[data-collapsed='true']::after),:deep(h3[data-collapsed='true']::after){content:'  …';color:#94a3b8;font-size:.7em}:deep([data-font-family='sans']){font-family:"Microsoft YaHei",Arial,sans-serif}:deep([data-font-family='serif']){font-family:SimSun,"Songti SC",serif}:deep([data-font-family='kai']){font-family:KaiTi,"Kaiti SC",serif}:deep([data-font-family='mono']){font-family:Consolas,monospace}:deep([data-font-size='12']){font-size:12px}:deep([data-font-size='14']){font-size:14px}:deep([data-font-size='16']){font-size:16px}:deep([data-font-size='18']){font-size:18px}:deep([data-font-size='20']){font-size:20px}:deep([data-font-size='24']){font-size:24px}:deep([data-font-size='28']){font-size:28px}:deep([data-font-size='32']){font-size:32px}:deep([data-text-color='red']){color:#dc2626}:deep([data-text-color='blue']){color:#2563eb}:deep([data-text-color='green']){color:#15803d}:deep([data-text-color='gray']){color:#64748b}:deep([data-text-color='purple']){color:#7e22ce}:deep([data-text-align='left']){text-align:left}:deep([data-text-align='center']){text-align:center}:deep([data-text-align='right']){text-align:right}:deep([data-text-align='justify']){text-align:justify}:deep([data-line-height='1.25']){line-height:1.25}:deep([data-line-height='1.5']){line-height:1.5}:deep([data-line-height='1.75']){line-height:1.75}:deep([data-line-height='2']){line-height:2}:deep([data-indent='1']){margin-left:2em}:deep([data-indent='2']){margin-left:4em}:deep([data-indent='3']){margin-left:6em}:deep([data-indent='4']){margin-left:8em}:deep([data-indent='5']){margin-left:10em}:deep([data-indent='6']){margin-left:12em}:deep([data-indent='7']){margin-left:14em}:deep([data-indent='8']){margin-left:16em}:deep(.page-break){height:20px;margin:24px calc(var(--margin-right) * -1) 24px calc(var(--margin-left) * -1);border-top:2px dashed #94a3b8;border-bottom:2px dashed #94a3b8;background:#eef2f6}:deep([data-change-type='insert']){border-bottom:2px solid #22c55e;background:#dcfce7}:deep([data-change-type='delete']){color:#b91c1c;background:#fee2e2;text-decoration:line-through}:deep(.collaboration-caret__caret),:deep(.collaboration-carets__caret){border-left:2px solid currentColor;border-right:2px solid currentColor;margin-left:-1px;pointer-events:none;position:relative}:deep(.collaboration-caret__label),:deep(.collaboration-carets__label){position:absolute;top:-1.4em;left:-2px;padding:1px 4px;border-radius:3px 3px 3px 0;background:currentColor;color:white;font-size:10px;white-space:nowrap}:deep(.collaboration-caret__selection),:deep(.collaboration-carets__selection){background-color:currentColor;opacity:.22;pointer-events:none}
.formula-backdrop{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:16px;background:rgba(15,23,42,.35)}.formula-panel{width:min(680px,100%);max-height:calc(100vh - 32px);overflow:auto;padding:16px;border-radius:8px;background:white;box-shadow:0 18px 60px rgba(15,23,42,.2)}.formula-panel header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.formula-panel header button{border:0;background:transparent;cursor:pointer}.formula-tabs,.formula-actions{display:flex;gap:6px}.formula-tabs button,.formula-actions button{min-height:32px;padding:5px 10px;border:1px solid var(--border);border-radius:4px;background:white;cursor:pointer}.formula-tabs button.active,.formula-actions button.primary{border-color:var(--primary);background:var(--primary);color:white}.symbol-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:5px;margin:10px 0}.symbol-grid button{min-height:38px;border:1px solid var(--border);border-radius:4px;background:#f8fafc;cursor:pointer}.formula-panel>label{display:grid;gap:5px;margin-top:10px;color:var(--text-secondary);font-size:12px}.formula-panel textarea,.formula-options input{box-sizing:border-box;width:100%;padding:8px;border:1px solid var(--border);border-radius:4px;resize:vertical}.formula-preview{min-height:58px;display:grid;place-items:center;margin:8px 0;padding:8px;border:1px dashed var(--border);overflow:auto}.formula-options{display:grid;grid-template-columns:auto auto 1fr;align-items:center;gap:12px}.formula-actions{justify-content:flex-end;margin-top:14px;flex-wrap:wrap}
.reference-panel{width:min(760px,100%)}.reference-import{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:6px;margin:9px 0}.reference-import.manual-reference{grid-template-columns:minmax(120px,.7fr) minmax(160px,1.2fr) minmax(150px,1fr) auto}.reference-import input{min-width:0;padding:8px;border:1px solid var(--border);border-radius:4px}.reference-import button,.reference-items button{border:1px solid var(--border);border-radius:4px;background:white;cursor:pointer}.reference-import button{padding:7px 10px}.reference-items{display:grid;gap:6px;max-height:260px;overflow:auto}.reference-items article{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px;border:1px solid var(--border);border-radius:5px}.reference-items article>div:first-child{display:grid;gap:2px;min-width:0}.reference-items span,.reference-items small{overflow:hidden;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.reference-items small{color:var(--text-muted);font-size:10px}.reference-items article>div:last-child{display:flex;gap:5px}.reference-items button{min-width:32px;padding:5px 7px}.reference-items .danger{color:var(--danger)}.reference-note{color:var(--text-muted);font-size:12px}.footnote-manager{margin-top:16px;padding-top:12px;border-top:1px solid var(--border)}.footnote-manager h3{margin:0 0 8px;font-size:13px}.footnote-manager p{margin:5px 0;font-size:12px}.footnote-manager strong{margin-right:6px;color:var(--primary)}
.quick-dialog,.page-settings-dialog{width:min(440px,100%);padding:16px;border-radius:8px;background:white;box-shadow:0 18px 60px rgba(15,23,42,.24)}.quick-dialog header,.page-settings-dialog header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.quick-dialog header button,.page-settings-dialog header button{border:0;background:transparent;cursor:pointer}.quick-dialog>p{margin:0 0 12px;color:var(--text-secondary);font-size:12px;line-height:1.6}.quick-dialog label,.page-settings-dialog label{display:grid;gap:6px;color:var(--text-secondary);font-size:12px;font-weight:600}.quick-dialog textarea,.quick-dialog select,.page-settings-dialog input,.page-settings-dialog select{box-sizing:border-box;width:100%;padding:8px;border:1px solid var(--border);border-radius:4px;background:white;resize:vertical}.quick-dialog footer,.page-settings-dialog footer{display:flex;justify-content:flex-end;gap:7px;margin-top:14px}.quick-dialog footer button,.page-settings-dialog footer button{min-height:32px;padding:5px 11px;border:1px solid var(--border);border-radius:4px;background:white;cursor:pointer}.quick-dialog footer .primary,.page-settings-dialog footer .primary{border-color:var(--primary);background:var(--primary);color:white}.page-settings-dialog{display:grid;gap:11px}.page-margin-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.dialog-error{margin:8px 0 0;color:#b91c1c;font-size:12px}
@media(max-width:700px){.rich-content{padding:10px 8px 30px}.document-paper,.document-paper.paged-editing{width:100%;min-width:0;min-height:calc(100vh - 142px);padding:28px 22px;background:white;background-image:none}.document-paper.paged-editing .editor-host{position:relative;inset:auto;width:100%;height:auto;min-height:calc(100vh - 198px);background:white;box-shadow:none}.document-paper.paged-editing :deep(.tiptap){height:auto;min-height:calc(100vh - 198px);column-width:auto;column-count:1}.spread-grid{grid-template-columns:var(--paper-width)}.spread-page:last-child:nth-child(odd){grid-column:auto}.reference-import,.reference-import.manual-reference{grid-template-columns:1fr}.reference-import button{width:100%}}
</style>
