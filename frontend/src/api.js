const BASE = "/api";

// Decodes the JWT payload for display only (e.g. showing username/role in
// the topbar). This is NOT signature verification and must never be used
// for authorization decisions — the backend is the only source of truth
// for that.
export function decodeJwtPayload(jwt) {
  try {
    const payload = jwt.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

let token = localStorage.getItem("todo_token") || null;

export function setToken(newToken) {
  token = newToken;
  if (newToken) {
    localStorage.setItem("todo_token", newToken);
  } else {
    localStorage.removeItem("todo_token");
  }
}

export function getToken() {
  return token;
}

async function request(path, { method = "GET", body, auth = true } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (auth && token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  // 204 No Content (task delete)
  if (res.status === 204) return null;

  let data = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { error: text };
    }
  }

  if (!res.ok) {
    const message =
      data?.error ||
      (data?.errors && Object.values(data.errors).join(", ")) ||
      `Request failed (${res.status})`;
    throw new Error(message);
  }

  return data;
}

export const api = {
  register: (username, password, email) =>
    request("/auth/register", {
      method: "POST",
      auth: false,
      body: { username, password, email },
    }),

  login: (username, password) =>
    request("/auth/login", {
      method: "POST",
      auth: false,
      body: { username, password },
    }),

  logout: () => request("/auth/logout", { method: "POST" }),

  health: () => request("/test/health", { auth: false }),

  listTasks: (page = 0, size = 20, status, priority, overdue) => {
    const params = new URLSearchParams({ page, size });
    if (status) params.append("status", status);
    if (priority) params.append("priority", priority);
    if (overdue) params.append("overdue", "true");
    return request(`/tasks?${params}`);
  },

  createTask: (task) => request("/tasks", { method: "POST", body: task }),

  updateTask: (id, patch) => request(`/tasks/${id}`, { method: "PATCH", body: patch }),

  deleteTask: (id) => request(`/tasks/${id}`, { method: "DELETE" }),
};