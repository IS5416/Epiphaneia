import type { ApiResponse, ApiListResponse } from '../types';

const BASE = '/api/v1';
const UNAUTHORIZED = 401;

class ApiError extends Error {
  code: string;
  status: number;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

function redirectOn401(): void {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.body) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(`${BASE}${path}`, {
    credentials: 'same-origin',
    headers: { ...headers, ...(options.headers as Record<string, string> | undefined) },
    ...options,
  });

  if (res.status === UNAUTHORIZED) {
    redirectOn401();
    throw new ApiError(UNAUTHORIZED, 'UNAUTHORIZED', 'Session expired');
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const body = await res.json().catch(() => ({}));

  if (!res.ok) {
    const error = (body as ApiResponse<never>).error;
    throw new ApiError(
      res.status,
      error?.code ?? 'UNKNOWN',
      error?.message ?? `HTTP ${res.status}`,
    );
  }

  const wrapped = body as ApiResponse<T>;
  if (wrapped.error) {
    throw new ApiError(res.status, wrapped.error.code, wrapped.error.message);
  }

  return wrapped.data as T;
}

async function getList<T>(
  path: string,
): Promise<ApiListResponse<T>> {
  const res = await fetch(`${BASE}${path}`, { credentials: 'same-origin' });

  if (res.status === UNAUTHORIZED) {
    redirectOn401();
    throw new ApiError(UNAUTHORIZED, 'UNAUTHORIZED', 'Session expired');
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const error = (body as ApiResponse<never>).error;
    throw new ApiError(
      res.status,
      error?.code ?? 'UNKNOWN',
      error?.message ?? `HTTP ${res.status}`,
    );
  }

  return res.json() as Promise<ApiListResponse<T>>;
}

export { request, getList, ApiError, BASE };
