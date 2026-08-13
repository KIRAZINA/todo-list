import { useEffect, useState } from "react";
import { api, getToken, setToken, decodeJwtPayload } from "./api";
import AuthScreen from "./components/AuthScreen";
import TaskBoard from "./components/TaskBoard";
import AdminPanel from "./components/AdminPanel";
import ProfilePanel from "./components/ProfilePanel";
import StatusBar from "./components/StatusBar";

function sessionFromToken() {
  const token = getToken();
  if (!token) return null;
  const payload = decodeJwtPayload(token);
  if (!payload) return null;
  return {
    username: payload.sub || payload.username,
    role: Array.isArray(payload.role) ? payload.role[0] : payload.role,
  };
}

export default function App() {
  const [session, setSession] = useState(sessionFromToken);
  const [view, setView] = useState("tasks"); // "tasks" | "admin" | "profile"

  useEffect(() => {
    const onAuthExpired = () => {
      setSession(null);
      setView("tasks");
    };
    window.addEventListener("todo:auth-expired", onAuthExpired);
    return () => window.removeEventListener("todo:auth-expired", onAuthExpired);
  }, []);

  const isAdmin = session?.role === "ADMIN";

  function handleLogout() {
    // Best-effort server-side revocation (invalidates the JWT), then always
    // drop the local token so the user is logged out client-side regardless.
    api.logout().catch(() => {});
    setToken(null);
    setSession(null);
    setView("tasks");
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <span className="brand">
          <strong>todo</strong> // api console
        </span>
        {session && (
          <nav className="topbar-nav" aria-label="Primary">
            <button
              className={`nav-btn${view === "tasks" ? " active" : ""}`}
              onClick={() => setView("tasks")}
            >
              Tasks
            </button>
            <button
              className={`nav-btn${view === "profile" ? " active" : ""}`}
              onClick={() => setView("profile")}
            >
              Profile
            </button>
            {isAdmin && (
              <button
                className={`nav-btn${view === "admin" ? " active" : ""}`}
                onClick={() => setView("admin")}
              >
                Admin
              </button>
            )}
          </nav>
        )}
        {session && (
          <div className="session">
            <span>{session.username}</span>
            {session.role && <span className="role-pill">{session.role}</span>}
            <button className="logout-btn" onClick={handleLogout}>
              Log out
            </button>
          </div>
        )}
      </header>

      {session ? (
        <main>
          {view === "admin" ? (
            <AdminPanel session={session} />
          ) : view === "profile" ? (
            <ProfilePanel />
          ) : (
            <TaskBoard />
          )}
        </main>
      ) : (
        <AuthScreen onAuthenticated={() => setSession(sessionFromToken())} />
      )}

      <StatusBar />
    </div>
  );
}