import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

const paginationKey = new PluginKey('docflowPagination')

function decorationFor(spec) {
  if (spec.type === 'manual') {
    return Decoration.node(spec.from, spec.to, {
      'data-pagination-manual-height': 'true',
      style: `height:${spec.height}px`
    })
  }
  if (spec.type === 'inline') {
    return Decoration.widget(spec.from, () => {
      const spacer = document.createElement('span')
      spacer.className = 'pagination-page-spacer pagination-inline-spacer'
      spacer.setAttribute('aria-hidden', 'true')
      spacer.setAttribute('contenteditable', 'false')
      spacer.style.height = `${spec.height}px`
      return spacer
    }, { side: -1, key: `inline-page-${spec.from}` })
  }
  if (spec.type === 'table-cell') {
    return Decoration.widget(spec.from, () => {
      const spacer = document.createElement('div')
      spacer.className = 'pagination-page-spacer pagination-table-cell-spacer'
      spacer.setAttribute('aria-hidden', 'true')
      spacer.setAttribute('contenteditable', 'false')
      spacer.style.height = `${spec.height}px`
      return spacer
    }, { side: -1, key: `table-cell-page-${spec.from}` })
  }
  return Decoration.widget(spec.from, () => {
    const spacer = document.createElement('div')
    spacer.className = 'pagination-page-spacer'
    spacer.setAttribute('aria-hidden', 'true')
    spacer.setAttribute('contenteditable', 'false')
    spacer.style.height = `${spec.height}px`
    return spacer
  }, { side: -1, key: `page-${spec.from}` })
}

function paragraphBreakPosition(element, documentFrom, documentTo, boundaryY) {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let textOffset = 0
  let lineTop = null
  let lineStartOffset = 0
  let textNode
  while ((textNode = walker.nextNode())) {
    const value = textNode.nodeValue || ''
    for (let index = 0; index < value.length; index += 1) {
      const range = document.createRange()
      range.setStart(textNode, index)
      range.setEnd(textNode, index + 1)
      const rect = range.getClientRects()[0]
      if (!rect) continue
      if (lineTop == null || Math.abs(rect.top - lineTop) > 1) {
        lineTop = rect.top
        lineStartOffset = textOffset + index
      }
      if (rect.bottom > boundaryY + 1) {
        return {
          position: Math.min(documentTo - 1, Math.max(documentFrom + 1, documentFrom + 1 + lineStartOffset)),
          lineTop
        }
      }
    }
    textOffset += value.length
  }
  return null
}

