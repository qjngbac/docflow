import test from 'node:test'
import assert from 'node:assert/strict'
import * as Y from 'yjs'
import { createInitialDocument, documentToHtml, replaceDocumentContent } from '../src/initial-document.js'
import { encodeCheckpoint, restoreDocument } from '../src/crdt-state.js'

test('same-position Chinese edits in the editor default fragment survive reverse delivery, checkpoint and reconnect', () => {
  const seed = createInitialDocument({ initialContent: '<p>开始结束</p>', initialContentFormat: 'HTML' })
  const first = restoreDocument(encodeCheckpoint(seed))
  const second = restoreDocument(encodeCheckpoint(seed))
  let updateA, updateB
  first.once('update', update => { updateA = update })
  second.once('update', update => { updateB = update })
  first.getXmlFragment('default').get(0).get(0).insert(2, '甲')
  second.getXmlFragment('default').get(0).get(0).insert(2, '乙')
  const server = restoreDocument(encodeCheckpoint(seed), [updateB, updateA, updateA])
  Y.applyUpdate(first, Y.encodeStateAsUpdate(server, Y.encodeStateVector(first)))
  Y.applyUpdate(second, Y.encodeStateAsUpdate(server, Y.encodeStateVector(second)))
  assert.match(documentToHtml(server), /^<p>开始(?:甲乙|乙甲)结束<\/p>$/)
  assert.equal(documentToHtml(first), documentToHtml(server))
  assert.equal(documentToHtml(second), documentToHtml(server))

  const checkpoint = encodeCheckpoint(server)
  let pending
  server.once('update', update => { pending = update })
  server.getXmlFragment('default').get(0).get(0).insert(6, '！')
  first.getXmlFragment('default').get(0).get(0).insert(0, '离线')
  const restarted = restoreDocument(checkpoint, [pending])
  Y.applyUpdate(restarted, Y.encodeStateAsUpdate(first, Y.encodeStateVector(restarted)))
  Y.applyUpdate(first, Y.encodeStateAsUpdate(restarted, Y.encodeStateVector(first)))
  assert.match(documentToHtml(restarted), /^<p>离线开始(?:甲乙|乙甲)结束！<\/p>$/)
  assert.equal(documentToHtml(first), documentToHtml(restarted))
  for (const document of [seed, first, second, server, restarted]) document.destroy()
})

test('resized table row height survives initialization and materialization', () => {
  const document = createInitialDocument({ initialContent: '<table><tbody><tr data-row-height="84"><td><p>cell</p></td></tr></tbody></table>' })
  assert.match(documentToHtml(document), /data-row-height="84"/)
  document.destroy()
})

test('math and comment metadata survive CRDT materialization', () => {
  const document = createInitialDocument({
    initialContentFormat: 'HTML',
    initialContent: '<p><span data-type="inline-math" data-latex="E=mc^2"></span> <span data-comment-id="42">review</span></p><div data-type="block-math" data-latex="x^2+y^2=z^2"></div>'
  })
  const html = documentToHtml(document)
  assert.match(html, /data-type="inline-math"/)
  assert.match(html, /data-latex="E=mc\^2"/)
  assert.match(html, /data-comment-id="42"/)
  assert.match(html, /data-type="block-math"/)
})

test('page breaks, review marks and paragraph attribution survive CRDT materialization', () => {
  const document = createInitialDocument({
    initialContentFormat: 'HTML',
    initialContent: '<p data-last-edit-by="7" data-last-edit-name="Alice"><span data-change-id="c1" data-change-type="insert" data-author-id="7" data-author-name="Alice">new text</span></p><div data-type="page-break"></div>'
  })
  const html = documentToHtml(document)
  assert.match(html, /data-last-edit-by="7"/)
  assert.match(html, /data-change-id="c1"/)
  assert.match(html, /data-change-type="insert"/)
  assert.match(html, /data-type="page-break"/)
})

test('advanced formatting, numbered formulas, references and notes survive materialization', () => {
  const document = createInitialDocument({
    initialContentFormat: 'HTML',
    initialContent: '<h2 data-collapsed="true" data-text-align="center">Section</h2><p data-line-height="1.5" data-indent="2"><span data-docflow-style="true" data-font-family="serif" data-font-size="18" data-text-color="blue">styled</span> <sup data-type="footnote" data-footnote-id="1" data-footnote-text="note">[1]</sup> <span data-type="citation" data-citation-key="RFC" data-citation-title="Reference">[RFC]</span></p><div data-type="block-math" data-latex="\\ce{H2O}" data-equation-label="eq-water" data-equation-number="1" data-formula-kind="chem"></div><p><span data-type="equation-reference" data-equation-label="eq-water" data-equation-number="1">式（1）</span></p>'
  })
  const html = documentToHtml(document)
  assert.match(html, /data-collapsed="true"/)
  assert.match(html, /data-font-family="serif"/)
  assert.match(html, /data-equation-label="eq-water"/)
  assert.match(html, /data-formula-kind="chem"/)
  assert.match(html, /data-footnote-text="note"/)
  assert.match(html, /data-citation-key="RFC"/)
})

test('version replacement keeps an old client convergent through a Yjs deletion update', () => {
  const current = createInitialDocument({ initialContent: '<p>Current</p>', initialContentFormat: 'HTML' })
  const oldClient = new Y.Doc()
  Y.applyUpdate(oldClient, Y.encodeStateAsUpdate(current))
  const replacement = createInitialDocument({ initialContent: '<p>Earlier</p>', initialContentFormat: 'HTML' })

  replaceDocumentContent(current, replacement)
  Y.applyUpdate(oldClient, Y.encodeStateAsUpdate(current, Y.encodeStateVector(oldClient)))

  assert.match(documentToHtml(current), /Earlier/)
  assert.equal(documentToHtml(oldClient), documentToHtml(current))
  current.destroy()
  oldClient.destroy()
  replacement.destroy()
})
