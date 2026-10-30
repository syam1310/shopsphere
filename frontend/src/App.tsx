import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, saveSession, clearSession, hasSession, sessionRole, money } from './api';
import type { Cart, Product, ShopOrder, AuthResponse } from './api';

type Page = 'shop' | 'orders' | 'admin';
type AuthMode = 'login' | 'register' | 'admin-setup';
type ProductReview = { id: number; productId: number; reviewerName: string; rating: number; comment: string; createdAt: string };

const emptyCart: Cart = { cartId: null, userId: 0, items: [], total: 0 };
const CATALOG_PAGE_SIZE = 48;
const paymentMethods = ['UPI', 'CARD', 'WALLET'] as const;
const GUEST_CART_KEY = 'shopsphere.guestCart';
const SHADE_NAMES = ['Black', 'White', 'Blue', 'Sage'] as const;
const CATEGORY_IMAGE: Record<string, string> = {
  'Home & Kitchen': 'catalog-extra-19', Fashion: 'catalog-extra-01', Books: 'catalog-extra-02',
  Beauty: 'catalog-extra-03', 'Sports & Outdoors': 'catalog-extra-04', 'Toys & Games': 'catalog-extra-05',
  Grocery: 'catalog-extra-06', 'Office Supplies': 'catalog-extra-07', Automotive: 'catalog-extra-08',
  'Pet Supplies': 'catalog-extra-09', 'Health & Wellness': 'catalog-extra-10', Garden: 'catalog-extra-11',
  'Baby & Kids': 'catalog-extra-12', Footwear: 'catalog-extra-13', 'Jewelry & Accessories': 'catalog-extra-14',
  'Luggage & Travel': 'catalog-extra-15', Music: 'catalog-extra-16', Appliances: 'catalog-extra-17',
  'Furniture & Decor': 'catalog-extra-18', Electronics: 'catalog-07',
};
// Keep the catalog filters usable if the category endpoint is temporarily
// unavailable. The API response replaces this list when it succeeds.
const FALLBACK_CATEGORIES = Object.keys(CATEGORY_IMAGE);
const PRODUCT_IMAGE_HINTS: Array<[string, string]> = [
  ['mouse', 'shade'], ['headphone', 'shade'], ['backpack', 'shade'], ['daypack', 'shade'],
  ['water bottle', 'shade'], ['shaker bottle', 'shade'], ['cycling bottle', 'shade'], ['flask', 'shade'],
  ['sneaker', 'shade'], ['shoes', 'shade'], ['t-shirt', 'shade'], ['tshirt', 'shade'],
  ['cotton shirt', 'shade'], ['book', 'catalog-extra-02'], ['journal', 'catalog-extra-02'],
  ['notebook', 'catalog-extra-07'], ['coffee', 'catalog-extra-06'], ['tea', 'catalog-extra-06'],
  ['yoga mat', 'catalog-14'], ['speaker', 'catalog-08'], ['keyboard', 'catalog-07'],
  ['lamp', 'catalog-12'], ['plant', 'catalog-18'], ['garden', 'catalog-extra-11'],
  ['guitar', 'catalog-extra-16'], ['watch', 'catalog-16'], ['jewelry', 'catalog-extra-14'],
  ['necklace', 'catalog-extra-14'], ['toy', 'catalog-extra-05'], ['puzzle', 'catalog-extra-05'],
  ['pet', 'catalog-extra-09'], ['dog', 'catalog-extra-09'], ['cat', 'catalog-extra-09'],
  ['thermometer', 'catalog-extra-10'], ['baby', 'catalog-extra-12'], ['sneaker', 'catalog-extra-13'],
  ['shoe', 'catalog-extra-13'], ['kettle', 'catalog-extra-17'], ['toaster', 'catalog-extra-17'],
  ['cookware', 'catalog-extra-20'], ['pan', 'catalog-extra-20'], ['dinner', 'catalog-extra-19'],
  ['ceramic', 'catalog-11'], ['mug', 'catalog-11'], ['pillow', 'catalog-19'], ['cushion', 'catalog-19'],
  ['organizer', 'catalog-20'], ['desk', 'catalog-20'], ['car', 'catalog-extra-08'],
  ['skincare', 'catalog-extra-03'], ['moisturizer', 'catalog-extra-03'], ['beauty', 'catalog-extra-03'],
  ['sunglasses', 'catalog-15'], ['luggage', 'catalog-extra-15'], ['travel', 'catalog-extra-15'],
];

function productShadeFamily(name: string): number | null {
  const value = name.toLowerCase();
  if (value.includes('mouse')) return 1;
  if (value.includes('headphone')) return 2;
  if (value.includes('backpack') || value.includes('daypack')) return 3;
  if (value.includes('bottle') || value.includes('flask')) return 4;
  if (value.includes('sneaker') || value.includes('shoes')) return 5;
  if (value.includes('t-shirt') || value.includes('tshirt') || value.includes('cotton shirt')) return 6;
  return null;
}

function productImage(name: string, category: string, shade = 0, imageUrl?: string | null): string {
  const value = name.trim().toLowerCase();
  const shadeFamily = productShadeFamily(value);
  if (shadeFamily) return `/product-images/shade-${String((shadeFamily - 1) * 4 + shade + 1).padStart(2, '0')}.jpg`;
  const hinted = PRODUCT_IMAGE_HINTS.find(([hint]) => value.includes(hint))?.[1];
  const image = hinted ?? CATEGORY_IMAGE[category] ?? 'catalog-07';
  return imageUrl?.startsWith('/product-images/') ? imageUrl : `/product-images/${image}.jpg`;
}

function productIdFromPath(): number | null {
  const match = window.location.pathname.match(/^\/products\/(\d+)\/?$/);
  return match ? Number(match[1]) : null;
}

type GuestCartItem = Cart['items'][number] & { syncTargetQuantity?: number };

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: { client_id: string; callback: (response: { credential?: string }) => void }) => void;
          renderButton: (element: HTMLElement, options: { theme: string; size: string; text: string; shape: string; width: number }) => void;
        };
      };
    };
  }
}

function loadGoogleIdentityScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-google-identity]');
    const script = existing ?? document.createElement('script');
    script.addEventListener('load', () => resolve(), { once: true });
    script.addEventListener('error', () => reject(new Error('Google sign-in could not load.')), { once: true });
    if (!existing) {
      script.dataset.googleIdentity = 'true';
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  });
}

function readGuestCart(): GuestCartItem[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(GUEST_CART_KEY) ?? '[]');
    return Array.isArray(saved) ? saved.filter((item): item is GuestCartItem =>
      item && Number.isInteger(item.productId) && Number.isInteger(item.quantity) && item.quantity > 0
      && typeof item.productName === 'string' && item.unitPrice !== undefined)
      .map((item) => ({ ...item, subtotal: Number(item.unitPrice) * item.quantity })) : [];
  } catch {
    return [];
  }
}

