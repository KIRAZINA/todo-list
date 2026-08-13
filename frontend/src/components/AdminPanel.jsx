import { useEffect, useState, useCallback } from "react";
import { api } from "../api";
import TaskFilters from "./TaskFilters";

const PAGE_SIZE = 10;
const ROLES = ["USER", "ADMIN"];

function formatDateTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function AdminPanel({ session }) {
  const isAdmin = session?.role === "ADMIN";
  const ownUsername = session?.username;

  const [tab, setTab] = useState("users"); // "users" | "tasks"

  const [usersPage, setUsersPage] = useState(0);
  const [usersData, setUsersData] = useState(null);
  const [usersLoading, setUsersLoading] = useState(true);

  const [tasksPage, setTasksPage] = useState(0);
  const [tasksData, setTasksData] = useState(null);
  const [tasksLoading, setTasksLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState(null);
  const [priorityFilter, setPriorityFilter] = useState(null);
  const [overdueFilter, setOverdueFilter] = useState(false);
  const [dueFrom, setDueFrom] = useState("");
  const [dueTo, setDueTo] = useState("");
  const [sortBy, setSortBy] = useState("createdAt");
  const [direction, setDirection] = useState("desc");

  const [error, setError] = useState(null);
  const [busyIds, setBusyIds] = useState(() => new Set());

  const loadUsers = useCallback(async (p) => {
    setUsersLoading(true);
    setError(null);
    try {
      const res = await api.listUsers(p, PAGE_SIZE);
      setUsersData(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setUsersLoading(false);
    }
  }, []);

  const loadTasks = useCallback(
    async (p, status, prio, overdue, from, to, sort, dir) => {
      setTasksLoading(true);
      setError(null);
      try {
        const res = await api.listAllTasks(p, PAGE_SIZE, status, prio, overdue, from, to, sort, dir);
        setTasksData(res);
      } catch (err) {
        setError(err.message);
      } finally {
        setTasksLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    if (isAdmin) loadUsers(usersPage);
  }, [isAdmin, usersPage, loadUsers]);

  useEffect(() => {
    if (isAdmin && tab === "tasks") {
      loadTasks(
        tasksPage,
        statusFilter,
        priorityFilter,
        overdueFilter,
        dueFrom,
        dueTo,
        sortBy,
        direction
      );
    }
  }, [
    isAdmin,
    tab,
    tasksPage,
    statusFilter,
    priorityFilter,
    overdueFilter,
    dueFrom,
    dueTo,
    sortBy,
    direction,
    loadTasks,
  ]);

  function resetTasksPage() {
    setTasksPage(0);
  }

  function handleStatusChange(value) {
    resetTasksPage();
    setStatusFilter(value);
  }

  function handlePriorityChange(value) {
    resetTasksPage();
    setPriorityFilter(value);
  }

  function handleOverdueToggle() {
    resetTasksPage();
    setOverdueFilter((v) => !v);
  }

  function handleDueFromChange(value) {
    resetTasksPage();
    setDueFrom(value);
  }

  function handleDueToChange(value) {
    resetTasksPage();
    setDueTo(value);
  }

  function handleSortByChange(value) {
    resetTasksPage();
    setSortBy(value);
  }

  function handleDirectionToggle() {
    resetTasksPage();
    setDirection((d) => (d === "asc" ? "desc" : "asc"));
  }

  function markBusy(id, busy) {
    setBusyIds((prev) => {
      const next = new Set(prev);
      if (busy) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  async function handleRoleChange(user, role) {
    if (role === user.role) return;
    setError(null);
    markBusy(user.id, true);
    try {
      await api.updateUserRole(user.id, role);
      loadUsers(usersPage);
    } catch (err) {
      setError(err.message);
    } finally {
      markBusy(user.id, false);
    }
  }

  async function handleDeleteUser(user) {
    if (
      !confirm(
        `Delete user "${user.username}" and all their tasks? This cannot be undone.`
      )
    )
      return;
    setError(null);
    markBusy(user.id, true);
    try {
      await api.deleteUser(user.id);
      const onLastPage =
        usersData && usersData.content.length === 1 && usersPage > 0;
      if (onLastPage) {
        setUsersPage((p) => p - 1);
      } else {
        loadUsers(usersPage);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      markBusy(user.id, false);
    }
  }

  if (!isAdmin) {
    return (
      <div className="empty-state">
        <div className="glyph">⛔</div>
        Access denied — the ADMIN role is required to view this panel.
      </div>
    );
  }

  return (
    <>
      <div className="board-header">
        <h2>Admin panel</h2>
        <span className="count">Manage users and tasks across all accounts</span>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="admin-tabs" role="tablist" aria-label="Admin sections">
        <button
          className={`filter-btn${tab === "users" ? " active" : ""}`}
          role="tab"
          aria-selected={tab === "users"}
          onClick={() => setTab("users")}
        >
          Users
        </button>
        <button
          className={`filter-btn${tab === "tasks" ? " active" : ""}`}
          role="tab"
          aria-selected={tab === "tasks"}
          onClick={() => setTab("tasks")}
        >
          Tasks
        </button>
      </div>

      {tab === "users" ? (
        <>
          <div className="board-header">
            <h3>Users</h3>
            {usersData && (
              <span className="count">{usersData.totalElements} total</span>
            )}
          </div>

          {usersLoading && !usersData ? (
            <div className="empty-state">
              <div className="glyph">···</div>
              Loading users…
            </div>
          ) : usersData && usersData.content.length === 0 ? (
            <div className="empty-state">
              <div className="glyph">∅</div>
              No users found.
            </div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Username</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Created</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {usersData?.content.map((user) => {
                    const isSelf = user.username === ownUsername;
                    const busy = busyIds.has(user.id);
                    return (
                      <tr key={user.id}>
                        <td className="mono">#{user.id}</td>
                        <td>
                          {user.username}
                          {isSelf && <span className="self-badge">you</span>}
                        </td>
                        <td>{user.email}</td>
                        <td>
                          <select
                            className="role-select"
                            value={user.role}
                            disabled={isSelf || busy}
                            aria-label={`Role for ${user.username}`}
                            onChange={(e) =>
                              handleRoleChange(user, e.target.value)
                            }
                          >
                            {ROLES.map((r) => (
                              <option key={r} value={r}>
                                {r}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td>{formatDateTime(user.createdAt)}</td>
                        <td>
                          <button
                            className="btn-danger"
                            disabled={isSelf || busy}
                            onClick={() => handleDeleteUser(user)}
                          >
                            {busy ? "…" : "Delete"}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {usersData && usersData.totalPages > 1 && (
            <div className="pagination">
              <button
                disabled={usersData.first}
                onClick={() => setUsersPage((p) => p - 1)}
              >
                ← Prev
              </button>
              <span>
                page {usersData.number + 1} / {usersData.totalPages}
              </span>
              <button
                disabled={usersData.last}
                onClick={() => setUsersPage((p) => p + 1)}
              >
                Next →
              </button>
            </div>
          )}
        </>
      ) : (
        <>
          <div className="board-header">
            <h3>All tasks</h3>
            {tasksData && (
              <span className="count">{tasksData.totalElements} total</span>
            )}
          </div>

          <TaskFilters
            status={statusFilter}
            onStatusChange={handleStatusChange}
            priority={priorityFilter}
            onPriorityChange={handlePriorityChange}
            overdue={overdueFilter}
            onOverdueToggle={handleOverdueToggle}
            dueFrom={dueFrom}
            onDueFromChange={handleDueFromChange}
            dueTo={dueTo}
            onDueToChange={handleDueToChange}
            sortBy={sortBy}
            onSortByChange={handleSortByChange}
            direction={direction}
            onDirectionToggle={handleDirectionToggle}
          />

          {tasksLoading && !tasksData ? (
            <div className="empty-state">
              <div className="glyph">···</div>
              Loading tasks…
            </div>
          ) : tasksData && tasksData.content.length === 0 ? (
            <div className="empty-state">
              <div className="glyph">∅</div>
              No tasks found.
            </div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Title</th>
                    <th>Status</th>
                    <th>Priority</th>
                    <th>Owner</th>
                    <th>Due</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {tasksData?.content.map((task) => (
                    <tr key={task.id}>
                      <td className="mono">#{task.id}</td>
                      <td>{task.title}</td>
                      <td>
                        <span className="status-pill">{task.status}</span>
                      </td>
                      <td>
                        <span
                          className={`priority-dot ${task.priority.toLowerCase()}`}
                          title={`Priority: ${task.priority}`}
                        />{" "}
                        {task.priority}
                      </td>
                      <td>{task.ownerUsername}</td>
                      <td>
                        {task.dueDate
                          ? formatDateTime(task.dueDate)
                          : "—"}
                      </td>
                      <td>{formatDateTime(task.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {tasksData && tasksData.totalPages > 1 && (
            <div className="pagination">
              <button
                disabled={tasksData.first}
                onClick={() => setTasksPage((p) => p - 1)}
              >
                ← Prev
              </button>
              <span>
                page {tasksData.number + 1} / {tasksData.totalPages}
              </span>
              <button
                disabled={tasksData.last}
                onClick={() => setTasksPage((p) => p + 1)}
              >
                Next →
              </button>
            </div>
          )}
        </>
      )}
    </>
  );
}