const API_BASE = '/api'
const CSRF_HEADER = 'X-XSRF-TOKEN'

/**
 * A failed API call. `fieldErrors` is filled from the backend's problem+json
 * `errors` object, so a form can put messages next to the fields they belong to.
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }
}

type CsrfTokenResponse = {
  token: string
  headerName: string
  parameterName: string
}

const CSRF_COOKIE = 'XSRF-TOKEN'

/**
 * The cookie is the source of truth for the CSRF token — that is exactly why
 * the backend sets it with HttpOnly=false. Reading it per request instead of
 * keeping a copy in memory means the value can never go stale: signing in or
 * out replaces the cookie, and the next write picks the new one up by itself.
 */
function readCsrfCookie(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${CSRF_COOKIE}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

/** Asks the backend for a token, which also (re)sets the cookie. */
async function fetchCsrfToken(): Promise<string> {
  const response = await fetch(`${API_BASE}/auth/csrf`, { credentials: 'include' })
  if (!response.ok) {
    throw new ApiError(response.status, 'Kon geen CSRF-token ophalen')
  }
  const body: CsrfTokenResponse = await response.json()
  return body.token
}

/** The cookie when there is one, otherwise bootstrap a fresh token. */
async function csrfTokenForWrite(): Promise<string> {
  return readCsrfCookie() ?? (await fetchCsrfToken())
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
}

function isWrite(method: string): boolean {
  return method !== 'GET' && method !== 'HEAD'
}

async function toApiError(response: Response): Promise<ApiError> {
  let detail = `Er ging iets mis (${response.status})`
  let fieldErrors: Record<string, string> = {}

  try {
    const body = await response.json()
    if (typeof body?.detail === 'string') {
      detail = body.detail
    }
    if (body?.errors && typeof body.errors === 'object') {
      fieldErrors = body.errors as Record<string, string>
    }
  } catch {
    // no JSON body (a 401 from the security filter chain has none)
  }

  return new ApiError(response.status, detail, fieldErrors)
}

/**
 * The single place that talks to the backend.
 *
 * `credentials: 'include'` sends the httpOnly login cookie, and every write
 * carries the CSRF token in the header the backend expects. Screens call the
 * wrappers in `auth.ts` and never deal with either.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'

  const send = async (token: string | null): Promise<Response> => {
    const headers: Record<string, string> = {}
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    if (token) {
      headers[CSRF_HEADER] = token
    }
    return fetch(`${API_BASE}${path}`, {
      method,
      credentials: 'include',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  }

  let response: Response
  if (isWrite(method)) {
    response = await send(await csrfTokenForWrite())
    // safety net: if the token was rejected anyway, ask for a new one and retry once
    if (response.status === 403) {
      response = await send(await fetchCsrfToken())
    }
  } else {
    response = await send(null)
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
