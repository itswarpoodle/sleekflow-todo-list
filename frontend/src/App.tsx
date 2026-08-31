import { useEffect, useState } from 'react'
import { createTodo, deleteTodo, getTodo, listTodos, TodoApiError, updateTodo } from './api'
import TodoForm from './TodoForm'
import TodoList from './TodoList'
import type { Todo, TodoInput, TodoPage, TodoPriority, TodoQuery, TodoStatus } from './types'

const initialQuery: TodoQuery = { page: 0, size: 10, direction: 'asc' }
const emptyPage: TodoPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'An unexpected error occurred.'
}

export default function App() {
  const [query, setQuery] = useState<TodoQuery>(initialQuery)
  const [page, setPage] = useState<TodoPage>(emptyPage)
  const [availableDependencies, setAvailableDependencies] = useState<Todo[]>([])
  const [dependencySearch, setDependencySearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingTodo, setEditingTodo] = useState<Todo | null>(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<TodoApiError | null>(null)
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  // SSE carries only invalidations. Refetching the current bounded query keeps
  // filters and pagination authoritative and avoids a second client-side cache.
  useEffect(() => {
    if (typeof EventSource === 'undefined') return
    const events = new EventSource('/api/todos/events')
    let refreshTimer: number | undefined
    const refresh = () => {
      window.clearTimeout(refreshTimer)
      refreshTimer = window.setTimeout(() => setRevision((current) => current + 1), 100)
    }
    events.addEventListener('todo-change', refresh)
    return () => {
      window.clearTimeout(refreshTimer)
      events.removeEventListener('todo-change', refresh)
      events.close()
    }
  }, [])

  // The server owns filtering, sorting, and pagination; the client keeps one bounded page.
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setListError(null)
    listTodos(query, controller.signal)
      .then(setPage)
      .catch((error) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setListError(errorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [query, revision])

  // Dependency choices use a separate bounded, server-backed search so list filters
  // do not hide valid prerequisites and large TODO collections remain usable.
  useEffect(() => {
    if (!editorOpen) return
    const controller = new AbortController()
    const timeout = window.setTimeout(() => {
      listTodos({
        page: 0,
        size: 20,
        name: dependencySearch.trim() || undefined,
        sort: 'name',
        direction: 'asc',
      }, controller.signal)
        .then((result) => setAvailableDependencies(result.content))
        .catch((error) => {
          if (error instanceof DOMException && error.name === 'AbortError') return
          setAvailableDependencies([])
        })
    }, 200)
    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [dependencySearch, editorOpen, revision])

  useEffect(() => {
    if (!editorOpen) return
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) closeEditor()
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [editorOpen, saving])

  const updateQuery = (change: Partial<TodoQuery>) => {
    // Any filter, sort, or page-size change returns to page one unless navigation
    // supplied an explicit page number.
    setQuery((current) => ({ ...current, ...change, page: change.page ?? 0 }))
  }

  const clearFilters = () => setQuery((current) => ({
    page: 0,
    size: current.size,
    direction: 'asc',
  }))

  const openCreate = () => {
    setEditingTodo(null)
    setDependencySearch('')
    setFormError(null)
    setEditorOpen(true)
  }

  const openEdit = (todo: Todo) => {
    setEditingTodo(todo)
    setDependencySearch('')
    setFormError(null)
    setEditorOpen(true)
  }

  const closeEditor = () => {
    setEditorOpen(false)
    setEditingTodo(null)
    setFormError(null)
  }

  const saveTodo = async (input: TodoInput) => {
    setSaving(true)
    setFormError(null)
    try {
      if (editingTodo) await updateTodo(editingTodo.id, editingTodo.version, input)
      else await createTodo(input)
      closeEditor()
      setRevision((current) => current + 1)
    } catch (error) {
      setFormError(error instanceof TodoApiError ? error : new TodoApiError(errorMessage(error)))
    } finally {
      setSaving(false)
    }
  }

  const reloadEditingTodo = async () => {
    if (!editingTodo) return
    setSaving(true)
    try {
      setEditingTodo(await getTodo(editingTodo.id))
      setFormError(null)
      setRevision((current) => current + 1)
    } catch (error) {
      setFormError(error instanceof TodoApiError ? error : new TodoApiError(errorMessage(error)))
    } finally {
      setSaving(false)
    }
  }

  const removeTodo = async (todo: Todo) => {
    setDeleting(true)
    setListError(null)
    try {
      await deleteTodo(todo.id, todo.version)
      setConfirmingDeleteId(null)
      // Avoid leaving the user on an empty trailing page after deleting its last item.
      if (page.content.length === 1 && query.page > 0) {
        setQuery((current) => ({ ...current, page: current.page - 1 }))
      } else {
        setRevision((current) => current + 1)
      }
    } catch (error) {
      setListError(errorMessage(error))
      if (error instanceof TodoApiError && error.code === 'TODO_VERSION_CONFLICT') {
        setConfirmingDeleteId(null)
        setRevision((current) => current + 1)
      }
    } finally {
      setDeleting(false)
    }
  }

  const filtersActive = Boolean(query.status || query.priority || query.dueDate || query.blocked !== undefined)

  return (
    <main className="app-shell">
      <header className="page-header">
        <div>
          <p className="eyebrow">SleekFlow TODO workspace</p>
          <h1>Work, clearly arranged.</h1>
          <p className="intro">Plan tasks, respect dependencies, and keep recurring work moving.</p>
        </div>
        <button className="primary-button header-action" onClick={openCreate} type="button">New TODO</button>
      </header>

      <section className="workspace" aria-label="TODO workspace">
        <aside className="filter-panel" aria-labelledby="filters-heading">
          <div className="panel-heading">
            <h2 id="filters-heading">Filters</h2>
            {filtersActive && <button className="text-button" onClick={clearFilters} type="button">Clear</button>}
          </div>

          <div className="filter-fields">
            <div className="field">
              <label htmlFor="filter-status">Status</label>
              <select
                id="filter-status"
                onChange={(event) => updateQuery({ status: event.target.value as TodoStatus || undefined })}
                value={query.status || ''}
              >
                <option value="">All statuses</option>
                <option value="NOT_STARTED">Not started</option>
                <option value="IN_PROGRESS">In progress</option>
                <option value="COMPLETED">Completed</option>
                <option value="ARCHIVED">Archived</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="filter-priority">Priority</label>
              <select
                id="filter-priority"
                onChange={(event) => updateQuery({ priority: event.target.value as TodoPriority || undefined })}
                value={query.priority || ''}
              >
                <option value="">All priorities</option>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="filter-due-date">Due date</label>
              <input
                id="filter-due-date"
                onChange={(event) => updateQuery({ dueDate: event.target.value || undefined })}
                type="date"
                value={query.dueDate || ''}
              />
            </div>
            <div className="field">
              <label htmlFor="filter-blocked">Dependency state</label>
              <select
                id="filter-blocked"
                onChange={(event) => updateQuery({ blocked: event.target.value === '' ? undefined : event.target.value === 'true' })}
                value={query.blocked === undefined ? '' : query.blocked.toString()}
              >
                <option value="">All TODOs</option>
                <option value="true">Blocked</option>
                <option value="false">Unblocked</option>
              </select>
            </div>
          </div>

          <div className="sort-section">
            <h3>Sort</h3>
            <div className="field">
              <label htmlFor="sort-field">Field</label>
              <select
                id="sort-field"
                onChange={(event) => updateQuery({ sort: event.target.value as TodoQuery['sort'] || undefined })}
                value={query.sort || ''}
              >
                <option value="">Recently created</option>
                <option value="dueDate">Due date</option>
                <option value="priority">Priority</option>
                <option value="status">Status</option>
                <option value="name">Name</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="sort-direction">Direction</label>
              <select
                disabled={!query.sort}
                id="sort-direction"
                onChange={(event) => updateQuery({ direction: event.target.value as TodoQuery['direction'] })}
                value={query.direction}
              >
                <option value="asc">Ascending</option>
                <option value="desc">Descending</option>
              </select>
            </div>
          </div>
        </aside>

        <section className="list-section" aria-labelledby="list-heading">
          <div className="list-heading">
            <div>
              <h2 id="list-heading">TODOs</h2>
              <p aria-live="polite">{page.totalElements} active {page.totalElements === 1 ? 'item' : 'items'}</p>
            </div>
            <label className="page-size-control" htmlFor="page-size">
              Per page
              <select
                id="page-size"
                onChange={(event) => updateQuery({ size: Number(event.target.value) })}
                value={query.size}
              >
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
            </label>
          </div>

          {listError && (
            <div className="list-error" role="alert">
              <p>{listError}</p>
              <button className="secondary-button" onClick={() => setRevision((current) => current + 1)} type="button">Try again</button>
            </div>
          )}

          {loading ? (
            <div className="skeleton-list" aria-label="Loading TODOs">
              {[0, 1, 2].map((item) => <div className="skeleton-card" key={item} />)}
            </div>
          ) : page.content.length === 0 ? (
            <div className="empty-state">
              <h3>{filtersActive ? 'No TODOs match these filters.' : 'The list is ready for its first TODO.'}</h3>
              <p>{filtersActive ? 'Clear a filter or adjust the selected values.' : 'Add a task with only a name, then refine it when needed.'}</p>
              {filtersActive
                ? <button className="secondary-button" onClick={clearFilters} type="button">Clear filters</button>
                : <button className="primary-button" onClick={openCreate} type="button">Create first TODO</button>}
            </div>
          ) : (
            <TodoList
              confirmingDeleteId={confirmingDeleteId}
              deleting={deleting}
              onDelete={removeTodo}
              onEdit={openEdit}
              onRequestDelete={setConfirmingDeleteId}
              todos={page.content}
            />
          )}

          {page.totalPages > 1 && (
            <nav className="pagination" aria-label="TODO pages">
              <button
                className="secondary-button"
                disabled={query.page === 0}
                onClick={() => updateQuery({ page: query.page - 1 })}
                type="button"
              >Previous</button>
              <span>Page {query.page + 1} of {page.totalPages}</span>
              <button
                className="secondary-button"
                disabled={query.page + 1 >= page.totalPages}
                onClick={() => updateQuery({ page: query.page + 1 })}
                type="button"
              >Next</button>
            </nav>
          )}
        </section>
      </section>

      {editorOpen && (
        <div className="editor-backdrop" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !saving) closeEditor()
        }}>
          <section aria-labelledby="editor-title" aria-modal="true" className="editor-panel" role="dialog">
            <TodoForm
              availableDependencies={availableDependencies}
              error={formError}
              key={editingTodo ? `${editingTodo.id}-${editingTodo.version}` : 'new'}
              onCancel={closeEditor}
              onDependencySearch={setDependencySearch}
              onReloadConflict={reloadEditingTodo}
              onSubmit={saveTodo}
              saving={saving}
              todo={editingTodo}
            />
          </section>
        </div>
      )}
    </main>
  )
}
