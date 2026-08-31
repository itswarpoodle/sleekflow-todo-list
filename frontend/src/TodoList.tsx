import type { CSSProperties } from 'react'
import type { Todo } from './types'
import { priorityLabels, statusLabels } from './types'

type TodoListProps = {
  todos: Todo[]
  confirmingDeleteId: string | null
  deleting: boolean
  onEdit: (todo: Todo) => void
  onRequestDelete: (id: string | null) => void
  onDelete: (todo: Todo) => Promise<void>
}

function formatDueDate(dueDate: string | null) {
  if (!dueDate) return 'No due date'
  // Interpret a date-only value at local midnight so UTC conversion cannot move it
  // to the previous calendar day for users west of Greenwich.
  return new Date(`${dueDate}T00:00:00`).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

function recurrenceLabel(todo: Todo) {
  if (!todo.recurrence) return null
  if (todo.recurrence.frequency !== 'CUSTOM') {
    return todo.recurrence.frequency.charAt(0) + todo.recurrence.frequency.slice(1).toLowerCase()
  }
  return `Every ${todo.recurrence.interval} ${todo.recurrence.unit.toLowerCase()}`
}

export default function TodoList({
  todos,
  confirmingDeleteId,
  deleting,
  onEdit,
  onRequestDelete,
  onDelete,
}: TodoListProps) {
  return (
    <div className="todo-list">
      {todos.map((todo, index) => (
        <article
          className={`todo-card ${todo.blocked ? 'blocked-card' : ''}`}
          key={todo.id}
          style={{ '--index': index } as CSSProperties}
        >
          <div className="todo-card-main">
            <div className="todo-card-heading">
              <div>
                <div className="badges">
                  <span className={`status-badge status-${todo.status.toLowerCase().replace('_', '-')}`}>{statusLabels[todo.status]}</span>
                  <span className={`priority-badge priority-${todo.priority.toLowerCase()}`}>{priorityLabels[todo.priority]}</span>
                  {todo.blocked && <span className="blocked-badge">Blocked</span>}
                </div>
                <h3>{todo.name}</h3>
              </div>
              <div className="card-actions">
                <button className="text-button" onClick={() => onEdit(todo)} type="button">Edit</button>
                <button className="danger-text-button" onClick={() => onRequestDelete(todo.id)} type="button">Delete</button>
              </div>
            </div>

            {todo.description && <p className="todo-description">{todo.description}</p>}

            <dl className="todo-meta">
              <div><dt>Due</dt><dd>{formatDueDate(todo.dueDate)}</dd></div>
              <div><dt>Dependencies</dt><dd>{todo.dependencyIds.length || 'None'}</dd></div>
              {recurrenceLabel(todo) && <div><dt>Repeats</dt><dd>{recurrenceLabel(todo)}</dd></div>}
            </dl>

            {todo.blocked && (
              <p className="blocked-note">Complete all prerequisites before starting or completing this TODO.</p>
            )}
          </div>

          {confirmingDeleteId === todo.id && (
            <div className="delete-confirmation" role="alertdialog" aria-label={`Delete ${todo.name}`}>
              <p><strong>Delete this TODO?</strong> It will disappear from active views but remain stored.</p>
              <div>
                <button className="secondary-button" onClick={() => onRequestDelete(null)} type="button">Keep TODO</button>
                <button className="danger-button" disabled={deleting} onClick={() => void onDelete(todo)} type="button">
                  {deleting ? 'Deleting...' : 'Confirm delete'}
                </button>
              </div>
            </div>
          )}
        </article>
      ))}
    </div>
  )
}
