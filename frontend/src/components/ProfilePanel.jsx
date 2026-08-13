import { useEffect, useState, useCallback } from "react";
import { api } from "../api";

export default function ProfilePanel() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);

  const [email, setEmail] = useState("");
  const [emailBusy, setEmailBusy] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordBusy, setPasswordBusy] = useState(false);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.getMe();
      setProfile(res);
      setEmail(res.email);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  async function handleEmailSubmit(e) {
    e.preventDefault();
    if (!email.trim()) return;
    setEmailBusy(true);
    setError(null);
    setMessage(null);
    try {
      const res = await api.updateEmail(email.trim());
      setProfile(res);
      setMessage("Email updated.");
    } catch (err) {
      setError(err.message);
    } finally {
      setEmailBusy(false);
    }
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    if (newPassword !== confirmPassword) {
      setError("New passwords do not match.");
      return;
    }
    setPasswordBusy(true);
    try {
      await api.changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setMessage("Password updated. Use your new password on next login.");
    } catch (err) {
      setError(err.message);
    } finally {
      setPasswordBusy(false);
    }
  }

  if (loading) {
    return (
      <div className="empty-state">
        <div className="glyph">···</div>
        Loading profile…
      </div>
    );
  }

  return (
    <>
      <div className="board-header">
        <h2>Profile</h2>
        <span className="count">Your account details and security</span>
      </div>

      {error && <div className="error-banner">{error}</div>}
      {message && <div className="success-banner">{message}</div>}

      {profile && (
        <section className="panel-card">
          <h3>Account</h3>
          <div className="profile-grid">
            <div>
              <span className="profile-label">Username</span>
              <span className="profile-value">{profile.username}</span>
            </div>
            <div>
              <span className="profile-label">Role</span>
              <span className="profile-value">
                <span className="role-pill">{profile.role}</span>
              </span>
            </div>
            <div>
              <span className="profile-label">Email</span>
              <span className="profile-value">{profile.email}</span>
            </div>
            <div>
              <span className="profile-label">Member since</span>
              <span className="profile-value">
                {new Date(profile.createdAt).toLocaleDateString(undefined, {
                  year: "numeric",
                  month: "short",
                  day: "numeric",
                })}
              </span>
            </div>
          </div>
        </section>
      )}

      <section className="panel-card">
        <h3>Change email</h3>
        <form onSubmit={handleEmailSubmit}>
          <div className="field">
            <label htmlFor="profile-email">New email</label>
            <input
              id="profile-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <button className="btn" type="submit" disabled={emailBusy}>
            {emailBusy ? "…" : "Save email"}
          </button>
        </form>
      </section>

      <section className="panel-card">
        <h3>Change password</h3>
        <form onSubmit={handlePasswordSubmit}>
          <div className="field">
            <label htmlFor="profile-current">Current password</label>
            <input
              id="profile-current"
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="profile-new">New password</label>
            <input
              id="profile-new"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
            <span className="field-hint">
              8–100 characters with at least one lowercase letter, one uppercase
              letter, and one digit.
            </span>
          </div>
          <div className="field">
            <label htmlFor="profile-confirm">Confirm new password</label>
            <input
              id="profile-confirm"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
          </div>
          <button className="btn" type="submit" disabled={passwordBusy}>
            {passwordBusy ? "…" : "Update password"}
          </button>
        </form>
      </section>
    </>
  );
}
