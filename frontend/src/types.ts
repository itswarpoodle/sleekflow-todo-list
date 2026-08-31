export type TodoStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED'
export type TodoPriority = 'LOW' | 'MEDIUM' | 'HIGH'
export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM'
export type RecurrenceUnit = 'DAYS' | 'WEEKS' | 'MONTHS'

export type RecurrenceRule = {
  frequency: RecurrenceFrequency
  interval: number
  unit: RecurrenceUnit
}

/** Read model returned by the Spring Boot API. */
export type Todo = {
  id: string
  name: string
  description: string | null
  dueDate: string | null
  status: TodoStatus
  priority: TodoPriority
  version: number
  dependencyIds: string[]
  blocked: boolean
  recurrence: RecurrenceRule | null
  previousOccurrenceId: string | null
  createdAt: string
  updatedAt: string
}

/** Complete editable state sent by both create and update workflows. */
export type TodoInput = {
  name: string
  description: string | null
  dueDate: string | null
  status: TodoStatus
  priority: TodoPriority
  dependencyIds: string[]
  recurrence: {
    frequency: RecurrenceFrequency
    interval?: number
    unit?: RecurrenceUnit
  } | null
}

export type TodoPage = {
  content: Todo[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Bounded server-side list controls; omitted properties mean no filter. */
export type TodoQuery = {
  page: number
  size: number
  status?: TodoStatus
  priority?: TodoPriority
  dueDate?: string
  blocked?: boolean
  name?: string
  sort?: 'dueDate' | 'priority' | 'status' | 'name'
  direction: 'asc' | 'desc'
}

export type ApiError = {
  code?: string
  message?: string
  fieldErrors?: Record<string, string>
}

export const statusLabels: Record<TodoStatus, string> = {
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
}

export const priorityLabels: Record<TodoPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
}
