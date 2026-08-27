import { FormEvent, useEffect, useState } from 'react'

type Todo = {
  id: string
  name: string
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

const statusLabels: Record<Todo['status'], string> = {
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
}

export default function App() {
  const [todos, setTodos] = useState<Todo[]>([])
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const loadTodos = async () => {
      try {
        const response = await fetch('/api/todos')
        if (!response.ok) throw new Error('The TODO list could not be loaded.')
        setTodos(await response.json())
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : 'Unexpected error')
      } finally {
        setLoading(false)
      }
    }

    void loadTodos()
  }, [])

  const createTodo = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmedName = name.trim()
    if (!trimmedName || saving) return

    setSaving(true)
    setError(null)
    try {
      const response = await fetch('/api/todos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: trimmedName }),
      })
      if (!response.ok) throw new Error('The TODO could not be created.')

      const created: Todo = await response.json()
      setTodos((current) => [created, ...current])
      setName('')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unexpected error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="app-shell">
      <header className="page-header">
        <p className="eyebrow">SleekFlow engineering assessment</p>
        <h1>Keep the next task clear.</h1>
        <p className="intro">A focused workspace for creating and tracking what needs attention.</p>
      </header>

      <section className="create-panel" aria-labelledby="create-heading">
        <div>
          <h2 id="create-heading">Add a TODO</h2>
          <p>Start with a short, actionable name.</p>
        </div>
        <form onSubmit={createTodo}>
          <label htmlFor="todo-name">Name</label>
          <div className="form-row">
            <input
              id="todo-name"
              maxLength={120}
              onChange={(event) => setName(event.target.value)}
              placeholder="Prepare the live demo"
              required
              value={name}
            />
            <button disabled={saving || !name.trim()} type="submit">
              {saving ? 'Adding…' : 'Add TODO'}
            </button>
          </div>
        </form>
      </section>

      {error && <p className="error-message" role="alert">{error}</p>}

      <section className="list-section" aria-labelledby="list-heading">
        <div className="list-heading">
          <h2 id="list-heading">TODOs</h2>
          <span>{todos.length} total</span>
        </div>

        {loading ? (
          <p className="list-state">Loading TODOs…</p>
        ) : todos.length === 0 ? (
          <p className="list-state">Nothing here yet. Add the first TODO above.</p>
        ) : (
          <ul className="todo-list">
            {todos.map((todo) => (
              <li key={todo.id}>
                <span className="status-mark" aria-hidden="true" />
                <span className="todo-name">{todo.name}</span>
                <span className="status-label">{statusLabels[todo.status]}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
