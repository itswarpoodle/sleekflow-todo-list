import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

test('loads existing TODOs', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify([
    {
      id: '1',
      name: 'Review the project brief',
      status: 'NOT_STARTED',
      createdAt: '2026-08-27T00:00:00Z',
      updatedAt: '2026-08-27T00:00:00Z',
    },
  ]), { status: 200 }))

  render(<App />)

  expect(await screen.findByText('Review the project brief')).toBeInTheDocument()
  expect(screen.getByText('1 total')).toBeInTheDocument()
})

test('creates a TODO through the API', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch')
    .mockResolvedValueOnce(new Response('[]', { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({
      id: '2',
      name: 'Prepare the live demo',
      status: 'NOT_STARTED',
      createdAt: '2026-08-27T00:00:00Z',
      updatedAt: '2026-08-27T00:00:00Z',
    }), { status: 201 }))

  render(<App />)
  await screen.findByText('Nothing here yet. Add the first TODO above.')

  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Prepare the live demo' } })
  fireEvent.click(screen.getByRole('button', { name: 'Add TODO' }))

  expect(await screen.findByText('Prepare the live demo')).toBeInTheDocument()
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  expect(fetchMock).toHaveBeenLastCalledWith('/api/todos', expect.objectContaining({ method: 'POST' }))
})
