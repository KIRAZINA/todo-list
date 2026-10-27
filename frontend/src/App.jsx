import { useEffect, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate, Link } from "react-router-dom";
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

function ProtectedRoute({ session, children, adminOnly = false }) {
  if (!session) return <Navigate to="/login" replace />;
  if (adminOnly && session.role !== "ADMIN") return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  const [session, setSession] = useState(sessionFromToken);

  useEffect(() => {
    const onAuthExpired = () => {
      setToken(null);
      setSession(null);
    };
    window.addEventListener("todo:auth-expired", onAuthExpired);
    return () => window.removeEventListener("todo:auth-expired", onAuthExpired);
  }, []);

  function handleLogout() {
    api.logout().catch(() => {});
    setToken(null);
    setSession(null);
  }

  const isAdmin = session?.role === "ADMIN";

  return (
    <BrowserRouter>
      <div className="app-shell">
        <header className="topbar">
          <Link to="/" className="brand">
            <strong>todo</strong> // api console
          </Link>
          {session && (
            <nav className="topbar-nav" aria-label="Primary">
              <Link className="nav-btn" to="/">Tasks</Link>
              <Link className="nav-btn" to="/profile">Profile</Link>
              {isAdmin && <Link className="nav-btn" to="/admin">Admin</Link>}
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

        <main>
          <Routes>
            <Route
              path="/login"
              element={<AuthScreen onAuthenticated={() => setSession(sessionFromToken())} />}
            />
            <Route
              path="/register"
              element={<AuthScreen onAuthenticated={() => setSession(sessionFromToken())} />}
            />
            <Route
              path="/"
              element={
                <ProtectedRoute session={session}>
                  <TaskBoard />
                </ProtectedRoute>
              }
            />
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute session={session}>
                  <TaskBoard />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute session={session}>
                  <ProfilePanel />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute session={session} adminOnly>
                  <AdminPanel session={session} />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to={session ? "/" : "/login"} replace />} />
          </Routes>
        </main>
        <StatusBar />
      </div>
    </BrowserRouter>
  );
}
