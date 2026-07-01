import HomePage from "./Page/HomePage";
import WebMapPage from "./Page/WebMapPage";

export default function App() {
  return window.location.pathname === "/webmap" || window.location.pathname.startsWith("/webmap/")
    ? <WebMapPage />
    : <HomePage />;
}
