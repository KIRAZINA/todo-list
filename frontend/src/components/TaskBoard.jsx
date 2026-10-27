import { useEffect, useState, useCallback } from "react";
import { api } from "../api";
import TaskFilters, { PRIORITIES, STATUSES } from "./TaskFilters";

const PAGE_SIZE = 10;

function nextStatus(current) {
  const i = STATUSES.indexOf(current);
  return STATUSES[(i + 1) % STATUSES.length];
}

function formatDueDate(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export default function TaskBoard() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState(null);
  const [priorityFilter, setPriorityFilter] = useState(null);
  const [overdueFilter, setOverdueFilter] = useState(false);
  const [nearDueFilter, setNearDueFilter] = useState(false);
  const [tagFilter, setTagFilter] = useState("");
  const [dueFrom, setDueFrom] = useState("");
  const [dueTo, setDueTo] = useState("");
  const [sortBy, setSortBy] = useState("createdAt");
  const [direction, setDirection] = useState("desc");
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [dueDate, setDueDate] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  const [subtasks, setSubtasks] = useState([]);
  const [newSubtask, setNewSubtask] = useState("");
  const [creating, setCreating] = useState(false);

  const load = useCallback(async (p, status, prio, overdue, from, to, sort, dir) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.listTasks(p, PAGE_SIZE, status, prio, overdue, from, to, sort, dir);
      setData(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page, statusFilter, priorityFilter, overdueFilter, dueFrom, dueTo, sortBy, direction);
  }, [page, statusFilter, priorityFilter, overdueFilter, dueFrom, dueTo, sortBy, direction, load]);

  function handleStatusFilterChange(value) { setPage(0); setStatusFilter(value); }
  function handlePriorityFilterChange(value) { setPage(0); setPriorityFilter(value); }
  function handleOverdueToggle() { setPage(0); setOverdueFilter((v) => !v); }
  function handleNearDueToggle() { setPage(0); setNearDueFilter((v) => !v); }
  function handleTagFilterChange(value) { setPage(0); setTagFilter(value); }
  function handleDueFromChange(value) { setPage(0); setDueFrom(value); }
  function handleDueToChange(value) { setPage(0); setDueTo(value); }
  function handleSortByChange(value) { setPage(0); setSortBy(value); }
  function handleDirectionToggle() { setPage(0); setDirection((d) => (d === "asc" ? "desc" : "asc")); }

  function addSubtask() {
    if (!newSubtask.trim()) return;
    setSubtasks((s) => [...s, { title: newSubtask.trim(), completed: false }]);
    setNewSubtask("");
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!title.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const tags = tagsInput.split(",").map((t) => t.trim()).filter(Boolean);
      await api.createTask({
        title: title.trim(),
        description: description.trim() || null,
        priority,
        dueDate: dueDate || null,
        tags: tags.length ? tags : null,
        subtasks: subtasks.length ? subtasks : null,
      });
      setTitle("");
      setDescription("");
      setPriority("MEDIUM");
      setDueDate("");
      setTagsInput("");
      setSubtasks([]);
      if (page === 0) {
        load(0, statusFilter, priorityFilter, overdueFilter, dueFrom, dueTo, sortBy, direction);
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
    setData((d) => ({
      ...d,
      content: d.content.map((t) => (t.id === task.id ? { ...t, status: updated } : t)),
    }));
    try {
      await api.updateTask(task.id, { status: updated });
    } catch (err) {
      setError(err.message);
      load(page, statusFilter, priorityFilter, overdueFilter, dueFrom, dueTo, sortBy, direction);
    }
  }

  async function handleToggleSubtask(task, subtask) {
    const updatedSubtasks = (task.subtasks || []).map((s) =>
      s.id === subtask.id ? { ...s, completed: !s.completed } : { title: s.title, completed: s.completed }
    );
    // optimistic
    setData((d) => ({
      ...d,
      content: d.content.map((t) =>
        t.id === task.id
          ? { ...t, subtasks: t.subtasks.map((s) => (s.id === subtask.id ? { ...s, completed: !s.completed } : s)) }
          : t
      ),
    }));
    try {
      await api.updateTask(task.id, { subtasks: updatedSubtasks });
    } catch (err) {
      setError(err.message);
      load(page, statusFilter, priorityFilter, overdueFilter, dueFrom, dueTo, sortBy, direction);
    }
  }

  async function handleDelete(task) {
    if (!confirm(`Delete "${task.title}"?`)) return;
    setError(null);
    try {
      await api.deleteTask(task.id);
      load(page, statusFilter, priorityFilter, overdueFilter, dueFrom, dueTo, sortBy, direction);
    } catch (err) {
      setError(err.message);
    }
  }

  // client-side filtering for tag / near-due on current page
  const filteredContent = (data?.content || []).filter((t) => {
    if (nearDueFilter && !t.nearDueDate) return false;
    if (tagFilter.trim() && !(t.tags || []).some((tg) => tg.toLowerCase().includes(tagFilter.toLowerCase()))) return false;
    return true;
  });

  return (
    <>
      <form className="task-form-card" onSubmit={handleCreate}>
        <p className="eyebrow">New task</p>
        <div className="form-grid">
          <div className="field full">
            <label htmlFor="title">Title</label>
            <input id="title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} required />
          </div>
          <div className="field full">
            <label htmlFor="description">Description</label>
            <input id="description" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} />
          </div>
          <div className="field">
            <label htmlFor="priority">Priority</label>
            <select id="priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
              {PRIORITIES.map((p) => (<option key={p} value={p}>{p}</option>))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="dueDate">Due date</label>
            <input id="dueDate" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </div>
          <div className="field full">
            <label htmlFor="tags">Tags (comma separated)</label>
            <input id="tags" value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} placeholder="work, urgent" />
          </div>
          <div className="field full">
            <label>Subtasks</label>
            <div className="subtask-input-row">
              <input value={newSubtask} onChange={(e) => setNewSubtask(e.target.value)} placeholder="Add subtask" />
              <button type="button" className="btn" onClick={addSubtask}>Add</button>
            </div>
            {subtasks.length > 0 && (
              <ul className="subtask-list subtask-list-edit">
                {subtasks.map((s, i) => (
                  <li key={i} className="subtask-item">
                    <span>{s.title}</span>
                    <button type="button" className="btn-danger" onClick={() => setSubtasks((arr) => arr.filter((_, idx) => idx !== i))}>×</button>
                  </li>
                ))}
              </ul>
            )}
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

      <TaskFilters
        status={statusFilter}
        onStatusChange={handleStatusFilterChange}
        priority={priorityFilter}
        onPriorityChange={handlePriorityFilterChange}
        overdue={overdueFilter}
        onOverdueToggle={handleOverdueToggle}
        nearDue={nearDueFilter}
        onNearDueToggle={handleNearDueToggle}
        tagFilter={tagFilter}
        onTagFilterChange={handleTagFilterChange}
        dueFrom={dueFrom}
        onDueFromChange={handleDueFromChange}
        dueTo={dueTo}
        onDueToChange={handleDueToChange}
        sortBy={sortBy}
        onSortByChange={handleSortByChange}
        direction={direction}
        onDirectionToggle={handleDirectionToggle}
      />

      {loading && !data ? (
        <div className="empty-state"><div className="glyph">···</div>Loading tasks…</div>
      ) : data && data.content.length === 0 ? (
        <div className="empty-state"><div className="glyph">∅</div>No tasks yet. Add one above.</div>
      ) : (
        <div className="task-list">
          {filteredContent.map((task) => {
            const subs = task.subtasks || [];
            const doneSubs = subs.filter((s) => s.completed).length;
            return (
              <div className="task-row" key={task.id}>
                <span className={`priority-dot ${task.priority.toLowerCase()}`} title={`Priority: ${task.priority}`} />
                <div className="task-main">
                  <div className="task-title">
                    {task.title}
                    {task.nearDueDate && <span className="badge-near-due">Near due</span>}
                    {task.overdue && <span className="badge-overdue">Overdue</span>}
                  </div>
                  <div className="task-meta">
                    #{task.id}
                    {task.dueDate ? (<> · due <span className={`due-date${task.overdue ? " overdue" : ""}`}>{formatDueDate(task.dueDate)}</span></>) : null}
                  </div>
                  {task.tags && task.tags.length > 0 && (
                    <div className="tag-chip-row">
                      {task.tags.map((t, i) => (
                        <span className="tag-chip" key={i} style={typeof t === "string" ? {} : { backgroundColor: t.color || undefined }}>
                          {typeof t === "string" ? t : t.name}
                        </span>
                      ))}
                    </div>
                  )}
                  {subs.length > 0 && (
                    <div className="subtask-progress">
                      <div className="progress-bar"><div className="progress-fill" style={{ width: `${(doneSubs / subs.length) * 100}%` }} /></div>
                      <span className="progress-text">{doneSubs}/{subs.length} subtasks</span>
                      <ul className="subtask-list">
                        {subs.map((s) => (
                          <li key={s.id} className={`subtask-item${s.completed ? " completed" : ""}`}>
                            <input type="checkbox" checked={s.completed} onChange={() => handleToggleSubtask(task, s)} />
                            <span>{s.title}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {task.description && <div className="task-desc">{task.description}</div>}
                </div>
                <button className="status-pill" onClick={() => handleCycleStatus(task)} title="Click to advance status">
                  {task.status}
                </button>
                <button className="btn-danger" onClick={() => handleDelete(task)}>Delete</button>
              </div>
            );
          })}
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button disabled={data.first} onClick={() => setPage((p) => p - 1)}>← Prev</button>
          <span>page {data.number + 1} / {data.totalPages}</span>
          <button disabled={data.last} onClick={() => setPage((p) => p + 1)}>Next →</button>
        </div>
      )}
    </>
  );
}
