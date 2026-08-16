<template>
  <div class="folder-tree-node">
    <div :class="['folder-row', { active: docStore.currentFolderId === folder.id, dragging: draggingId === folder.id }]" :style="{ paddingLeft: `${level * 14 + 8}px` }" draggable="true" @dragstart.stop="startDrag" @dragend.stop="draggingId = null" @dragover.prevent @drop.stop.prevent="dropFolder" @click="$emit('select', folder.id)">
      <button v-if="folder.children?.length" class="tree-toggle" type="button" :title="expanded ? '收起' : '展开'" @click.stop="expanded = !expanded">
        <ChevronRight :size="14" :class="{ rotated: expanded }" />
      </button>
      <span v-else class="tree-placeholder" />
      <Folder :size="16" />
      <span class="folder-name">{{ folder.name }}</span>
      <span class="folder-actions">
        <button type="button" title="新建子文件夹" @click.stop="$emit('create', folder)"><Plus :size="13" /></button>
        <button type="button" title="重命名或移动" @click.stop="$emit('edit', folder)"><Pencil :size="13" /></button>
        <button type="button" title="删除文件夹" @click.stop="$emit('delete', folder)"><Trash2 :size="13" /></button>
      </span>
    </div>
    <div v-if="expanded && folder.children?.length">
      <FolderTreeNode v-for="child in folder.children" :key="child.id" :folder="child" :level="level + 1" @select="$emit('select', $event)" @create="$emit('create', $event)" @edit="$emit('edit', $event)" @delete="$emit('delete', $event)" @move="$emit('move', $event)" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ChevronRight, Folder, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { useDocStore } from '../store'

const props = defineProps({ folder: { type: Object, required: true }, level: { type: Number, default: 0 } })
const emit = defineEmits(['select', 'create', 'edit', 'delete', 'move'])
const docStore = useDocStore()
const expanded = ref(true)
const draggingId = ref(null)
function startDrag(event) { draggingId.value = props.folder.id; event.dataTransfer.setData('text/folder-id', String(props.folder.id)); event.dataTransfer.effectAllowed = 'move' }
function dropFolder(event) { const sourceId = Number(event.dataTransfer.getData('text/folder-id')); if (sourceId && sourceId !== props.folder.id) emit('move', { sourceId, parentId: props.folder.id }) }
</script>

<style scoped>
.folder-row { min-height: 34px; display: flex; align-items: center; gap: 6px; border-radius: 5px; color: var(--text-secondary); cursor: pointer; font-size: 13px; }
.folder-row:hover { background: var(--border-light); color: var(--text); }
.folder-row.active { background: var(--primary-light); color: var(--primary); }
.folder-row.dragging { opacity: .5; }
.tree-toggle, .folder-actions button { display: grid; place-items: center; border: 0; background: transparent; color: inherit; cursor: pointer; }
.tree-toggle { width: 20px; height: 26px; }
.tree-toggle svg { transition: transform .15s; }.tree-toggle svg.rotated { transform: rotate(90deg); }
.tree-placeholder { width: 20px; }
.folder-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.folder-actions { display: none; align-items: center; padding-right: 4px; }
.folder-actions button { width: 24px; height: 26px; border-radius: 4px; }
.folder-actions button:hover { background: #dce7f8; }
.folder-row:hover .folder-actions { display: flex; }
@media (max-width: 768px) { .folder-actions { display: flex; } }
</style>
