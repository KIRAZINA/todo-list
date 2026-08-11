import { useEffect, useState, useCallback } from "react";
import { api } from "../api";

const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];
const STATUSES = ["TODO", "IN_PROGRESS", "DONE"];
const PAGE_SIZE = 10;

function nextStatus(current) {
  const i = STATUSES.indexOf(current);
  return STATUSES[(i + 1) % STATUSES.length];
}

export default function TaskBoard() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null); // PaginatedTaskResponse
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [dueDate, setDueDate] = useState("");
  const [creating, setCreating] = useState(false);

  const load = useCallback(async (p) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.listTasks(p, PAGE_SIZE);
      setData(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page);
  }, [page, load]);

  async function handleCreate(e) {
    e.preventDefault();
    if (!title.trim()) return;
    setCreating(true);
    setError(null);
    try {
      await api.createTask({
        title: title.trim(),
        description: description.trim() || null,
        priority,
        dueDate: dueDate || null,
      });
      setTitle("");
      setDescription("");
      setPriority("MEDIUM");
      setDueDate("");
      if (page === 0) {
        load(0);
      } else {
        setPage(0);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setCreating(false);
    }
  }

  async function handleCycleStatus(task) {
    const updated = nextStatus(task.status);
    // optimistic update
    setData((d) => ({
      ...d,
      content: d.content.map((t) => (t.id === task.id ? { ...t, status: updated } : t)),
    }));
    try {
      await api.updateTask(task.id, { status: updated });
    } catch (err) {
      setError(err.message);
      load(page);
    }
  }

  async function handleDelete(task) {
    if (!confirm(`Delete "${task.title}"?`)) return;
    setError(null);
    try {
      await api.deleteTask(task.id);
      load(page);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <>
      <form className="task-form-card" onSubmit={handleCreate}>
        <p className="eyebrow">New task</p>
        <div className="form-grid">
          <div className="field full">
            <label htmlFor="title">Title</label>
            <input
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={100}
              required
            />
          </div>
          <div className="field full">
            <label htmlFor="description">Description</label>
            <input
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={1000}
            />
          </div>
          <div className="field">
            <label htmlFor="priority">Priority</label>
            <select id="priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="dueDate">Due date</label>
            <input
              id="dueDate"
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
            />
          </div>
          <div className="field">
            <button className="btn" type="submit" disabled={creating}>
              {creating ? "Adding…" : "Add task"}
            </button>
          </div>
        </div>
      </form>

      {error && <div className="error-banner">{error}</div>}

      <div className="board-header">
        <h2>Tasks</h2>
        {data && <span className="count">{data.totalElements} total</span>}
      </div>

      {loading && !data ? (
        <div className="empty-state">
          <div className="glyph">···</div>
          Loading tasks…
        </div>
      ) : data && data.content.length === 0 ? (
        <div className="empty-state">
          <div className="glyph">∅</div>
          No tasks yet. Add one above.
        </div>
      ) : (
        <div className="task-list">
          {data?.content.map((task) => (
            <div className="task-row" key={task.id}>
              <span className={`priority-dot ${task.priority.toLowerCase()}`} title={`Priority: ${task.priority}`} />
              <div className="task-main">
                <div className="task-title">{task.title}</div>
                <div className="task-meta">
                  #{task.id}
                  {task.dueDate ? ` · due ${task.dueDate}` : ""}
                </div>
                {task.description && <div className="task-desc">{task.description}</div>}
              </div>
              <button className="status-pill" onClick={() => handleCycleStatus(task)} title="Click to advance status">
                {task.status}
              </button>
              <button className="btn-danger" onClick={() => handleDelete(task)}>
                Delete
              </button>
            </div>
          ))}
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            ← Prev
          </button>
          <span>
            page {data.number + 1} / {data.totalPages}
          </span>
          <button disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Next →
          </button>
        </div>
      )}
    </>
  );
}