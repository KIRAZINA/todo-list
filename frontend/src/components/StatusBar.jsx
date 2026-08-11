import { useEffect, useState } from "react";
import { api } from "../api";

export default function StatusBar() {
  const [status, setStatus] = useState("checking"); // "checking" | "up" | "down"
  const [lastChecked, setLastChecked] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function ping() {
      try {
        await api.health();
        if (!cancelled) setStatus("up");
      } catch {
        if (!cancelled) setStatus("down");
      } finally {
        if (!cancelled) setLastChecked(new Date());
      }
    }

    ping();
    const interval = setInterval(ping, 15000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  return (
    <div className="statusbar">
      <span className={`dot ${status === "up" ? "up" : status === "down" ? "down" : ""}`} />
      <span>
        api /api/test/health — {status}
        {lastChecked ? ` — checked ${lastChecked.toLocaleTimeString()}` : ""}
      </span>
    </div>
  );
}