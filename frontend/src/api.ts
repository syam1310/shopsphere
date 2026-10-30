export type Product = {
  id: number;
  name: string;
  description?: string | null;
  price: number | string;
  quantity: number;
  category: string;
  imageUrl?: string | null;
  active: boolean;
};

export type CartItem = {
  productId: number;
  productName: string;
  unitPrice: number | string;
  quantity: number;
  subtotal: number | string;
};

export type Cart = {
  cartId: number | null;
  userId: number;
  items: CartItem[];
  total: number | string;
};

export type OrderItem = {
  productId: number;
  quantity: number;
  unitPrice: number | string;
  subtotal: number | string;
};

export type ShopOrder = {
  id: number;
  userId: number;
  totalAmount: number | string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | string;
  createdAt: string;
  items: OrderItem[];
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType?: string;
};

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

const ACCESS_KEY = 'shopsphere.accessToken';
const REFRESH_KEY = 'shopsphere.refreshToken';

export function saveSession(tokens: AuthResponse) {
  sessionStorage.setItem(ACCESS_KEY, tokens.accessToken);
  sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken);
}

export function clearSession() {
  sessionStorage.removeItem(ACCESS_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
}

export function hasSession() {
  return Boolean(sessionStorage.getItem(ACCESS_KEY));
}

export function sessionRole(): string | null {
  const token = sessionStorage.getItem(ACCESS_KEY);
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))) as { role?: unknown };
    return typeof decoded.role === 'string' ? decoded.role : null;
  } catch {
    return null;
  }
}

type RequestOptions = RequestInit & { retryAuth?: boolean };

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { retryAuth = true, ...requestOptions } = options;
  const headers = new Headers(requestOptions.headers);
  const accessToken = sessionStorage.getItem(ACCESS_KEY);
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  if (requestOptions.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, { ...requestOptions, headers });

  if (response.status === 401 && retryAuth && accessToken) {
    const refreshToken = sessionStorage.getItem(REFRESH_KEY);
    if (refreshToken) {
      const refreshed = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (refreshed.ok) {
        saveSession((await refreshed.json()) as AuthResponse);
        return api<T>(path, { ...options, retryAuth: false });
      }
    }

    clearSession();
    window.dispatchEvent(new Event('shopsphere:session-expired'));
  }

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as
      | { message?: string; error?: string; detail?: string }
      | null;
    const message = payload?.message ?? payload?.detail ?? payload?.error
      ?? `Request failed (${response.status})`;
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function money(value: number | string) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(Number(value));
}
