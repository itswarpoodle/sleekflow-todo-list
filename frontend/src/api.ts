import type { ApiError, Todo, TodoInput, TodoPage, TodoQuery } from './types'

/** Preserves the API's machine-readable code and field errors for the editor UI. */
export class TodoApiError extends Error {
  readonly code?: string
  readonly fieldErrors: Record<string, string>

  constructor(fallbackMessage: string, error?: ApiError) {
    super(error?.message || fallbackMessage)
    this.name = 'TodoApiError'
    this.code = error?.code
    this.fieldErrors = error?.fieldErrors || {}
  }
}

async function request<T>(path: string, init?: RequestInit, fallbackMessage = 'The request could not be completed.') {
  const response = await fetch(path, init)
  if (!response.ok) {
    let error: ApiError | undefined
    try {
      error = await response.json() as ApiError
    } catch {
      // Proxies and infrastructure can return non-JSON errors; keep a useful fallback.
      error = undefined
    }
    throw new TodoApiError(fallbackMessage, error)
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

export function listTodos(query: TodoQuery, signal?: AbortSignal) {
  // Send only active optional filters so the URL remains readable and reproducible.
  const parameters = new URLSearchParams({
    page: query.page.toString(),
    size: query.size.toString(),
    direction: query.direction,
  })
  if (query.status) parameters.set('status', query.status)
  if (query.priority) parameters.set('priority', query.priority)
  if (query.dueDate) parameters.set('dueDate', query.dueDate)
  if (query.blocked !== undefined) parameters.set('blocked', query.blocked.toString())
  if (query.name) parameters.set('name', query.name)
  if (query.sort) parameters.set('sort', query.sort)

  return request<TodoPage>(
    `/api/todos?${parameters.toString()}`,
    { signal },
    'The TODO list could not be loaded.',
  )
}

export function createTodo(input: TodoInput) {
  return request<Todo>('/api/todos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }, 'The TODO could not be created.')
}

export function getTodo(id: string) {
  return request<Todo>(`/api/todos/${id}`, undefined, 'The current TODO could not be loaded.')
}

export function updateTodo(id: string, version: number, input: TodoInput) {
  return request<Todo>(`/api/todos/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'If-Match': `"${version}"`,
    },
    body: JSON.stringify(input),
  }, 'The TODO could not be updated.')
}

export function deleteTodo(id: string, version: number) {
  return request<void>(`/api/todos/${id}`, {
    method: 'DELETE',
    headers: { 'If-Match': `"${version}"` },
  }, 'The TODO could not be deleted.')
}
