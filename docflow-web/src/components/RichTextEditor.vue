<template>
  <div class="rich-editor">
    <div v-if="editor" class="rich-toolbar">
      <button type="button" :class="{active:editor.isActive('bold')}" title="加粗" @click="editor.chain().focus().toggleBold().run()"><Bold :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('italic')}" title="斜体" @click="editor.chain().focus().toggleItalic().run()"><Italic :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('underline')}" title="下划线" @click="editor.chain().focus().toggleUnderline().run()"><UnderlineIcon :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('strike')}" title="删除线" @click="editor.chain().focus().toggleStrike().run()"><Strikethrough :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('code')}" title="行内代码" @click="editor.chain().focus().toggleCode().run()"><Code2 :size="17"/></button>
      <span/>
      <button type="button" :class="{active:editor.isActive('heading',{level:1})}" title="一级标题" @click="editor.chain().focus().toggleHeading({level:1}).run()">H1</button>
      <button type="button" :class="{active:editor.isActive('heading',{level:2})}" title="二级标题" @click="editor.chain().focus().toggleHeading({level:2}).run()">H2</button>
      <button type="button" :class="{active:editor.isActive('bulletList')}" title="无序列表" @click="editor.chain().focus().toggleBulletList().run()"><List :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('orderedList')}" title="有序列表" @click="editor.chain().focus().toggleOrderedList().run()"><ListOrdered :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('taskList')}" title="任务列表" @click="editor.chain().focus().toggleTaskList().run()"><ListChecks :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('blockquote')}" title="引用" @click="editor.chain().focus().toggleBlockquote().run()"><Quote :size="17"/></button>
      <button type="button" :class="{active:editor.isActive('codeBlock')}" title="代码块" @click="editor.chain().focus().toggleCodeBlock().run()"><SquareCode :size="17"/></button>
      <button type="button" title="插入链接" @click="setLink"><LinkIcon :size="17"/></button>
      <button type="button" title="插入表格" @click="editor.chain().focus().insertTable({rows:3,cols:3,withHeaderRow:true}).run()"><Table2 :size="17"/></button>
      <button type="button" title="插入图片" @click="fileInput?.click()"><ImagePlus :size="17"/></button>
      <button type="button" title="插入公式" @click="insertFormula"><Sigma :size="17"/></button>
      <span/>
      <button type="button" title="撤销" @click="editor.chain().focus().undo().run()"><Undo2 :size="17"/></button>
      <button type="button" title="重做" @click="editor.chain().focus().redo().run()"><Redo2 :size="17"/></button>
      <input ref="fileInput" class="hidden-input" type="file" accept="image/*" multiple @change="chooseImages"/>
    </div>
    <EditorContent :editor="editor" class="rich-content"/>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import { TableKit } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { Mathematics } from '@tiptap/extension-mathematics'
import { Bold, Code2, ImagePlus, Italic, Link as LinkIcon, List, ListChecks, ListOrdered, Quote, Redo2, Sigma, SquareCode, Strikethrough, Table2, Underline as UnderlineIcon, Undo2 } from 'lucide-vue-next'
import { sanitizeHtml } from '../utils/contentConversion'
import { confirmDialog, promptDialog } from '../utils/dialog'
import 'katex/dist/katex.min.css'

const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'change', 'upload-images'])
const fileInput = ref(null)
const editor = useEditor({
  content: sanitizeHtml(props.modelValue),
  extensions: [
    StarterKit.configure({ link: { openOnClick: false, autolink: true }, underline: {} }),
    Image.configure({ inline: false, allowBase64: false }),
    TableKit.configure({ table: { resizable: true } }),
    TaskList,
    TaskItem.configure({ nested: true }),
    Mathematics.configure({
      katexOptions: { throwOnError: false, strict: false },
      inlineOptions: { onClick: (node, pos) => editFormula(node, pos, false) },
      blockOptions: { onClick: (node, pos) => editFormula(node, pos, true) }
    })
  ],
  editorProps: {
    handleDrop(view,event){const files=[...(event.dataTransfer?.files||[])].filter(file=>file.type.startsWith('image/'));if(!files.length)return false;event.preventDefault();upload(files);return true},
    handlePaste(view,event){const files=[...(event.clipboardData?.files||[])].filter(file=>file.type.startsWith('image/'));if(!files.length)return false;event.preventDefault();upload(files);return true}
  },
  onUpdate:({editor:instance})=>{const html=instance.getHTML();emit('update:modelValue',html);emit('change',html)}
})