function measure(view, options) {
  const paper = view.dom.closest('.document-paper')
  if (!paper) return { pageCount: 1, specs: [] }
  if (options.isEnabled?.() === false) return { pageCount: null, specs: [] }

  const clone = paper.cloneNode(true)
  clone.classList.add('pagination-measurement')
  const paperStyles = getComputedStyle(paper)
  for (const property of ['--paper-width', '--paper-height', '--margin-top', '--margin-right', '--margin-bottom', '--margin-left', '--page-gap']) {
    clone.style.setProperty(property, paperStyles.getPropertyValue(property))
  }
  Object.assign(clone.style, {
    position: 'fixed',
    left: '-100000px',
    top: '0',
    visibility: 'hidden',
    pointerEvents: 'none',
    minHeight: '0',
    height: 'auto',
    transform: 'none'
  })
  const cloneRoot = clone.querySelector('.tiptap')
  if (!cloneRoot) return { pageCount: 1, specs: [] }
  cloneRoot.querySelectorAll('.pagination-page-spacer').forEach(node => node.remove())
  for (const child of cloneRoot.children) {
    child.style.setProperty('content-visibility', 'visible')
    child.style.setProperty('contain', 'none')
    if (child.hasAttribute('data-pagination-manual-height')) child.style.removeProperty('height')
    child.removeAttribute('data-pagination-manual-height')
  }

  ;(paper.parentElement || document.body).appendChild(clone)
  try {
    const settings = options.getPageSettings?.() || {}
    const letter = settings.pageFormat === 'LETTER'
    const paperWidthMm = letter ? 216 : 210
    const paperHeightMm = letter ? 279 : 297
    const paperRect = clone.getBoundingClientRect()
    const rootChildren = [...cloneRoot.children]
    const pxPerMm = paper.getBoundingClientRect().width / paperWidthMm
    const pageHeight = paperHeightMm * pxPerMm
    const topMargin = Number(settings.marginTop ?? 20) * pxPerMm
    const bottomMargin = Number(settings.marginBottom ?? 20) * pxPerMm
    const pageGap = Number.parseFloat(getComputedStyle(clone).getPropertyValue('--page-gap')) || 18
    const contentHeight = pageHeight - topMargin - bottomMargin
    const positions = []
    view.state.doc.forEach((node, offset) => positions.push({ from: offset, to: offset + node.nodeSize }))

    const specs = []
    let cumulativeOffset = 0
    let pageTop = 0
    let pageCount = 1
    const advancePage = () => {
      pageTop += pageHeight + pageGap
      pageCount += 1
    }

    for (let index = 0; index < Math.min(rootChildren.length, positions.length); index += 1) {
      const child = rootChildren[index]
      const rect = child.getBoundingClientRect()
      const naturalTop = rect.top - paperRect.top
      const height = rect.height
      let projectedTop = naturalTop + cumulativeOffset
      let projectedBottom = projectedTop + height
      let contentStart = pageTop + topMargin
      let contentEnd = pageTop + pageHeight - bottomMargin
      let nextContentStart = pageTop + pageHeight + pageGap + topMargin

      while (projectedTop >= nextContentStart - 1) {
        advancePage()
        contentStart = pageTop + topMargin
        contentEnd = pageTop + pageHeight - bottomMargin
        nextContentStart = pageTop + pageHeight + pageGap + topMargin
      }

      if (child.matches('[data-type="page-break"], .page-break')) {
        const desiredHeight = Math.max(topMargin + bottomMargin + pageGap, nextContentStart - projectedTop)
        specs.push({ type: 'manual', ...positions[index], height: desiredHeight })
        cumulativeOffset += desiredHeight - height
        advancePage()
        continue
      }

      const tableElement = child.matches('table') ? child : child.querySelector(':scope > table')
      const tableNode = view.state.doc.nodeAt(positions[index].from)
      if (tableElement && tableNode?.type.name === 'table') {
        const rows = [...tableElement.rows]
        const rowPositions = []
        const rowCellContentPositions = []
        let rowPosition = positions[index].from + 1
        tableNode.forEach(row => {
          rowPositions.push(rowPosition)
          const cellContentPositions = []
          let cellPosition = rowPosition + 1
          row.forEach(cell => {
            cellContentPositions.push(cellPosition + 1)
            cellPosition += cell.nodeSize
          })
          rowCellContentPositions.push(cellContentPositions)
          rowPosition += row.nodeSize
        })

        if (rows.length) {
          const firstRect = rows[0].getBoundingClientRect()
          const firstTop = firstRect.top - paperRect.top + cumulativeOffset
          const firstBottom = firstRect.bottom - paperRect.top + cumulativeOffset
          if (firstRect.height <= contentHeight && firstBottom > contentEnd + 1 && firstTop > contentStart + 1) {
            const offset = Math.max(0, nextContentStart - projectedTop)
            specs.push({ type: 'automatic', ...positions[index], height: offset })
            cumulativeOffset += offset
            projectedTop += offset
            projectedBottom += offset
            advancePage()
            contentStart = pageTop + topMargin
            contentEnd = pageTop + pageHeight - bottomMargin
            nextContentStart = pageTop + pageHeight + pageGap + topMargin
          }
        }

        for (let rowIndex = 0; rowIndex < Math.min(rows.length, rowPositions.length); rowIndex += 1) {
          const rowRect = rows[rowIndex].getBoundingClientRect()
          let rowTop = rowRect.top - paperRect.top + cumulativeOffset
          let rowBottom = rowRect.bottom - paperRect.top + cumulativeOffset

          while (rowTop >= nextContentStart - 1) {
            advancePage()
            contentStart = pageTop + topMargin
            contentEnd = pageTop + pageHeight - bottomMargin
            nextContentStart = pageTop + pageHeight + pageGap + topMargin
          }

          const rowFitsOnPage = rowRect.height <= contentHeight
          if (rowFitsOnPage && rowBottom > contentEnd + 1 && rowTop > contentStart + 1) {
            const offset = Math.max(0, nextContentStart - rowTop)
            for (const from of rowCellContentPositions[rowIndex] || []) {
              specs.push({ type: 'table-cell', from, height: offset })
            }
            cumulativeOffset += offset
            rowTop += offset
            rowBottom += offset
            projectedBottom += offset
            advancePage()
            contentStart = pageTop + topMargin
            contentEnd = pageTop + pageHeight - bottomMargin
            nextContentStart = pageTop + pageHeight + pageGap + topMargin
          }

          while (rowBottom > nextContentStart) {
            advancePage()
            contentStart = pageTop + topMargin
            contentEnd = pageTop + pageHeight - bottomMargin
            nextContentStart = pageTop + pageHeight + pageGap + topMargin
          }
          if (rowBottom > contentEnd) {
            pageCount = Math.max(pageCount, Math.floor(rowBottom / (pageHeight + pageGap)) + 1)
          }
        }
        continue
      }

      const fitsOnPage = height <= contentHeight
      const crossesBottomMargin = projectedBottom > contentEnd + 1
      const canMoveWholeBlock = projectedTop > contentStart + 1
      if (child.matches('p') && crossesBottomMargin) {
        const insertedAt = new Set()
        const lineHeight = Number.parseFloat(getComputedStyle(child).lineHeight) || 28
        const maximumInlineSpacer = topMargin + bottomMargin + pageGap + lineHeight * 2
        while (projectedBottom > contentEnd + 1) {
          const boundaryY = paperRect.top + contentEnd - cumulativeOffset
          const breakInfo = paragraphBreakPosition(child, positions[index].from, positions[index].to, boundaryY)
          if (breakInfo == null || insertedAt.has(breakInfo.position)) break
          const projectedLineTop = breakInfo.lineTop - paperRect.top + cumulativeOffset
          const inlineSpacerHeight = Math.max(pageGap, nextContentStart - projectedLineTop)
          const isNearPageBoundary = projectedLineTop >= contentEnd - lineHeight * 2
            && projectedLineTop <= contentEnd + lineHeight
          if (!isNearPageBoundary || inlineSpacerHeight > maximumInlineSpacer) break
          insertedAt.add(breakInfo.position)
          specs.push({ type: 'inline', from: breakInfo.position, height: inlineSpacerHeight })
          cumulativeOffset += inlineSpacerHeight
          projectedBottom += inlineSpacerHeight
          advancePage()
          contentEnd = pageTop + pageHeight - bottomMargin
          nextContentStart = pageTop + pageHeight + pageGap + topMargin
        }
        if (insertedAt.size) continue
      }
      if (fitsOnPage && crossesBottomMargin && canMoveWholeBlock) {
        const offset = Math.max(0, nextContentStart - projectedTop)
        const naturalMarginTop = Number.parseFloat(getComputedStyle(child).marginTop) || 0
        const previousMarginBottom = index > 0 ? (Number.parseFloat(getComputedStyle(rootChildren[index - 1]).marginBottom) || 0) : 0
        const collapsedMargin = Math.min(Math.max(0, naturalMarginTop), Math.max(0, previousMarginBottom))
        specs.push({ type: 'automatic', ...positions[index], height: Math.max(0, offset - collapsedMargin) })
        cumulativeOffset += offset
        advancePage()
        projectedTop += offset
        projectedBottom += offset
        contentEnd = pageTop + pageHeight - bottomMargin
        nextContentStart = pageTop + pageHeight + pageGap + topMargin
      }

      while (projectedBottom > nextContentStart) {
        advancePage()
        contentEnd = pageTop + pageHeight - bottomMargin
        nextContentStart = pageTop + pageHeight + pageGap + topMargin
      }
      if (projectedBottom > contentEnd) pageCount = Math.max(pageCount, Math.floor(projectedBottom / (pageHeight + pageGap)) + 1)
    }
    return { pageCount: Math.max(1, pageCount), specs }
  } finally {
    clone.remove()
  }
}

