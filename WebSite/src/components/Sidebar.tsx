import "./sidebar.css";

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <h2 className="sidebar-title">BetterSurvival</h2>
      <nav>
        <a href="/" className="sidebar-link">Home</a>
        <a href="/webmap" className="sidebar-link">WebMap</a>
      </nav>
    </aside>
  );
}