watch(()=>props.modelValue,value=>{const safe=sanitizeHtml(value);if(editor.value&&editor.value.getHTML()!==safe)editor.value.commands.setContent(safe,false)})
onBeforeUnmount(()=>editor.value?.destroy())
function chooseImages(event){const files=[...(event.target.files||[])];event.target.value='';if(files.length)upload(files)}
function upload(files){emit('upload-images',files,urls=>urls.forEach(src=>editor.value?.chain().focus().setImage({src}).run()))}
async function setLink(){const current=editor.value?.getAttributes('link').href||'';const href=await promptDialog('插入链接',current||'https://',{message:'请输入完整的网页地址。',placeholder:'https://example.com',confirmText:'应用'});if(href===null)return;if(!href.trim())editor.value?.chain().focus().unsetLink().run();else editor.value?.chain().focus().setLink({href:href.trim(),target:'_blank'}).run()}
async function insertFormula(){const latex=await promptDialog('插入公式','',{message:'输入 LaTeX 公式，例如 E = mc^2。',placeholder:'E = mc^2',confirmText:'下一步'});if(!latex?.trim())return;const block=await confirmDialog('将公式单独显示为一行吗？选择“行内公式”会将公式插入当前文字中。',{title:'选择公式样式',confirmText:'独立公式',cancelText:'行内公式'});if(block)editor.value?.chain().focus().insertBlockMath({latex:latex.trim()}).run();else editor.value?.chain().focus().insertInlineMath({latex:latex.trim()}).run()}
async function editFormula(node,pos,block){const latex=await promptDialog('编辑公式',node.attrs.latex||'',{message:'修改 LaTeX 公式内容。',confirmText:'保存'});if(latex===null||!latex.trim())return;if(block)editor.value?.commands.updateBlockMath({latex:latex.trim(),pos});else editor.value?.commands.updateInlineMath({latex:latex.trim(),pos})}
</script>

<style scoped>
.rich-editor{height:100%;display:flex;flex-direction:column;background:white}.rich-toolbar{min-height:44px;display:flex;align-items:center;gap:3px;overflow-x:auto;padding:5px 8px;border-bottom:1px solid var(--border);background:white}.rich-toolbar button{width:32px;height:32px;flex:0 0 auto;display:grid;place-items:center;border:0;border-radius:4px;background:transparent;color:#4b5563;cursor:pointer;font-size:12px;font-weight:700}.rich-toolbar button:hover,.rich-toolbar button.active{background:var(--primary-light);color:var(--primary)}.rich-toolbar>span{width:1px;height:22px;flex:0 0 auto;margin:0 4px;background:var(--border)}.rich-content{flex:1;min-height:0;overflow-y:auto;padding:20px 0 48px;background:#eef2f6}.hidden-input{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
:deep(.tiptap){box-sizing:border-box;width:min(860px,calc(100% - 40px));min-height:max(720px,calc(100vh - 168px));margin:0 auto;padding:46px 64px;outline:none;border:1px solid #d8e0e9;background:white;box-shadow:0 2px 10px rgba(15,23,42,.08);color:#1f2937;line-height:1.75;overflow-wrap:anywhere}:deep(.tiptap h1){font-size:2em;margin:1em 0 .5em}:deep(.tiptap h2){font-size:1.5em;margin:1em 0 .5em}:deep(.tiptap p){margin:.55em 0}:deep(.tiptap blockquote){margin:1em 0;padding-left:14px;border-left:3px solid #93b4e8;color:#64748b}:deep(.tiptap img){max-width:100%;height:auto}:deep(.tiptap ul),:deep(.tiptap ol){padding-left:24px}:deep(.tiptap table){width:100%;margin:1em 0;border-collapse:collapse}:deep(.tiptap th),:deep(.tiptap td){min-width:80px;padding:7px 9px;border:1px solid var(--border);vertical-align:top}:deep(.tiptap th){background:#f1f5f9}:deep(.tiptap pre){overflow:auto;padding:12px;border-radius:6px;background:#111827;color:#f8fafc}:deep(.tiptap code){padding:1px 4px;border-radius:3px;background:#eef2f7}:deep(.tiptap pre code){padding:0;background:transparent}:deep(.tiptap ul[data-type='taskList']){list-style:none;padding-left:0}:deep(.tiptap ul[data-type='taskList'] li){display:flex;gap:7px}:deep(.tiptap ul[data-type='taskList'] li>div){flex:1}:deep([data-type='block-math']){margin:18px 0;text-align:center}:deep([data-type='inline-math']){display:inline-block;padding:0 2px}
@media(max-width:700px){.rich-content{padding-top:10px}:deep(.tiptap){width:calc(100% - 16px);min-height:calc(100vh - 142px);padding:28px 22px}}
</style>
