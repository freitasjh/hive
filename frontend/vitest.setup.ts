if (typeof globalThis.localStorage === 'undefined') {
  const store: Record<string, string> = {}
  // @ts-ignore
  globalThis.localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v },
    removeItem: (k: string) => { delete store[k] },
    clear: () => { Object.keys(store).forEach(k => delete store[k]) },
    length: 0,
    key: () => null
  }
}

// @ts-ignore
if (typeof window !== 'undefined' && !window.matchMedia) {
  // @ts-ignore
  window.matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })
}
// @ts-ignore
if (typeof globalThis.matchMedia === 'undefined' && typeof window !== 'undefined' && window.matchMedia) {
  // @ts-ignore
  globalThis.matchMedia = window.matchMedia
}
// @ts-ignore
if (typeof globalThis.ResizeObserver === 'undefined') {
  // @ts-ignore
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
// @ts-ignore
if (typeof window !== 'undefined' && typeof window.ResizeObserver === 'undefined') {
  // @ts-ignore
  window.ResizeObserver = globalThis.ResizeObserver
}
