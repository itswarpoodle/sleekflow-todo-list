import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

type Todo = {
  id: string
  name: string
  version: number
}

function localDateOffset(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const localTime = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return localTime.toISOString().slice(0, 10)
}

async function createTodo(request: APIRequestContext, name: string) {
  const response = await request.post('/api/todos', { data: { name } })
  expect(response.ok()).toBeTruthy()
  return await response.json() as Todo
}

async function cleanup(request: APIRequestContext, prefix: string) {
  const response = await request.get('/api/todos', {
    params: { name: prefix, page: '0', size: '100', direction: 'asc' },
  })
  if (!response.ok()) return
  const page = await response.json() as { content: Todo[] }
  await Promise.all(page.content.map((todo) => request.delete(`/api/todos/${todo.id}`, {
    headers: { 'If-Match': `"${todo.version}"` },
  })))
}

function card(page: Page, name: string) {
  return page.locator('article').filter({ has: page.getByRole('heading', { name, exact: true }) })
}

async function openEditor(page: Page, name: string) {
  await card(page, name).getByRole('button', { name: 'Edit' }).click()
  return page.getByRole('dialog')
}

test('runs the cumulative core workflow in the browser', async ({ page, request }) => {
  const prefix = `E2E core ${Date.now()}`
  const prerequisite = `${prefix} prerequisite`
  const dependent = `${prefix} dependent`
  const recurring = `${prefix} weekly`

  try {
    await page.goto('/')

    await page.getByRole('button', { name: 'New TODO' }).click()
    let editor = page.getByRole('dialog')
    await editor.getByLabel('Name', { exact: true }).fill(prerequisite)
    await editor.getByLabel('Priority', { exact: true }).selectOption('HIGH')
    await editor.getByRole('button', { name: 'Create TODO' }).click()
    await expect(page.getByRole('heading', { name: prerequisite })).toBeVisible()

    await page.getByRole('button', { name: 'New TODO' }).click()
    editor = page.getByRole('dialog')
    await editor.getByLabel('Name', { exact: true }).fill(dependent)
    await editor.getByRole('searchbox', { name: 'Search dependencies' }).fill(prerequisite)
    await editor.getByRole('checkbox', { name: new RegExp(prerequisite) }).check()
    await editor.getByRole('button', { name: 'Create TODO' }).click()
    await expect(card(page, dependent).getByText('Blocked', { exact: true })).toBeVisible()

    editor = await openEditor(page, dependent)
    const blockedStatus = editor.getByLabel('Status', { exact: true })
    await expect(blockedStatus.getByRole('option', { name: 'In progress' })).toBeDisabled()
    await expect(blockedStatus.getByRole('option', { name: 'Completed' })).toBeDisabled()
    await expect(blockedStatus.getByRole('option', { name: 'Archived' })).toBeEnabled()
    await expect(editor.getByText(/before starting or completing this TODO/i)).toBeVisible()
    await editor.getByRole('button', { name: 'Cancel' }).click()

    editor = await openEditor(page, prerequisite)
    await editor.getByLabel('Status', { exact: true }).selectOption('COMPLETED')
    await editor.getByRole('button', { name: 'Save changes' }).click()

    editor = await openEditor(page, dependent)
    await expect(editor.getByLabel('Status', { exact: true }).getByRole('option', { name: 'Completed' })).toBeEnabled()
    await editor.getByLabel('Status', { exact: true }).selectOption('IN_PROGRESS')
    await editor.getByRole('button', { name: 'Save changes' }).click()
    await expect(card(page, dependent).getByText('In progress', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'New TODO' }).click()
    editor = page.getByRole('dialog')
    await editor.getByLabel('Name', { exact: true }).fill(recurring)
    await editor.getByLabel('Due date', { exact: true }).fill(localDateOffset(31))
    await editor.getByLabel('Recurrence', { exact: true }).selectOption('WEEKLY')
    await editor.getByRole('button', { name: 'Create TODO' }).click()
    editor = await openEditor(page, recurring)
    await editor.getByLabel('Status', { exact: true }).selectOption('COMPLETED')
    await editor.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('heading', { name: recurring })).toHaveCount(2)

    await page.getByRole('complementary', { name: 'Filters' })
      .getByLabel('Status', { exact: true })
      .selectOption('COMPLETED')
    await expect(page.getByRole('heading', { name: prerequisite })).toBeVisible()
    await page.getByRole('button', { name: 'Clear' }).click()

    await card(page, dependent).getByRole('button', { name: 'Delete' }).click()
    await page.getByRole('button', { name: 'Confirm delete' }).click()
    await expect(page.getByRole('heading', { name: dependent })).toHaveCount(0)
  } finally {
    await cleanup(request, prefix)
  }
})

test('prevents assigning a past due date', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'New TODO' }).click()
  const editor = page.getByRole('dialog')
  const dueDate = editor.getByLabel('Due date', { exact: true })

  await expect(dueDate).toHaveAttribute('min', localDateOffset(0))
  await editor.getByLabel('Name', { exact: true }).fill('E2E invalid past date')
  await dueDate.fill(localDateOffset(-1))
  await editor.getByRole('button', { name: 'Create TODO' }).click()

  await expect(editor.getByText('Due date must be today or later.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'E2E invalid past date' })).toHaveCount(0)
})

test('synchronizes committed changes across two browser tabs', async ({ page, context, request }) => {
  const prefix = `E2E realtime ${Date.now()}`
  const originalName = `${prefix} original`
  const changedName = `${prefix} synchronized`

  try {
    await createTodo(request, originalName)
    const secondPage = await context.newPage()
    await Promise.all([page.goto('/'), secondPage.goto('/')])
    await expect(page.getByRole('heading', { name: originalName })).toBeVisible()
    await expect(secondPage.getByRole('heading', { name: originalName })).toBeVisible()

    const editor = await openEditor(page, originalName)
    await editor.getByLabel('Name', { exact: true }).fill(changedName)
    await editor.getByRole('button', { name: 'Save changes' }).click()

    await expect(secondPage.getByRole('heading', { name: changedName })).toBeVisible()
  } finally {
    await cleanup(request, prefix)
  }
})

test('protects a stale editor from overwriting a newer version', async ({ page, context, request }) => {
  const prefix = `E2E conflict ${Date.now()}`
  const originalName = `${prefix} original`
  const currentName = `${prefix} current`

  try {
    await createTodo(request, originalName)
    const stalePage = await context.newPage()
    await Promise.all([page.goto('/'), stalePage.goto('/')])
    const currentEditor = await openEditor(page, originalName)
    const staleEditor = await openEditor(stalePage, originalName)

    await currentEditor.getByLabel('Name', { exact: true }).fill(currentName)
    await currentEditor.getByRole('button', { name: 'Save changes' }).click()
    await staleEditor.getByLabel('Name', { exact: true }).fill(`${prefix} stale overwrite`)
    await staleEditor.getByRole('button', { name: 'Save changes' }).click()

    await expect(staleEditor.getByText(/changed after it was loaded/i)).toBeVisible()
    await staleEditor.getByRole('button', { name: 'Reload current TODO' }).click()
    await expect(stalePage.getByRole('dialog').getByLabel('Name', { exact: true })).toHaveValue(currentName)
  } finally {
    await cleanup(request, prefix)
  }
})
