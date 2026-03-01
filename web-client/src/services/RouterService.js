const NAVIGATION_EVENT = "app:navigate";

export class RouterService {
  currentLocation() {
    return {
      pathname: window.location.pathname,
      search: window.location.search,
    };
  }

  navigate(pathWithQuery) {
    window.history.pushState({}, "", pathWithQuery);
    window.dispatchEvent(new Event(NAVIGATION_EVENT));
  }

  replace(pathWithQuery) {
    window.history.replaceState({}, "", pathWithQuery);
    window.dispatchEvent(new Event(NAVIGATION_EVENT));
  }

  redirect(pathOrUrl) {
    window.location.assign(pathOrUrl);
  }

  resolveSameOriginPath(value) {
    if (!value || !String(value).trim()) {
      return null;
    }

    try {
      const resolved = new URL(value, window.location.origin);
      if (resolved.origin !== window.location.origin) {
        return null;
      }
      return `${resolved.pathname}${resolved.search}${resolved.hash}`;
    } catch (error) {
      return null;
    }
  }

  readQuery(search) {
    return new URLSearchParams(search || "");
  }

  subscribe(listener) {
    const handler = () => listener(this.currentLocation());
    window.addEventListener("popstate", handler);
    window.addEventListener(NAVIGATION_EVENT, handler);
    return () => {
      window.removeEventListener("popstate", handler);
      window.removeEventListener(NAVIGATION_EVENT, handler);
    };
  }
}

export const routerService = new RouterService();