export const PaginationLayout = Extension.create({
  name: 'paginationLayout',
  addOptions() {
    return {
      isEnabled: () => true,
      getPageSettings: () => ({}),
      onPageCount: () => {}
    }
  },
  addProseMirrorPlugins() {
    const options = this.options
    return [new Plugin({
      key: paginationKey,
      state: {
        init: () => ({ decorations: DecorationSet.empty, signature: '' }),
        apply(transaction, previous) {
          const result = transaction.getMeta(paginationKey)
          if (result) {
            return {
              decorations: DecorationSet.create(transaction.doc, result.specs.map(decorationFor)),
              signature: result.signature
            }
          }
          return { ...previous, decorations: previous.decorations.map(transaction.mapping, transaction.doc) }
        }
      },
      props: {
        decorations(state) { return paginationKey.getState(state)?.decorations || DecorationSet.empty }
      },
      view(view) {
        let frame = 0
        const schedule = () => {
          cancelAnimationFrame(frame)
          frame = requestAnimationFrame(() => {
            const result = measure(view, options)
            if (result.pageCount != null) options.onPageCount?.(result.pageCount)
            const signature = JSON.stringify({ pageCount: result.pageCount, specs: result.specs })
            if (signature === paginationKey.getState(view.state)?.signature) return
            view.dispatch(view.state.tr.setMeta(paginationKey, { ...result, signature }).setMeta('addToHistory', false))
          })
        }
        const observer = new ResizeObserver(schedule)
        observer.observe(view.dom)
        const paper = view.dom.closest('.document-paper')
        if (paper) observer.observe(paper)
        window.addEventListener('resize', schedule)
        schedule()
        return {
          update(updatedView, previousState) {
            view = updatedView
            if (!previousState.doc.eq(updatedView.state.doc)) schedule()
          },
          destroy() {
            cancelAnimationFrame(frame)
            observer.disconnect()
            window.removeEventListener('resize', schedule)
          }
        }
      }
    })]
  }
})
