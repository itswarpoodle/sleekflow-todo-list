import { FormEvent, useState } from 'react'
import { TodoApiError } from './api'
import type {
  RecurrenceFrequency,
  RecurrenceUnit,
  Todo,
  TodoInput,
  TodoPriority,
  TodoStatus,
} from './types'
import { priorityLabels, statusLabels } from './types'

type TodoFormProps = {
  todo: Todo | null
  availableDependencies: Todo[]
  saving: boolean
  error: TodoApiError | null
  onCancel: () => void
  onDependencySearch: (query: string) => void
  onSubmit: (input: TodoInput) => Promise<void>
}

export default function TodoForm({
  todo,
  availableDependencies,
  saving,
  error,
  onCancel,
  onDependencySearch,
  onSubmit,
}: TodoFormProps) {
  const [name, setName] = useState(todo?.name || '')
  const [description, setDescription] = useState(todo?.description || '')
  const [dueDate, setDueDate] = useState(todo?.dueDate || '')
  const [status, setStatus] = useState<TodoStatus>(todo?.status || 'NOT_STARTED')
  const [priority, setPriority] = useState<TodoPriority>(todo?.priority || 'MEDIUM')
  const [dependencyIds, setDependencyIds] = useState<string[]>(todo?.dependencyIds || [])
  const [dependencySearch, setDependencySearch] = useState('')
  const [frequency, setFrequency] = useState<RecurrenceFrequency | ''>(todo?.recurrence?.frequency || '')
  const [interval, setInterval] = useState(todo?.recurrence?.interval.toString() || '1')
  const [unit, setUnit] = useState<RecurrenceUnit>(todo?.recurrence?.unit || 'DAYS')
  const [clientError, setClientError] = useState<string | null>(null)

  // A TODO cannot depend on itself. Candidate matching is server-backed so the
  // selector remains useful when the shared list contains thousands of TODOs.
  const dependencies = availableDependencies.filter((candidate) => candidate.id !== todo?.id)

  const toggleDependency = (id: string) => {
    setDependencyIds((current) => current.includes(id)
      ? current.filter((dependencyId) => dependencyId !== id)
      : [...current, id])
  }

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setClientError(null)

    if (!name.trim()) {
      setClientError('Name is required.')
      return
    }
    if (frequency && !dueDate) {
      setClientError('A recurring TODO needs a due date.')
      return
    }
    if (frequency === 'CUSTOM' && Number(interval) < 1) {
      setClientError('Custom recurrence must use a positive interval.')
      return
    }

    await onSubmit({
      name: name.trim(),
      description: description.trim() || null,
      dueDate: dueDate || null,
      status,
      priority,
      dependencyIds,
      // Standard schedules have canonical server-side interval/unit values. Only a
      // custom schedule sends those fields explicitly.
      recurrence: frequency
        ? frequency === 'CUSTOM'
          ? { frequency, interval: Number(interval), unit }
          : { frequency }
        : null,
    })
  }

  return (
    <form className="todo-form" onSubmit={submit}>
      <div className="editor-heading">
        <div>
          <p className="editor-context">{todo ? 'Edit TODO' : 'New TODO'}</p>
          <h2 id="editor-title">{todo ? todo.name : 'Add work to the list'}</h2>
        </div>
        <button className="text-button" onClick={onCancel} type="button">Close</button>
      </div>

      {(clientError || error) && (
        <div className="form-error" role="alert">
          <strong>{clientError || error?.message}</strong>
          {error?.code && <span>{error.code.replaceAll('_', ' ')}</span>}
        </div>
      )}

      <div className="field full-field">
        <label htmlFor="todo-name">Name</label>
        <input
          autoFocus
          id="todo-name"
          maxLength={120}
          onChange={(event) => setName(event.target.value)}
          placeholder="Prepare the live demo"
          required
          value={name}
        />
        {error?.fieldErrors.name && <small className="field-error">{error.fieldErrors.name}</small>}
      </div>

      <div className="field full-field">
        <label htmlFor="todo-description">Description</label>
        <textarea
          id="todo-description"
          maxLength={2000}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Add useful context, acceptance notes, or the next action."
          rows={4}
          value={description}
        />
        {error?.fieldErrors.description && <small className="field-error">{error.fieldErrors.description}</small>}
      </div>

      <div className="form-grid">
        <div className="field">
          <label htmlFor="todo-due-date">Due date</label>
          <input id="todo-due-date" onChange={(event) => setDueDate(event.target.value)} type="date" value={dueDate} />
        </div>
        <div className="field">
          <label htmlFor="todo-priority">Priority</label>
          <select id="todo-priority" onChange={(event) => setPriority(event.target.value as TodoPriority)} value={priority}>
            {Object.entries(priorityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="todo-status">Status</label>
          <select id="todo-status" onChange={(event) => setStatus(event.target.value as TodoStatus)} value={status}>
            {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="todo-recurrence">Recurrence</label>
          <select
            id="todo-recurrence"
            onChange={(event) => setFrequency(event.target.value as RecurrenceFrequency | '')}
            value={frequency}
          >
            <option value="">Does not repeat</option>
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
            <option value="CUSTOM">Custom</option>
          </select>
        </div>
      </div>

      {frequency === 'CUSTOM' && (
        <div className="custom-recurrence" aria-label="Custom recurrence">
          <div className="field">
            <label htmlFor="recurrence-interval">Repeat every</label>
            <input
              id="recurrence-interval"
              min="1"
              onChange={(event) => setInterval(event.target.value)}
              type="number"
              value={interval}
            />
          </div>
          <div className="field">
            <label htmlFor="recurrence-unit">Unit</label>
            <select id="recurrence-unit" onChange={(event) => setUnit(event.target.value as RecurrenceUnit)} value={unit}>
              <option value="DAYS">Days</option>
              <option value="WEEKS">Weeks</option>
              <option value="MONTHS">Months</option>
            </select>
          </div>
        </div>
      )}

      <fieldset className="dependencies-fieldset">
        <legend>Dependencies</legend>
        <p>A TODO remains blocked until every selected prerequisite is completed.</p>
        <input
          aria-label="Search dependencies"
          onChange={(event) => {
            setDependencySearch(event.target.value)
            onDependencySearch(event.target.value)
          }}
          placeholder="Find a TODO"
          type="search"
          value={dependencySearch}
        />
        <div className="dependency-options">
          {dependencies.length === 0 ? (
            <p className="dependency-empty">No matching TODOs available.</p>
          ) : dependencies.map((candidate) => (
            <label className="dependency-option" key={candidate.id}>
              <input
                checked={dependencyIds.includes(candidate.id)}
                onChange={() => toggleDependency(candidate.id)}
                type="checkbox"
              />
              <span>
                <strong>{candidate.name}</strong>
                <small>{statusLabels[candidate.status]}</small>
              </span>
            </label>
          ))}
        </div>
      </fieldset>

      <div className="form-actions">
        <button className="secondary-button" onClick={onCancel} type="button">Cancel</button>
        <button className="primary-button" disabled={saving} type="submit">
          {saving ? 'Saving...' : todo ? 'Save changes' : 'Create TODO'}
        </button>
      </div>
    </form>
  )
}
