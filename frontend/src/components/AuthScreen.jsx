import { useState } from "react";
import { api, setToken } from "../api";

export default function AuthScreen({ onAuthenticated }) {
  const [mode, setMode] = useState("login"); // "login" | "register"
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "register") {
        await api.register(username, password, email);
        // Auto-login right after registering so the tester doesn't
        // have to re-type credentials.
        const res = await api.login(username, password);
        setToken(res.token);
      } else {
        const res = await api.login(username, password);
        setToken(res.token);
      }
      onAuthenticated();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <p className="auth-eyebrow">todo // api console</p>
        <h1>{mode === "login" ? "Sign in" : "Create an account"}</h1>

        {error && <div className="error-banner">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
              minLength={3}
              maxLength={50}
            />
          </div>

          {mode === "register" && (
            <div className="field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </div>
          )}

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required
              minLength={8}
              maxLength={100}
              pattern={mode === "register" ? "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}" : undefined}
              title="At least 8 characters, with one lowercase letter, one uppercase letter, and one digit"
            />
            {mode === "register" && (
              <small className="field-hint">
                At least 8 characters, with one lowercase letter, one uppercase letter, and one digit.
              </small>
            )}
          </div>

          <button className="btn" type="submit" disabled={busy} style={{ width: "100%" }}>
            {busy ? "Working…" : mode === "login" ? "Sign in" : "Register"}
          </button>
        </form>

        <div className="auth-switch">
          {mode === "login" ? (
            <>
              No account yet?{" "}
              <button type="button" onClick={() => { setMode("register"); setError(null); }}>
                Register instead
              </button>
            </>
          ) : (
            <>
              Already have an account?{" "}
              <button type="button" onClick={() => { setMode("login"); setError(null); }}>
                Sign in instead
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}