function writeGuestCart(items: GuestCartItem[]) {
  if (items.length === 0) localStorage.removeItem(GUEST_CART_KEY);
  else localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
}

function makeGuestCart(items: GuestCartItem[]): Cart {
  return {
    cartId: null,
    userId: 0,
    items: items.map(({ syncTargetQuantity: _target, ...item }) => item),
    total: items.reduce((sum, item) => sum + Number(item.unitPrice) * item.quantity, 0),
  };
}

function App() {
  const [authenticated, setAuthenticated] = useState(hasSession);
  const [isAdmin, setIsAdmin] = useState(() => sessionRole() === 'ADMIN');
  const [page, setPage] = useState<Page>('shop');
  const [selectedProductId, setSelectedProductId] = useState<number | null>(productIdFromPath);
  const [pendingBuyNowId, setPendingBuyNowId] = useState<number | null>(null);
  const [buyNowProductId, setBuyNowProductId] = useState<number | null>(null);
  const [buyNowPaymentMethod, setBuyNowPaymentMethod] = useState<(typeof paymentMethods)[number] | ''>('');
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [showAuth, setShowAuth] = useState(false);
  const [products, setProducts] = useState<Product[]>([]);
  const [catalogCategories, setCatalogCategories] = useState<string[]>(FALLBACK_CATEGORIES);
  const [catalogPage, setCatalogPage] = useState(0);
  const [catalogTotalPages, setCatalogTotalPages] = useState(0);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogLoaded, setCatalogLoaded] = useState(false);
  const [totalProducts, setTotalProducts] = useState(0);
  const [failedImages, setFailedImages] = useState<Set<number>>(() => new Set());
  const [selectedShades, setSelectedShades] = useState<Record<number, number>>({});
  const [cart, setCart] = useState<Cart>(emptyCart);
  const [orders, setOrders] = useState<ShopOrder[]>([]);
  const [productReviews, setProductReviews] = useState<ProductReview[]>([]);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [reviewBusy, setReviewBusy] = useState(false);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('All');
  const [cartOpen, setCartOpen] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<(typeof paymentMethods)[number]>('UPI');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [checkoutPending, setCheckoutPending] = useState(false);
  const [adminSetupAvailable, setAdminSetupAvailable] = useState(false);
  const googleButtonRef = useRef<HTMLDivElement>(null);
  const catalogRequestId = useRef(0);
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? '';

  useEffect(() => {
    const restorePath = () => {
      const productId = productIdFromPath();
      setSelectedProductId(productId);
      if (!productId && window.location.pathname === '/orders') setPage('orders');
      else if (!productId && window.location.pathname === '/admin' && sessionRole() === 'ADMIN') setPage('admin');
      else setPage('shop');
    };
    window.addEventListener('popstate', restorePath);
    return () => window.removeEventListener('popstate', restorePath);
  }, []);

  const loadProducts = useCallback(async (requestedPage = 0, append = false, selectedCategory = 'All', searchTerm = '') => {
    const requestId = ++catalogRequestId.current;
    setCatalogLoading(true);
    try {
      const params = new URLSearchParams({ page: String(requestedPage), size: String(CATALOG_PAGE_SIZE) });
      if (selectedCategory !== 'All') params.set('category', selectedCategory);
      if (searchTerm.trim()) params.set('search', searchTerm.trim());
      const result = await api<{ content: Product[]; totalElements: number; totalPages: number; number: number }>(`/api/products/catalog?${params}`);
      if (requestId !== catalogRequestId.current) return;
      setProducts((current) => {
        if (!append) return result.content.filter((product) => product.active);
        const merged = new Map(current.map((product) => [product.id, product]));
        result.content.filter((product) => product.active).forEach((product) => merged.set(product.id, product));
        return [...merged.values()];
      });
      setCatalogPage(result.number);
      setCatalogTotalPages(result.totalPages);
      setTotalProducts(result.totalElements);
      setCatalogLoaded(true);
    } finally {
      if (requestId === catalogRequestId.current) setCatalogLoading(false);
    }
  }, []);

  const loadCart = useCallback(async () => {
    if (!hasSession()) {
      setCart(makeGuestCart(readGuestCart()));
      return;
    }

    let serverCart = await api<Cart>('/api/cart');
    let guestItems = readGuestCart();
    while (guestItems.length > 0) {
      const guestItem = guestItems[0];
      const serverItem = serverCart.items.find((item) => item.productId === guestItem.productId);
      const targetQuantity = guestItem.syncTargetQuantity ?? (serverItem?.quantity ?? 0) + guestItem.quantity;
      guestItems[0] = { ...guestItem, syncTargetQuantity: targetQuantity };
      writeGuestCart(guestItems);

      if ((serverItem?.quantity ?? 0) < targetQuantity) {
        try {
          serverCart = serverItem
            ? await api<Cart>(`/api/cart/items/${guestItem.productId}`, {
              method: 'PUT', body: JSON.stringify({ quantity: targetQuantity }),
            })
            : await api<Cart>('/api/cart/items', {
              method: 'POST', body: JSON.stringify({ productId: guestItem.productId, quantity: targetQuantity }),
            });
        } catch (syncError) {
          // A timeout may happen after the cart write committed. Re-read before retrying.
          const reconciled = await api<Cart>('/api/cart');
          const reconciledItem = reconciled.items.find((item) => item.productId === guestItem.productId);
          if ((reconciledItem?.quantity ?? 0) < targetQuantity) throw syncError;
          serverCart = reconciled;
        }
      }

      guestItems = guestItems.filter((item) => item.productId !== guestItem.productId);
      writeGuestCart(guestItems);
    }
    setCart(serverCart);
  }, []);

  const loadOrders = useCallback(async () => {
    setOrders(await api<ShopOrder[]>('/api/orders/my'));
  }, []);

  const loadStore = useCallback(async () => {
    setError('');
    try {
      await Promise.all([loadProducts(), loadCart()]);
      if (page === 'orders') await loadOrders();
    } catch (requestError) {
      setError(messageFor(requestError));
    }
  }, [loadCart, loadOrders, loadProducts, page]);

  useEffect(() => {
    const expired = () => {
      writeGuestCart(cart.items);
      setCart(makeGuestCart(readGuestCart()));
      setAuthenticated(false);
      setIsAdmin(false);
      setShowAuth(true);
      setPage('shop');
      setError('Your session expired. Please sign in again.');
    };
    window.addEventListener('shopsphere:session-expired', expired);
    return () => window.removeEventListener('shopsphere:session-expired', expired);
  }, [cart.items]);

  useEffect(() => {
    if (authenticated) void loadStore();
  }, [authenticated, loadStore]);

  useEffect(() => {
    void loadProducts().catch((requestError) => setError(messageFor(requestError)));
    void api<string[]>('/api/products/categories', { retryAuth: false })
      .then((result) => {
        const categories = result.filter((item): item is string => typeof item === 'string' && Boolean(item.trim()));
        setCatalogCategories(categories.length ? categories.sort((a, b) => a.localeCompare(b)) : FALLBACK_CATEGORIES);
      })
      .catch(() => setCatalogCategories(FALLBACK_CATEGORIES));
    if (!authenticated) setCart(makeGuestCart(readGuestCart()));
  }, [authenticated, loadProducts]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadProducts(0, false, category, search).catch((requestError) => setError(messageFor(requestError)));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [category, search]);

  useEffect(() => {
    void api<{ available: boolean }>('/api/auth/admin-setup/status', { retryAuth: false })
      .then((status) => setAdminSetupAvailable(status.available))
      .catch(() => setAdminSetupAvailable(false));
  }, []);

  useEffect(() => {
    if (authenticated && page === 'orders') void loadOrders().catch((e) => setError(messageFor(e)));
  }, [authenticated, page, loadOrders]);

  useEffect(() => {
    if (!showAuth || authMode !== 'login' || !googleClientId || !googleButtonRef.current) return;
    let cancelled = false;
    void loadGoogleIdentityScript().then(() => {
      if (cancelled || !googleButtonRef.current || !window.google) return;
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: ({ credential }) => {
          if (credential) void loginWithGoogle(credential);
          else setError('Google sign-in did not return an account credential.');
        },
      });
      googleButtonRef.current.replaceChildren();
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        theme: 'outline', size: 'large', text: 'continue_with', shape: 'rectangular', width: 300,
      });
    }).catch((loadError) => setError(messageFor(loadError)));
    return () => { cancelled = true; };
  }, [authMode, googleClientId, showAuth]);

  const categories = useMemo(() => ['All', ...catalogCategories], [catalogCategories]);
  const pageControls = useMemo((): Array<number | 'gap-left' | 'gap-right'> => {
    if (catalogTotalPages <= 7) return Array.from({ length: catalogTotalPages }, (_, pageNumber) => pageNumber);
    const visiblePages = [...new Set([0, catalogPage - 1, catalogPage, catalogPage + 1, catalogTotalPages - 1])]
      .filter((pageNumber) => pageNumber >= 0 && pageNumber < catalogTotalPages)
      .sort((a, b) => a - b);
    return visiblePages.flatMap((pageNumber, index) => {
      const previous = visiblePages[index - 1];
      const gap = previous === undefined ? [] : pageNumber - previous > 1
        ? [pageNumber - previous === 2 ? previous + 1 : index === 1 ? 'gap-left' as const : 'gap-right' as const]
        : [];
      return [...gap, pageNumber];
    });
  }, [catalogPage, catalogTotalPages]);
  const visibleProducts = products;
  const displayedProducts = products;
  const selectedProduct = products.find((product) => product.id === selectedProductId) ?? null;
  const relatedProducts = useMemo(() => {
    if (!selectedProduct) return [];
    const sameCategory = products.filter((product) => product.id !== selectedProduct.id && product.category === selectedProduct.category);
    return (sameCategory.length ? sameCategory : products.filter((product) => product.id !== selectedProduct.id)).slice(0, 4);
  }, [products, selectedProduct]);

  useEffect(() => {
    setProductReviews([]);
    if (selectedProductId === null) return;
    void api<ProductReview[]>(`/api/products/${selectedProductId}/reviews`, { retryAuth: false })
      .then(setProductReviews)
      .catch(() => setProductReviews([]));
  }, [selectedProductId]);

  useEffect(() => {
    if (selectedProductId === null || products.some((product) => product.id === selectedProductId)) return;
    void api<Product>(`/api/products/${selectedProductId}`, { retryAuth: false })
      .then((product) => setProducts((current) => current.some((item) => item.id === product.id) ? current : [...current, product]))
      .catch(() => undefined);
  }, [products, selectedProductId]);

  function openProduct(product: Product) {
    history.pushState({}, '', `/products/${product.id}`);
    setSelectedProductId(product.id);
    setPage('shop');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function returnToShop() {
    history.pushState({}, '', '/');
    setSelectedProductId(null);
    setPage('shop');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const cartCount = cart.items.reduce((sum, item) => sum + item.quantity, 0);

  async function handleAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const password = String(form.get('password') ?? '');
    setBusy(true);
    setError('');
    setMessage('');

    try {
      if (authMode === 'admin-setup') {
        const name = String(form.get('name') ?? '').trim();
        const setupCode = String(form.get('setupCode') ?? '').trim();
        const tokens = await api<AuthResponse>('/api/auth/admin-setup', {
          method: 'POST',
          body: JSON.stringify({ name, email, password, setupCode }),
          retryAuth: false,
        });
        saveSession(tokens);
        setIsAdmin(sessionRole() === 'ADMIN');
        setAdminSetupAvailable(false);
        setShowAuth(false);
        setCheckoutPending(false);
        setAuthenticated(true);
        setMessage('Your ShopSphere administrator account is ready.');
      } else if (authMode === 'register') {
        const name = String(form.get('name') ?? '').trim();
        await api('/api/users/register', {
          method: 'POST',
          body: JSON.stringify({ name, email, password }),
          retryAuth: false,
        });
        setAuthMode('login');
        setMessage('Account created. Sign in to start shopping.');
      } else {
        const tokens = await api<AuthResponse>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email, password }),
          retryAuth: false,
        });
        saveSession(tokens);
        setIsAdmin(sessionRole() === 'ADMIN');
        setShowAuth(false);
        setMessage('Welcome to ShopSphere.');
        if (pendingBuyNowId !== null) {
          const product = products.find((item) => item.id === pendingBuyNowId);
          setPendingBuyNowId(null);
          setAuthenticated(true);
          if (product) startBuyNow(product);
          return;
        }
        const continueToCheckout = checkoutPending;
        if (continueToCheckout) {
          try {
            await loadCart();
            setCheckoutPending(false);
            await checkout();
            setAuthenticated(true);
          } catch {
            setAuthenticated(true);
            setError('You are signed in, but we could not sync your saved bag yet. Open your bag and retry checkout.');
          }
        } else {
          setAuthenticated(true);
        }
      }
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function loginWithGoogle(credential: string) {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const tokens = await api<AuthResponse>('/api/auth/google', {
        method: 'POST', body: JSON.stringify({ credential }), retryAuth: false,
      });
      saveSession(tokens);
      setIsAdmin(sessionRole() === 'ADMIN');
      setShowAuth(false);
      setMessage('Welcome to ShopSphere.');
      if (pendingBuyNowId !== null) {
        const product = products.find((item) => item.id === pendingBuyNowId);
        setPendingBuyNowId(null);
        setAuthenticated(true);
        if (product) startBuyNow(product);
        return;
      }
      if (checkoutPending) {
        try {
          await loadCart();
          setCheckoutPending(false);
          await checkout();
        } catch {
          setError('You are signed in, but we could not sync your saved bag yet. Open your bag and retry checkout.');
        }
      }
      setAuthenticated(true);
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    const refreshToken = sessionStorage.getItem('shopsphere.refreshToken');
    try {
      if (refreshToken) {
        await api('/api/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
          retryAuth: false,
        });
      }
    } catch {
      // Clear the browser session even if the service is unavailable.
    }
    clearSession();
    setIsAdmin(false);
    setAuthenticated(false);
    setShowAuth(false);
    setPage('shop');
    writeGuestCart(cart.items);
    setCart(makeGuestCart(readGuestCart()));
    setOrders([]);
  }

  async function addToCart(product: Product) {
    setBusy(true);
    setError('');
    try {
      const existing = cart.items.find((item) => item.productId === product.id);
      const nextQuantity = (existing?.quantity ?? 0) + 1;
      let updated: Cart;
      if (hasSession()) {
        updated = existing
          ? await api<Cart>(`/api/cart/items/${product.id}`, {
            method: 'PUT', body: JSON.stringify({ quantity: nextQuantity }),
          })
          : await api<Cart>('/api/cart/items', {
            method: 'POST', body: JSON.stringify({ productId: product.id, quantity: 1 }),
          });
      } else {
        const guestItems = readGuestCart().filter((item) => item.productId !== product.id);
        guestItems.push({
          productId: product.id,
          productName: product.name,
          unitPrice: product.price,
          quantity: nextQuantity,
          subtotal: Number(product.price) * nextQuantity,
        });
        writeGuestCart(guestItems);
        updated = makeGuestCart(guestItems);
      }
      setCart(updated);
      setMessage(`${product.name} added to your bag.`);
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function createProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const product = {
      name: String(form.get('name') ?? '').trim(),
      description: String(form.get('description') ?? '').trim(),
      price: Number(form.get('price')),
      quantity: Number(form.get('quantity')),
      category: String(form.get('category') ?? '').trim(),
      imageUrl: String(form.get('imageUrl') ?? '').trim() || null,
    };
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const created = await api<Product>('/api/products', {
        method: 'POST',
        body: JSON.stringify(product),
      });
      let inventoryReady = false;
      try {
        await api('/api/inventory', {
          method: 'POST',
          body: JSON.stringify({ productId: created.id, quantity: product.quantity }),
        });
        inventoryReady = true;
      } catch {
        // A timeout may happen after Inventory Service committed the create.
        // Re-read the record so an already-created stock row isn't mistaken for failure.
        try {
          const inventory = await api<{ quantity: number }>(`/api/inventory/product/${created.id}`);
          inventoryReady = inventory.quantity === product.quantity;
        } catch {
          inventoryReady = false;
        }
      }
      if (!inventoryReady) {
        await loadProducts(0, false, category, search).catch(() => undefined);
        throw new Error(`${created.name} was created, but its stock could not be set up. It will remain unavailable until inventory is corrected.`);
      }
      await loadProducts(0, false, category, search);
      const updatedCategories = await api<string[]>('/api/products/categories', { retryAuth: false });
      setCatalogCategories(updatedCategories.filter(Boolean).sort());
      formElement.reset();
      setMessage(`${created.name} is now listed with ${product.quantity} in stock.`);
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function updateQuantity(item: Cart['items'][number], quantity: number) {
    setError('');
    try {
      if (!hasSession()) {
        const guestItems = readGuestCart()
          .filter((line) => line.productId !== item.productId || quantity > 0)
          .map((line) => line.productId === item.productId
            ? { ...line, quantity, subtotal: Number(line.unitPrice) * quantity }
            : line);
        writeGuestCart(guestItems);
        setCart(makeGuestCart(guestItems));
        return;
      }
      if (quantity <= 0) {
        await api(`/api/cart/items/${item.productId}`, { method: 'DELETE' });
        setCart((current) => ({ ...current, items: current.items.filter((line) => line.productId !== item.productId) }));
      } else {
        setCart(await api<Cart>(`/api/cart/items/${item.productId}`, {
          method: 'PUT', body: JSON.stringify({ quantity }),
        }));
      }
    } catch (requestError) {
      setError(messageFor(requestError));
    }
  }

  async function checkout() {
    if (!hasSession()) {
      setCheckoutPending(true);
      setAuthMode('login');
      setShowAuth(true);
      setMessage('Sign in or create an account to continue to checkout. Your bag is saved.');
      return;
    }
    if (cart.items.length === 0) {
      setError('Add an item to your bag before checkout.');
      return;
    }
    setBusy(true);
    setError('');
    setMessage('');
    const keyName = 'shopsphere.checkoutKey';
    const idempotencyKey = sessionStorage.getItem(keyName) ?? crypto.randomUUID();
    sessionStorage.setItem(keyName, idempotencyKey);

    try {
      if (readGuestCart().length > 0) await loadCart();
      const order = await api<ShopOrder>(`/api/orders/checkout?paymentMethod=${paymentMethod}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
      });
      sessionStorage.removeItem(keyName);
      setCart(emptyCart);
      setCartOpen(false);
      setPage('orders');
      setOrders((current) => [order, ...current.filter((item) => item.id !== order.id)]);
      setMessage(`Order #${order.id} is confirmed. Thank you for shopping with us.`);
      await loadCart();
    } catch (requestError) {
      if (requestError instanceof ApiError && (
        [400, 402].includes(requestError.status)
        || (requestError.status === 409 && requestError.message.toLowerCase().includes('belongs to a failed request'))
        || (requestError.status === 409 && requestError.message.toLowerCase().includes('inventory is insufficient'))
      )) {
        sessionStorage.removeItem(keyName);
      }
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  function startBuyNow(product: Product) {
    if (product.quantity <= 0) {
      setError('This product is currently out of stock.');
      return;
    }
    if (!hasSession()) {
      setPendingBuyNowId(product.id);
      setAuthMode('login');
      setShowAuth(true);
      setMessage('Sign in to buy this item directly. Your bag will stay unchanged.');
      return;
    }
    setError('');
    setBuyNowPaymentMethod('');
    setBuyNowProductId(product.id);
  }

  async function confirmBuyNow(product: Product) {
    if (!buyNowPaymentMethod) {
      setError('Choose a payment method before confirming your order.');
      return;
    }
    if (!hasSession()) {
      setBuyNowProductId(null);
      setPendingBuyNowId(product.id);
      setAuthMode('login');
      setShowAuth(true);
      return;
    }
    setBusy(true);
    setError('');
    const keyName = `shopsphere.buyNowCheckoutKey.${product.id}`;
    const idempotencyKey = sessionStorage.getItem(keyName) ?? crypto.randomUUID();
    sessionStorage.setItem(keyName, idempotencyKey);
    try {
      const order = await api<ShopOrder>(`/api/orders/checkout/buy-now?paymentMethod=${buyNowPaymentMethod}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ productId: product.id, quantity: 1 }),
      });
      sessionStorage.removeItem(keyName);
      setBuyNowProductId(null);
      setPage('orders');
      setSelectedProductId(null);
      history.pushState({}, '', '/orders');
      setOrders((current) => [order, ...current.filter((item) => item.id !== order.id)]);
      setMessage(`Order #${order.id} is confirmed. Your bag was left unchanged.`);
      void loadOrders().catch(() => undefined);
    } catch (requestError) {
      if (requestError instanceof ApiError && (
        [400, 402].includes(requestError.status)
        || (requestError.status === 409 && /failed request|inventory is insufficient/i.test(requestError.message))
      )) {
        sessionStorage.removeItem(keyName);
      }
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function cancelOrder(order: ShopOrder) {
    setError('');
    setBusy(true);
    try {
      const updated = await api<ShopOrder>(`/api/orders/${order.id}/status?status=CANCELLED`, {
        method: 'PATCH',
      });
      setOrders((current) => current.map((item) => item.id === updated.id ? updated : item));
      setMessage(`Order #${order.id} was cancelled.`);
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app-shell">
      <div className="announcement"><span>Thoughtful finds. Everyday good.</span><span>Free delivery on orders over ₹2,000</span></div>
      <header className="site-header">
        <a className="brand" href="#shop" onClick={() => setPage('shop')} aria-label="ShopSphere home">
          <span className="brand-mark">s</span><span>shopsphere<span className="brand-dot">.</span></span>
        </a>
        <nav className="main-nav" aria-label="Main navigation">
          <button className={page === 'shop' && selectedProductId === null ? 'nav-link active' : 'nav-link'} onClick={returnToShop}>Shop</button>
          {authenticated && <button className={page === 'orders' ? 'nav-link active' : 'nav-link'} onClick={() => { history.pushState({}, '', '/orders'); setSelectedProductId(null); setPage('orders'); }}>Your orders</button>}
          {authenticated && isAdmin && <button className={page === 'admin' ? 'nav-link active' : 'nav-link'} onClick={() => { history.pushState({}, '', '/admin'); setSelectedProductId(null); setPage('admin'); }}>Manage products</button>}
        </nav>
        <div className="header-actions">
          <button className="icon-button account-button" aria-label={authenticated ? 'Account menu' : 'Sign in'} onClick={() => setShowAuth(true)}>
            <span className="person-icon" aria-hidden="true">◉</span><span className="account-label">{authenticated ? 'Account' : 'Sign in'}</span>
          </button>
          <button className="bag-button" onClick={() => setCartOpen(true)} aria-label={`Open bag with ${cartCount} items`}>
            <span className="bag-icon" aria-hidden="true">♧</span><span>Bag</span><span className="bag-count">{cartCount}</span>
          </button>
        </div>
      </header>

      {message && <div className="toast success" role="status"><span>{message}</span><button onClick={() => setMessage('')} aria-label="Dismiss">×</button></div>}
      {error && <div className="toast error-toast" role="alert"><span>{error}</span><button onClick={() => setError('')} aria-label="Dismiss">×</button></div>}

      <main>
          {page === 'shop' && selectedProductId === null ? <>
            <section className="hero-banner">
              <div className="hero-text"><p className="eyebrow">THE EVERYDAY EDIT · 01</p><h1>Small joys,<br /><em>well chosen.</em></h1><p>Useful, beautiful things for the spaces and moments you call yours.</p><button className="text-button" onClick={() => document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })}>Explore the edit <span>↓</span></button></div>
              <div className="hero-art" aria-hidden="true"><div className="art-disc" /><div className="art-vase vase-back" /><div className="art-vase vase-front" /><div className="art-stem stem-one" /><div className="art-stem stem-two" /><div className="art-leaf leaf-one" /><div className="art-leaf leaf-two" /><div className="art-leaf leaf-three" /><div className="art-book book-one" /><div className="art-book book-two" /><span className="art-note">objects for<br />slow mornings</span></div>
              <div className="hero-pagination"><span className="pagination-active">01</span><span className="pagination-line" /><span>03</span></div>
            </section>

            <section id="catalog" className="catalog-section">
              <div className="catalog-heading"><div><p className="eyebrow">A GOOD PLACE TO START</p><h2>Find your good thing<span>.</span></h2></div><label className="search-box"><span aria-hidden="true">⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search the collection" aria-label="Search products" /><kbd>⌘ K</kbd></label></div>
              <div className="catalog-toolbar"><div className="category-list" aria-label="Filter by category">{categories.map((item) => <button key={item} className={category === item ? 'category-chip selected' : 'category-chip'} onClick={() => setCategory(item)}>{item}</button>)}</div><span className="result-count">{totalProducts.toLocaleString()} {totalProducts === 1 ? 'piece' : 'pieces'}</span></div>
              {catalogLoading && !catalogLoaded ? <div className="empty-state"><h3>Finding your good things…</h3></div> : !catalogLoaded ? <div className="empty-state"><div className="empty-icon">✳</div><h3>The shelves are taking a breath.</h3><p>We couldn’t load products right now. Check that the ShopSphere services are running, then try again.</p><button className="outline-button" onClick={() => void loadProducts(0, false, category, search).catch((requestError) => setError(messageFor(requestError)))}>Try again</button></div> : visibleProducts.length === 0 ? <div className="empty-state compact"><h3>No matching finds yet.</h3><p>Try another search or category.</p><button className="text-button" onClick={() => { setSearch(''); setCategory('All'); }}>Clear filters</button></div> : <><div className="product-grid">{displayedProducts.map((product, index) => {
                const shadeFamily = productShadeFamily(product.name);
                const shadeIndex = selectedShades[product.id] ?? 0;
                const imageSrc = productImage(product.name, product.category, shadeIndex, product.imageUrl);
                return <article className="product-card" key={product.id}>
                <div className={`product-visual visual-${index % 4}`}>
                  {!failedImages.has(product.id)
                    ? <button className="product-open-image" onClick={() => openProduct(product)} aria-label={`View ${product.name} details`}><img src={imageSrc} alt={`${product.name} product photo${shadeFamily ? ` in ${SHADE_NAMES[shadeIndex]} shade` : ''}`} loading="lazy" onError={() => setFailedImages((current) => new Set(current).add(product.id))} /></button>
                    : <div className="product-placeholder" role="img" aria-label={`${product.name} image unavailable`}><span className="placeholder-orbit" /><span className="placeholder-initial">{product.name.slice(0, 1).toUpperCase()}</span><span className="placeholder-category">{product.category}</span></div>}
                  <span className="product-tag">{product.category}</span>
                  <button type="button" className="quick-add" disabled={busy || product.quantity <= 0} onClick={() => void addToCart(product)} aria-label={`Add ${product.name} to bag`}>{product.quantity <= 0 ? 'Sold out' : '＋'}</button>
                  {shadeFamily && <div className="product-shades" role="group" aria-label={`${product.name} shades`}>{SHADE_NAMES.map((shade, shadeOption) => <button key={shade} type="button" className={`shade-swatch shade-${shade.toLowerCase()}${shadeOption === shadeIndex ? ' selected' : ''}`} title={shade} aria-label={`${product.name}, ${shade}`} aria-pressed={shadeOption === shadeIndex} onClick={() => setSelectedShades((current) => ({ ...current, [product.id]: shadeOption }))} />)}</div>}
                </div>
                <div className="product-info"><div><h3><button className="product-title-link" onClick={() => openProduct(product)}>{product.name}</button></h3><p>{product.description || 'A considered little addition to your everyday.'}</p></div><span className="product-price">{money(product.price)}</span></div>
                <div className="stock-note">{product.quantity > 0 ? 'Availability confirmed at checkout' : 'Currently unavailable'}</div>
              </article>;
              })}</div>{catalogTotalPages > 1 && <nav className="catalog-pagination" aria-label="Product pages">
                <div className="pagination-controls">
                  <button type="button" className="page-arrow" aria-label="Previous page" disabled={catalogLoading || catalogPage === 0} onClick={() => void loadProducts(catalogPage - 1, false, category, search).catch((requestError) => setError(messageFor(requestError)))}>←</button>
                  {pageControls.map((pageNumber) => typeof pageNumber === 'number'
                    ? <button type="button" key={pageNumber} className={`page-number${catalogPage === pageNumber ? ' active' : ''}`} aria-label={`Page ${pageNumber + 1}`} aria-current={catalogPage === pageNumber ? 'page' : undefined} disabled={catalogLoading} onClick={() => void loadProducts(pageNumber, false, category, search).catch((requestError) => setError(messageFor(requestError)))}>{pageNumber + 1}</button>
                    : <span className="page-ellipsis" key={pageNumber} aria-hidden="true">…</span>)}
                  <button type="button" className="page-arrow" aria-label="Next page" disabled={catalogLoading || catalogPage >= catalogTotalPages - 1} onClick={() => void loadProducts(catalogPage + 1, false, category, search).catch((requestError) => setError(messageFor(requestError)))}>→</button>
                </div>
              </nav>}</>}
            </section>
          </> : page === 'orders' ? <section className="orders-section">
            <div className="orders-heading"><div><p className="eyebrow">YOUR SHOPPING HISTORY</p><h1>Your orders<span>.</span></h1></div><button className="outline-button" onClick={() => setPage('shop')}>Continue shopping <span>↗</span></button></div>
            {orders.length === 0 ? <div className="empty-state"><div className="empty-icon">◌</div><h3>Your story starts here.</h3><p>When you place an order, you’ll find the details right here.</p><button className="primary-button" onClick={() => setPage('shop')}>Browse the collection <span>↗</span></button></div> : <div className="order-list">{orders.map((order) => <article className="order-card" key={order.id}>
              <div className="order-card-head"><div><span className="order-number">ORDER #{order.id}</span><span className="order-date">{new Date(order.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })}</span></div><span className={`status-pill status-${order.status.toLowerCase()}`}>{order.status.toLowerCase()}</span></div>
              <div className="order-items">{order.items?.map((item, index) => <div className="order-item" key={`${item.productId}-${index}`}><span className="order-item-symbol">✳</span><span className="order-item-name">{products.find((product) => product.id === item.productId)?.name ?? `Product #${item.productId}`}<small>Qty {item.quantity}</small></span><span>{money(item.subtotal)}</span></div>)}</div>
              <div className="order-card-foot"><span>Total <b>{money(order.totalAmount)}</b></span>{order.status === 'PENDING' && <button className="cancel-button" disabled={busy} onClick={() => void cancelOrder(order)}>Cancel order</button>}</div>
            </article>)}</div>}
          </section> : page === 'admin' && isAdmin ? <section className="admin-section">
            <div className="orders-heading"><div><p className="eyebrow">STORE MANAGEMENT</p><h1>Manage products<span>.</span></h1></div><span className="admin-role-pill">Administrator</span></div>
            <div className="admin-panel"><div><p className="eyebrow">ADD TO THE COLLECTION</p><h2>List a product</h2><p className="muted">Add the product details and starting stock. Customers can see it as soon as it is saved.</p></div>
              <form className="admin-product-form" onSubmit={(event) => void createProduct(event)}>
                <label>Product name<input name="name" required maxLength={160} placeholder="Wireless keyboard" /></label>
                <label>Category<input name="category" required maxLength={80} placeholder="Electronics" list="product-categories" />
                  <datalist id="product-categories">{catalogCategories.map((item) => <option key={item} value={item} />)}</datalist>
                </label>
                <label>Price (INR)<input name="price" type="number" required min="0.01" step="0.01" placeholder="1499.00" /></label>
                <label>Starting stock<input name="quantity" type="number" required min="0" step="1" placeholder="20" /></label>
                <label className="admin-form-wide">Description<textarea name="description" maxLength={2000} rows={4} placeholder="What makes this a useful everyday find?" /></label>
                <label className="admin-form-wide">Product image URL <span className="muted">(optional)</span><input name="imageUrl" type="url" placeholder="https://…" /></label>
                <button className="primary-button" type="submit" disabled={busy}>{busy ? 'Saving product…' : 'Add product and stock'}<span>↗</span></button>
              </form>
            </div>
          </section> : <section className="product-detail-section">
            {!selectedProduct ? <div className="empty-state"><h3>{products.length ? 'We couldn’t find this product.' : 'Loading this product…'}</h3>{products.length > 0 && <button className="outline-button" onClick={returnToShop}>Return to the shop</button>}</div> : <>
              <button className="text-button product-back-link" onClick={returnToShop}>← Back to the shop</button>
              <div className="product-detail-layout">
                <div className="product-detail-image"><img src={productImage(selectedProduct.name, selectedProduct.category, selectedShades[selectedProduct.id] ?? 0, selectedProduct.imageUrl)} alt={selectedProduct.name} /></div>
                <div className="product-detail-copy"><p className="eyebrow">{selectedProduct.category}</p><h1>{selectedProduct.name}</h1><p className="product-detail-price">{money(selectedProduct.price)}</p>
                  <div className="detail-rating" aria-label={`${productReviews.length ? (productReviews.reduce((sum, review) => sum + review.rating, 0) / productReviews.length).toFixed(1) : 'No'} out of 5 stars`}><span>{productReviews.length ? '★'.repeat(Math.round(productReviews.reduce((sum, review) => sum + review.rating, 0) / productReviews.length)) : '☆☆☆☆☆'}</span> {productReviews.length ? `${(productReviews.reduce((sum, review) => sum + review.rating, 0) / productReviews.length).toFixed(1)} · ${productReviews.length} ${productReviews.length === 1 ? 'review' : 'reviews'}` : 'No reviews yet'}</div>
                  <p className="product-detail-description">{selectedProduct.description || 'A considered little addition to your everyday.'}</p>
                  {productShadeFamily(selectedProduct.name) && <div className="detail-shades"><span>Shade: <b>{SHADE_NAMES[selectedShades[selectedProduct.id] ?? 0]}</b></span><div className="product-shades" role="group" aria-label="Choose a product shade">{SHADE_NAMES.map((shade, shadeOption) => <button key={shade} type="button" className={`shade-swatch shade-${shade.toLowerCase()}${shadeOption === (selectedShades[selectedProduct.id] ?? 0) ? ' selected' : ''}`} title={shade} aria-label={`Select ${shade}`} aria-pressed={shadeOption === (selectedShades[selectedProduct.id] ?? 0)} onClick={() => setSelectedShades((current) => ({ ...current, [selectedProduct.id]: shadeOption }))} />)}</div></div>}
                  <p className={selectedProduct.quantity > 0 ? 'detail-stock available' : 'detail-stock unavailable'}>{selectedProduct.quantity > 0 ? 'In stock · ready to order' : 'Out of stock'}</p>
                  <div className="detail-actions"><button type="button" className="outline-button" disabled={busy || selectedProduct.quantity <= 0} onClick={() => void addToCart(selectedProduct)}>Add to bag</button><button type="button" className="primary-button" disabled={busy || selectedProduct.quantity <= 0} onClick={() => startBuyNow(selectedProduct)}>Buy now <span>↗</span></button></div>
                </div>
              </div>
              <section className="detail-reviews"><div className="detail-section-heading"><div><p className="eyebrow">CUSTOMER NOTES</p><h2>Reviews<span>.</span></h2></div><span>{productReviews.length} {productReviews.length === 1 ? 'review' : 'reviews'}</span></div>
                <form className="review-form" onSubmit={(event) => {
                  event.preventDefault();
                  if (!hasSession()) { setAuthMode('login'); setShowAuth(true); setMessage('Sign in to share your review.'); return; }
                  if (!reviewComment.trim()) { setError('Write a few words before submitting your review.'); return; }
                  setReviewBusy(true); setError('');
                  void api<ProductReview>(`/api/products/${selectedProduct.id}/reviews`, { method: 'POST', body: JSON.stringify({ rating: reviewRating, comment: reviewComment }) })
                    .then((review) => { setProductReviews((current) => [review, ...current]); setReviewComment(''); setMessage('Thanks for sharing your review.'); })
                    .catch((reviewError) => setError(messageFor(reviewError)))
                    .finally(() => setReviewBusy(false));
                }}>
                  <label>Your rating<select value={reviewRating} onChange={(event) => setReviewRating(Number(event.target.value))}>{[5, 4, 3, 2, 1].map((rating) => <option key={rating} value={rating}>{'★'.repeat(rating)} ({rating})</option>)}</select></label>
                  <label>Your review<textarea value={reviewComment} onChange={(event) => setReviewComment(event.target.value)} maxLength={1000} placeholder="What should other shoppers know?" rows={4} /></label>
                  <button className="outline-button" disabled={reviewBusy}>{reviewBusy ? 'Submitting…' : authenticated ? 'Submit review' : 'Sign in to review'}</button>
                </form>
                <div className="review-list">{productReviews.map((review) => <article className="review-card" key={review.id}><div><b>{review.reviewerName}</b><span>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span></div><time>{new Date(review.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</time><p>{review.comment}</p></article>)}{productReviews.length === 0 && <p className="no-reviews">No reviews yet. Be the first to share your thoughts.</p>}</div>
              </section>
              <section className="related-products"><div className="detail-section-heading"><div><p className="eyebrow">A FEW MORE FINDS</p><h2>Related pieces<span>.</span></h2></div></div><div className="related-grid">{relatedProducts.map((product) => <button className="related-card" key={product.id} onClick={() => openProduct(product)}><img src={productImage(product.name, product.category, 0, product.imageUrl)} alt="" /><span>{product.name}</span><b>{money(product.price)}</b></button>)}</div></section>
            </>}
          </section>}
      </main>

      <footer className="site-footer"><a className="brand footer-brand" href="#shop" onClick={() => setPage('shop')}><span className="brand-mark">s</span><span>shopsphere<span className="brand-dot">.</span></span></a><span>Shopping, with a little more intention.</span><span>© {new Date().getFullYear()} ShopSphere</span></footer>

      {buyNowProductId !== null && products.find((product) => product.id === buyNowProductId) && (() => {
        const product = products.find((item) => item.id === buyNowProductId)!;
        return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) setBuyNowProductId(null); }}><section className="auth-card modal-card buy-now-card" aria-labelledby="buy-now-title">
          <button className="close-button modal-close" type="button" disabled={busy} onClick={() => setBuyNowProductId(null)} aria-label="Close purchase review">×</button>
          <p className="eyebrow">REVIEW YOUR PURCHASE</p><h2 id="buy-now-title">Buy this item</h2>
          <div className="buy-now-item"><img src={productImage(product.name, product.category, selectedShades[product.id] ?? 0, product.imageUrl)} alt="" /><div><b>{product.name}</b><span>Quantity 1</span></div><strong>{money(product.price)}</strong></div>
          <label className="payment-select buy-now-payment">Payment method<select required value={buyNowPaymentMethod} onChange={(event) => setBuyNowPaymentMethod(event.target.value as typeof buyNowPaymentMethod)}><option value="" disabled>Choose a payment method</option>{paymentMethods.map((method) => <option key={method} value={method}>{method}</option>)}</select></label>
          <div className="demo-note"><span>ⓘ</span><span>Simulated payment only. No payment details are collected.</span></div>
          <button className="primary-button full-button" type="button" disabled={busy || !buyNowPaymentMethod} onClick={() => void confirmBuyNow(product)}>{busy ? 'Processing…' : 'Confirm and place order'}<span>↗</span></button>
          <button className="outline-button full-button buy-now-cancel" type="button" disabled={busy} onClick={() => setBuyNowProductId(null)}>Continue shopping</button>
        </section></div>;
      })()}

      {cartOpen && <div className="drawer-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setCartOpen(false); }}><aside className="cart-drawer" aria-label="Shopping bag">
        <div className="drawer-header"><div><p className="eyebrow">YOUR GOOD THINGS</p><h2>Your bag <span>({cartCount})</span></h2></div><button className="close-button" onClick={() => setCartOpen(false)} aria-label="Close bag">×</button></div>
        {cart.items.length === 0 ? <div className="drawer-empty"><div className="empty-icon">♧</div><h3>Your bag is taking a little pause.</h3><p>Find something lovely to bring home.</p><button className="primary-button" onClick={() => setCartOpen(false)}>Keep exploring</button></div> : <>
          <div className="drawer-items">{cart.items.map((item) => {
            const product = products.find((candidate) => candidate.id === item.productId);
            return <div className="drawer-item" key={item.productId}><div className="drawer-item-art"><img src={product ? productImage(product.name, product.category, selectedShades[product.id] ?? 0, product.imageUrl) : '/product-images/catalog-07.jpg'} alt="" /></div><div className="drawer-item-details"><h3>{item.productName}</h3><span>{money(item.unitPrice)}</span><div className="quantity-control"><button aria-label={`Decrease ${item.productName} quantity`} onClick={() => void updateQuantity(item, item.quantity - 1)}>−</button><span>{item.quantity}</span><button aria-label={`Increase ${item.productName} quantity`} onClick={() => void updateQuantity(item, item.quantity + 1)}>＋</button></div></div><div className="drawer-item-end"><b>{money(item.subtotal)}</b><button className="remove-link" onClick={() => void updateQuantity(item, 0)}>Remove</button></div></div>;
          })}</div>
          {authenticated && readGuestCart().length > 0 && <button className="outline-button full-button" onClick={() => void loadCart().catch((requestError) => setError(messageFor(requestError)))}>Retry syncing saved bag items</button>}
        <div className="checkout-box"><div className="subtotal-row"><span>Subtotal</span><b>{money(cart.total)}</b></div><p className="checkout-note">Delivery and final availability are confirmed at checkout.</p><label className="payment-select">Payment method<select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as typeof paymentMethods[number])}>{paymentMethods.map((method) => <option key={method} value={method}>{method}</option>)}</select></label><div className="demo-note"><span>ⓘ</span><span>Simulated payment only. No card, UPI, or wallet details are collected.</span></div><button className="primary-button full-button checkout-button" disabled={busy || cart.items.length === 0} onClick={() => void checkout()}>{busy ? 'Confirming your order…' : authenticated ? 'Place order' : 'Sign in to continue'}<span>↗</span></button><p className="secure-note">{authenticated ? 'Payment is simulated by ShopSphere for this demo.' : 'Your bag is saved when you sign in or create an account.'}</p></div>
        </>}
      </aside></div>}

      {showAuth && !authenticated && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowAuth(false); }}><section className="auth-card modal-card" aria-labelledby="auth-title">
        <button className="close-button modal-close" onClick={() => { setShowAuth(false); setCheckoutPending(false); }} aria-label="Close sign in">×</button>
        <div className="auth-card-top"><span className="tiny-mark">s</span><span>YOUR SHOPPING SPACE</span></div>
        <h2 id="auth-title">{authMode === 'login' ? 'Welcome back' : authMode === 'register' ? 'Create your account' : 'Set up ShopSphere'}</h2>
        <p className="muted">{authMode === 'admin-setup' ? 'Create the first administrator account for this store.' : checkoutPending ? 'Sign in to keep your bag and continue checkout.' : authMode === 'login' ? 'Sign in to save your bag and view orders.' : 'A few details and you’re in.'}</p>
        <form onSubmit={handleAuth} className="auth-form">
          {authMode !== 'login' && <label>Your name<input name="name" autoComplete="name" required placeholder="Avery Parker" /></label>}
          <label>Email address<input name="email" type="email" autoComplete="email" required placeholder="you@example.com" /></label>
          <label>{authMode === 'admin-setup' ? 'Account password' : 'Password'}<input name="password" type="password" minLength={8} autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} required placeholder="At least 8 characters" /></label>
          {authMode === 'admin-setup' && <><label>One-time setup code<input name="setupCode" type="password" autoComplete="off" minLength={32} maxLength={256} required placeholder="Provided by the store owner" /></label><p className="privacy-note">The setup code is checked securely by ShopSphere and setup closes after the first admin is created. If you use this email already, enter your current password.</p></>}
          <button className="primary-button full-button" type="submit" disabled={busy}>{busy ? 'Please wait…' : authMode === 'login' ? 'Sign in' : authMode === 'register' ? 'Create account' : 'Create administrator'}<span aria-hidden="true">↗</span></button>
        </form>
        {authMode === 'login' && <div className="google-signin-area">
          <div className="auth-divider"><span>OR</span></div>
          {googleClientId
            ? <div className="google-signin-slot" ref={googleButtonRef} />
            : <button className="google-not-configured" type="button" disabled title="Set VITE_GOOGLE_CLIENT_ID to enable Google sign-in">Continue with Google</button>}
          {!googleClientId && <p className="privacy-note">Google sign-in needs a Google OAuth client ID to be configured.</p>}
        </div>}
        <p className="auth-switch">{authMode === 'login' ? 'New to ShopSphere?' : authMode === 'register' ? 'Already have an account?' : 'Already have a ShopSphere account?'} <button onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setError(''); setMessage(''); }}>{authMode === 'login' ? 'Create account' : 'Sign in'}</button></p>
        {authMode === 'login' && adminSetupAvailable && <p className="auth-switch">Setting up this store? <button onClick={() => { setAuthMode('admin-setup'); setError(''); setMessage(''); }}>Create the first admin account</button></p>}
        {authMode !== 'admin-setup' && <p className="privacy-note">Your guest bag will be available after sign-in.</p>}
      </section></div>}
      {showAuth && authenticated && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowAuth(false); }}><section className="auth-card modal-card"><button className="close-button modal-close" onClick={() => setShowAuth(false)} aria-label="Close account">×</button><h2>Account</h2><p className="muted">You’re signed in to ShopSphere.</p><button className="outline-button full-button" onClick={() => void logout()}>Sign out</button></section></div>}
    </div>
  );
}

function messageFor(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in again to continue.';
    if (error.status === 403) return 'This action isn’t available for your account.';
    if (error.status === 404) return 'We couldn’t find that item.';
    if (error.status === 409 && error.message.toLowerCase().includes('inventory is insufficient')) {
      return 'An item in your bag is out of stock. Remove it and try again.';
    }
    if (error.status === 409 && error.message.toLowerCase().includes('belongs to a failed request')) {
      return 'This checkout attempt failed. Your bag is unchanged; try placing the order again.';
    }
    if (error.status === 409) return 'This request is already being processed. Refresh your orders in a moment.';
    if (error.status === 402) return 'Payment was declined. Your order was cancelled and reserved stock is being returned.';
    if (error.status === 503) return 'ShopSphere is briefly unavailable. Your bag is saved; please try again in a moment.';
    if (error.status >= 500) return 'ShopSphere had a temporary problem. Your bag is saved; please try again shortly.';
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return 'We couldn’t reach ShopSphere. Check your connection and try again.';
}

export default App;
