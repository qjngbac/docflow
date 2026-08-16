function openDialog({
  title = '请确认',
  message = '',
  confirmText = '确定',
  cancelText = '取消',
  danger = false,
  input = false,
  multiline = false,
  defaultValue = '',
  placeholder = ''
} = {}) {
  return new Promise(resolve => {
    const backdrop = document.createElement('div')
    backdrop.className = 'modal-backdrop app-dialog-backdrop'

    const dialog = document.createElement('section')
    dialog.className = 'app-dialog'
    dialog.setAttribute('role', 'dialog')
    dialog.setAttribute('aria-modal', 'true')

    const heading = document.createElement('h2')
    heading.textContent = title
    dialog.appendChild(heading)

    if (message) {
      const description = document.createElement('p')
      description.textContent = message
      dialog.appendChild(description)
    }

    let field = null
    if (input) {
      field = document.createElement(multiline ? 'textarea' : 'input')
      field.className = 'input app-dialog-input'
      field.value = defaultValue
      field.placeholder = placeholder
      if (multiline) field.rows = 4
      dialog.appendChild(field)
    }

    const footer = document.createElement('footer')
    const cancel = document.createElement('button')
    cancel.type = 'button'
    cancel.className = 'btn btn-secondary'
    cancel.textContent = cancelText
    const confirm = document.createElement('button')
    confirm.type = 'button'
    confirm.className = `btn ${danger ? 'btn-danger' : 'btn-primary'}`
    confirm.textContent = confirmText
    if (cancelText) footer.appendChild(cancel)
    footer.appendChild(confirm)
    dialog.appendChild(footer)
    backdrop.appendChild(dialog)
    document.body.appendChild(backdrop)

    let settled = false
    const finish = value => {
      if (settled) return
      settled = true
      document.removeEventListener('keydown', onKeyDown)
      backdrop.remove()
      resolve(value)
    }
    const onKeyDown = event => {
      if (event.key === 'Escape') finish(input ? null : false)
      if (event.key === 'Enter' && (!multiline || event.ctrlKey)) finish(input ? field.value : true)
    }
    cancel.addEventListener('click', () => finish(input ? null : false))
    confirm.addEventListener('click', () => finish(input ? field.value : true))
    backdrop.addEventListener('click', event => {
      if (event.target === backdrop) finish(input ? null : false)
    })
    document.addEventListener('keydown', onKeyDown)
    requestAnimationFrame(() => (field || confirm).focus())
  })
}

export function confirmDialog(message, options = {}) {
  return openDialog({ message, ...options })
}

export function promptDialog(title, defaultValue = '', options = {}) {
  return openDialog({ title, input: true, defaultValue, ...options })
}

export function alertDialog(message, options = {}) {
  return openDialog({ title: options.title || '提示', message, confirmText: options.confirmText || '知道了', cancelText: '', ...options }).then(() => true)
}
