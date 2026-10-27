export const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];
export const STATUSES = ["TODO", "IN_PROGRESS", "DONE"];

const STATUS_FILTERS = [
  { label: "All", value: null },
  ...STATUSES.map((s) => ({ label: s, value: s })),
];
const PRIORITY_FILTERS = [
  { label: "All", value: null },
  ...PRIORITIES.map((p) => ({ label: p, value: p })),
];

export default function TaskFilters({
  status,
  onStatusChange,
  priority,
  onPriorityChange,
  overdue,
  onOverdueToggle,
  nearDue,
  onNearDueToggle,
  tagFilter,
  onTagFilterChange,
  dueFrom,
  onDueFromChange,
  dueTo,
  onDueToChange,
  sortBy,
  onSortByChange,
  direction,
  onDirectionToggle,
}) {
  return (
    <div className="filters-row">
      <div className="status-filters" role="group" aria-label="Filter tasks by status">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.label}
            className={`filter-btn${status === f.value ? " active" : ""}`}
            onClick={() => onStatusChange(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>
      <div className="status-filters" role="group" aria-label="Filter tasks by priority">
        {PRIORITY_FILTERS.map((f) => (
          <button
            key={f.label}
            className={`filter-btn${priority === f.value ? " active" : ""}`}
            onClick={() => onPriorityChange(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>
      <div className="status-filters" role="group" aria-label="Filter by due date flags">
        <button
          className={`filter-btn overdue-toggle${overdue ? " active" : ""}`}
          onClick={onOverdueToggle}
          aria-pressed={overdue}
        >
          Overdue
        </button>
        <button
          className={`filter-btn near-due-toggle${nearDue ? " active" : ""}`}
          onClick={onNearDueToggle}
          aria-pressed={nearDue}
        >
          Near due
        </button>
      </div>
      <div className="date-filters" role="group" aria-label="Filter tasks by due date">
        <label htmlFor="tagFilter">Tag</label>
        <input
          id="tagFilter"
          value={tagFilter}
          onChange={(e) => onTagFilterChange(e.target.value)}
          placeholder="tag name"
        />
        <label htmlFor="dueFrom">Due from</label>
        <input
          id="dueFrom"
          type="date"
          value={dueFrom}
          onChange={(e) => onDueFromChange(e.target.value)}
        />
        <label htmlFor="dueTo">Due to</label>
        <input
          id="dueTo"
          type="date"
          value={dueTo}
          onChange={(e) => onDueToChange(e.target.value)}
        />
      </div>
      <div className="sort-filters" role="group" aria-label="Sort tasks">
        <label htmlFor="sortBy">Sort by</label>
        <select id="sortBy" value={sortBy} onChange={(e) => onSortByChange(e.target.value)}>
          <option value="createdAt">Created</option>
          <option value="dueDate">Due date</option>
          <option value="priority">Priority</option>
          <option value="title">Title</option>
        </select>
        <button
          className={`filter-btn sort-direction${direction === "asc" ? " active" : ""}`}
          onClick={onDirectionToggle}
          aria-pressed={direction === "asc"}
        >
          {direction === "asc" ? "↑ Asc" : "↓ Desc"}
        </button>
      </div>
    </div>
  );
}
