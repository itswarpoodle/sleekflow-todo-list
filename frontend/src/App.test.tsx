import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App'
import type { Todo, TodoPage } from './types'

const prerequisite: Todo = {
  id: 'todo-1',
  name: 'Review the project brief',
  description: 'Confirm every core requirement.',
  dueDate: '2026-09-10',
  status: 'COMPLETED',
  priority: 'HIGH',
  version: 0,
  dependencyIds: [],
  blocked: false,
  recurrence: null,
  previousOccurrenceId: null,
  createdAt: '2026-08-27T00:00:00Z',
  updatedAt: '2026-08-27T00:00:00Z',
}

function todoPage(content: Todo[]): TodoPage {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function localDateOffset(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const localTime = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return localTime.toISOString().slice(0, 10)
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

class FakeEventSource {
  static current: FakeEventSource | null = null
  private readonly listeners = new Map<string, Set<EventListener>>()

  constructor(_url: string) {
    FakeEventSource.current = this
  }

  addEventListener(type: string, listener: EventListener) {
    const listeners = this.listeners.get(type) || new Set<EventListener>()
    listeners.add(listener)
    this.listeners.set(type, listeners)
  }

  removeEventListener(type: string, listener: EventListener) {
    this.listeners.get(type)?.delete(listener)
  }

  close() {}

  emit(type: string) {
    this.listeners.get(type)?.forEach((listener) => listener(new Event(type)))
  }
}

test('loads the complete TODO summary', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(todoPage([prerequisite])))

  render(<App />)

  expect(await screen.findByText('Review the project brief')).toBeInTheDocument()
  expect(screen.getByText('Confirm every core requirement.')).toBeInTheDocument()
  expect(screen.getByText('1 active item')).toBeInTheDocument()
  const card = screen.getByText('Review the project brief').closest('article')
  expect(within(card as HTMLElement).getByText('Completed')).toBeInTheDocument()
  expect(within(card as HTMLElement).getByText('High')).toBeInTheDocument()
})

test('refetches the current bounded page after a real-time invalidation', async () => {
  vi.stubGlobal('EventSource', FakeEventSource)
  let changed = false
  vi.spyOn(globalThis, 'fetch').mockImplementation(async () => jsonResponse(todoPage([
    changed ? { ...prerequisite, name: 'Review the synchronized implementation', version: 1 } : prerequisite,
  ])))

  render(<App />)
  await screen.findByText('Review the project brief')
  changed = true
  FakeEventSource.current?.emit('todo-change')

  expect(await screen.findByText('Review the synchronized implementation')).toBeInTheDocument()
})

test('creates a recurring TODO with a dependency', async () => {
  let created = false
  const recurringTodo: Todo = {
    ...prerequisite,
    id: 'todo-2',
    name: 'Prepare the live demo',
    description: 'Walk through the core workflow.',
    dueDate: '2026-09-20',
    status: 'NOT_STARTED',
    priority: 'MEDIUM',
    dependencyIds: [prerequisite.id],
    recurrence: { frequency: 'WEEKLY', interval: 1, unit: 'WEEKS' },
  }
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
    if (init?.method === 'POST') {
      created = true
      return jsonResponse(recurringTodo, 201)
    }
    return jsonResponse(todoPage(created ? [recurringTodo, prerequisite] : [prerequisite]))
  })

  render(<App />)
  await screen.findByText('Review the project brief')
  fireEvent.click(screen.getByRole('button', { name: 'New TODO' }))

  const editor = screen.getByRole('dialog')
  fireEvent.change(within(editor).getByLabelText('Name'), { target: { value: 'Prepare the live demo' } })
  fireEvent.change(within(editor).getByLabelText('Description'), { target: { value: 'Walk through the core workflow.' } })
  fireEvent.change(within(editor).getByLabelText('Due date'), { target: { value: '2026-09-20' } })
  fireEvent.change(within(editor).getByLabelText('Recurrence'), { target: { value: 'WEEKLY' } })
  fireEvent.click(await within(editor).findByRole('checkbox', { name: /Review the project brief/ }))
  fireEvent.click(within(editor).getByRole('button', { name: 'Create TODO' }))

  expect(await screen.findByText('Prepare the live demo')).toBeInTheDocument()
  const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
  expect(postCall).toBeDefined()
  expect(JSON.parse(postCall?.[1]?.body as string)).toEqual(expect.objectContaining({
    name: 'Prepare the live demo',
    dueDate: '2026-09-20',
    dependencyIds: ['todo-1'],
    recurrence: { frequency: 'WEEKLY' },
  }))
})

