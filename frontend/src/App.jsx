import { useState } from "react";
import { api, getToken, setToken, decodeJwtPayload } from "./api";
import AuthScreen from "./components/AuthScreen";
import TaskBoard from "./components/TaskBoard";
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

  function handleLogout() {
    // Best-effort server-side revocation (invalidates the JWT), then always
    // drop the local token so the user is logged out client-side regardless.
    api.logout().catch(() => {});
    setToken(null);
    setSession(null);
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <span className="brand">
          <strong>todo</strong> // api console
        </span>
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
          <TaskBoard />
        </main>
      ) : (
        <AuthScreen onAuthenticated={() => setSession(sessionFromToken())} />
      )}

      <StatusBar />
    </div>
  );
}