test('prevents assigning a past due date in the editor', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(todoPage([])))

  render(<App />)
  await screen.findByText('The list is ready for its first TODO.')
  fireEvent.click(screen.getByRole('button', { name: 'New TODO' }))

  const editor = screen.getByRole('dialog')
  const dueDate = within(editor).getByLabelText('Due date')
  expect(dueDate).toHaveAttribute('min', localDateOffset(0))
  fireEvent.change(within(editor).getByLabelText('Name'), { target: { value: 'Past TODO' } })
  fireEvent.change(dueDate, { target: { value: localDateOffset(-1) } })
  fireEvent.click(within(editor).getByRole('button', { name: 'Create TODO' }))

  expect(await within(editor).findByText('Due date must be today or later.')).toBeInTheDocument()
  expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
})

test('sends filter and sort choices to the paged API', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(todoPage([prerequisite])))
  render(<App />)
  await screen.findByText('Review the project brief')

  fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'COMPLETED' } })
  fireEvent.change(screen.getByLabelText('Due date'), { target: { value: '2026-09-10' } })
  fireEvent.change(screen.getByLabelText('Dependency state'), { target: { value: 'false' } })
  fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'priority' } })
  fireEvent.change(screen.getByLabelText('Direction'), { target: { value: 'desc' } })

  await waitFor(() => {
    const urls = fetchMock.mock.calls.map(([input]) => input.toString())
    expect(urls.some((url) => url.includes('status=COMPLETED'))).toBe(true)
    expect(urls.some((url) => url.includes('dueDate=2026-09-10'))).toBe(true)
    expect(urls.some((url) => url.includes('blocked=false'))).toBe(true)
    expect(urls.some((url) => url.includes('sort=priority') && url.includes('direction=desc'))).toBe(true)
  })
})

test('searches dependency candidates through the bounded API', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(todoPage([prerequisite])))
  render(<App />)
  await screen.findByText('Review the project brief')

  fireEvent.click(screen.getByRole('button', { name: 'New TODO' }))
  fireEvent.change(screen.getByRole('searchbox', { name: 'Search dependencies' }), {
    target: { value: 'release' },
  })

  await waitFor(() => {
    const urls = fetchMock.mock.calls.map(([input]) => input.toString())
    expect(urls.some((url) => url.includes('name=release') && url.includes('size=20'))).toBe(true)
  })
})

test('edits and soft-deletes a TODO through explicit confirmation', async () => {
  let currentTodo: Todo | null = prerequisite
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
    if (init?.method === 'PUT') {
      currentTodo = { ...prerequisite, name: 'Review the final implementation', version: 1 }
      return jsonResponse(currentTodo)
    }
    if (init?.method === 'DELETE') {
      currentTodo = null
      return new Response(null, { status: 204 })
    }
    return jsonResponse(todoPage(currentTodo ? [currentTodo] : []))
  })

  render(<App />)
  await screen.findByText('Review the project brief')
  fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
  const editor = screen.getByRole('dialog')
  fireEvent.change(within(editor).getByLabelText('Name'), { target: { value: 'Review the final implementation' } })
  fireEvent.click(within(editor).getByRole('button', { name: 'Save changes' }))

  expect(await screen.findByText('Review the final implementation')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
  fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }))

  expect(await screen.findByText('The list is ready for its first TODO.')).toBeInTheDocument()
  expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(true)
  expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
  const putCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
  const deleteCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'DELETE')
  expect(new Headers(putCall?.[1]?.headers).get('If-Match')).toBe('"0"')
  expect(new Headers(deleteCall?.[1]?.headers).get('If-Match')).toBe('"1"')
})

test('reloads the current TODO after a stale update conflict', async () => {
  const currentTodo = {
    ...prerequisite,
    name: 'Review the current implementation',
    version: 1,
  }
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    if (init?.method === 'PUT') {
      return jsonResponse({
        code: 'TODO_VERSION_CONFLICT',
        message: 'The TODO changed after it was loaded; reload the current version and try again',
        fieldErrors: {},
      }, 412)
    }
    if (input.toString() === '/api/todos/todo-1') return jsonResponse(currentTodo)
    return jsonResponse(todoPage([prerequisite]))
  })

  render(<App />)
  await screen.findByText('Review the project brief')
  fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
  const editor = screen.getByRole('dialog')
  fireEvent.change(within(editor).getByLabelText('Name'), { target: { value: 'My stale edit' } })
  fireEvent.click(within(editor).getByRole('button', { name: 'Save changes' }))

  expect(await within(editor).findByText(/changed after it was loaded/)).toBeInTheDocument()
  fireEvent.click(within(editor).getByRole('button', { name: 'Reload current TODO' }))

  await waitFor(() => {
    expect(screen.getByRole('dialog')).toHaveAccessibleName('Review the current implementation')
    expect(within(screen.getByRole('dialog')).getByLabelText('Name')).toHaveValue('Review the current implementation')
  })
  const putCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
  expect(new Headers(putCall?.[1]?.headers).get('If-Match')).toBe('"0"')
})
