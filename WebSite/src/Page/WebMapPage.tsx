import { useEffect, useMemo, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type {
  BootstrapResponse,
  PlayerEntry,
  PlayersResponse,
  StatusResponse,
  WorldMarker,
  WorldMarkersResponse,
  WorldDetail,
  WorldSummary,
} from "../types";

type MarkerBundle = {
  marker: L.Marker;
};

type WaypointBundle = {
  marker: L.Marker;
  tooltipHtml: string;
};

type ChunkLoaderBundle = {
  rectangle: L.Rectangle;
  color: string;
  fading: boolean;
  fadeTimer: number | null;
  visibleOpacity: number;
  visibleFillOpacity: number;
};

type ChangedTile = {
  x: number;
  z: number;
  updatedAt: number;
};

type WorldChangesResponse = {
  tiles: ChangedTile[];
  latest: number;
};

type WebMapTileLayer = L.TileLayer & {
  setRevision: (revision: number) => void;
};

type MapCoords = {
  x: number;
  z: number;
};

type MapContextMenuState = {
  x: number;
  y: number;
  coords: MapCoords;
};

const DEFAULT_BOOTSTRAP: BootstrapResponse = {
  static: false,
  server: {
    title: "Minecraft Server WebMap - {world}",
    statusUrl: "/api/v1/status",
    worldsUrl: "/api/v1/worlds",
    playersUrl: "/api/v1/players",
  },
};

function parseQuery() {
  const search = window.location.search;
  let query = new URLSearchParams(search);
  if (!query.has("world") && !query.has("x") && !query.has("z") && !query.has("zoom")) {
    const decodedHref = decodeURIComponent(window.location.href);
    const index = decodedHref.indexOf("?");
    if (index >= 0) {
      const raw = decodedHref.slice(index + 1).split("#")[0];
      query = new URLSearchParams(raw);
    }
  }
  return {
    world: query.get("world") ?? "",
    x: Number(query.get("x") ?? "NaN"),
    z: Number(query.get("z") ?? "NaN"),
    zoom: Number(query.get("zoom") ?? "NaN"),
    follow: query.get("follow") ?? "",
  };
}

function toLatLng(x: number, z: number) {
  return L.latLng(-z, x);
}

function toPoint(latlng: L.LatLng) {
  return { x: Math.floor(latlng.lng), z: Math.floor(-latlng.lat) };
}

function toChunkBounds(chunkX: number, chunkZ: number) {
  return L.latLngBounds(
    toLatLng(chunkX * 16, (chunkZ + 1) * 16),
    toLatLng((chunkX + 1) * 16, chunkZ * 16)
  );
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatWaypointLabel(value: string) {
  return value
    .replace(/<br\s*\/?>/gi, "\n")
    .replaceAll("\\n", "\n")
    .split("\n")
    .map((line) => escapeHtml(line))
    .join("<br>");
}

function copyableCoords(coords: MapCoords | null) {
  if (!coords) {
    return "x: --- y: ? z: ---";
  }
  return `x: ${coords.x} y: ? z: ${coords.z}`;
}

async function copyText(text: string) {
  if (!text) {
    return false;
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // fall through to legacy copy path
  }
  const textArea = document.createElement("textarea");
  textArea.value = text;
  textArea.style.position = "fixed";
  textArea.style.opacity = "0";
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();
  let copied = false;
  try {
    copied = document.execCommand("copy");
  } catch {
    copied = false;
  }
  textArea.remove();
  return copied;
}

function makeWaypointIcon(marker: WorldMarker) {
  return L.divIcon({
    className: `waypoint-marker-wrap waypoint-kind-${marker.kind}`,
    html: `
      <div class="waypoint-marker" style="--waypoint-color:${marker.color}; --waypoint-rotation:${marker.rotation}deg">
        <span class="waypoint-player-icon"></span>
      </div>
    `,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

function normalizeMarkersResponse(payload: unknown): WorldMarkersResponse {
  const source = payload && typeof payload === "object" ? (payload as Record<string, unknown>) : {};
  const rawMarkers = Array.isArray(source.markers) ? source.markers : [];
  return {
    markers: rawMarkers
      .map((entry): WorldMarker | null => {
        if (!entry || typeof entry !== "object") {
          return null;
        }
        const row = entry as Record<string, unknown>;
        const kind =
          row.kind === "loadedchunk" || row.kind === "persistentchunk" || row.kind === "waypoint"
            ? row.kind
            : "waypoint";
        const id = typeof row.id === "string" ? row.id : "";
        if (!id) {
          return null;
        }
        return {
          id,
          kind,
          name: typeof row.name === "string" ? row.name : "",
          displayName: typeof row.displayName === "string" && row.displayName ? row.displayName : typeof row.name === "string" ? row.name : "",
          color: typeof row.color === "string" && row.color ? row.color : "#ffffff",
          x: Number.isFinite(row.x) ? Number(row.x) : 0,
          z: Number.isFinite(row.z) ? Number(row.z) : 0,
          chunkX: Number.isFinite(row.chunkX) ? Number(row.chunkX) : 0,
          chunkZ: Number.isFinite(row.chunkZ) ? Number(row.chunkZ) : 0,
          rotation: Number.isFinite(row.rotation) ? Number(row.rotation) : 0,
        };
      })
      .filter((entry): entry is WorldMarker => entry !== null),
    updatedAt: Number.isFinite(source.updatedAt) ? Number(source.updatedAt) : Date.now(),
  };
}

function worldIcon(type: WorldSummary["type"], icon: string | null) {
  if (icon) {
    return `/images/icon/${icon}.png`;
  }
  if (type === "nether") return "/images/icon/red-cube-smol.png";
  if (type === "the_end") return "/images/icon/purple-cube-smol.png";
  return "/images/icon/green-cube-smol.png";
}

function makePlayerIcon(faceUrl: string) {
  return L.divIcon({
    className: "player-face-icon-wrap",
    html: `<img src="${faceUrl}" class="player-face-icon" alt="player" />`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  });
}

function makeSpawnIcon() {
  return L.icon({
    iconUrl: "/images/icon/spawn.png",
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
}

function playerTooltip(player: PlayerEntry, detail: WorldDetail | null) {
  const showHealth = detail?.playerTracker.nameplates.showHealth ?? false;
  const showArmor = detail?.playerTracker.nameplates.showArmor ?? false;
  const container = document.createElement("div");
  container.classList.add("nameplate-container");
  const col2 = document.createElement("div");
  col2.classList.add("nameplate-col2");
  const displayName = document.createElement("span");
  displayName.classList.add("display-name");
  displayName.textContent = player.displayName;
  col2.appendChild(displayName);
  if (showHealth) {
    const health = document.createElement("img");
    health.src = `/images/health/${Math.min(Math.max(player.health, 0), 20)}.png`;
    health.classList.add("health");
    col2.appendChild(health);
  }
  if (showArmor) {
    const armor = document.createElement("img");
    armor.src = `/images/armor/${Math.min(Math.max(player.armor, 0), 20)}.png`;
    armor.classList.add("armor");
    col2.appendChild(armor);
  }
  container.appendChild(col2);
  return container;
}

function normalizePlayersResponse(payload: unknown): PlayersResponse {
  const source = payload && typeof payload === "object" ? (payload as Record<string, unknown>) : {};
  const rawPlayers = Array.isArray(source.players) ? source.players : [];
  return {
    players: rawPlayers
      .map((entry): PlayerEntry | null => {
        if (!entry || typeof entry !== "object") {
          return null;
        }
        const row = entry as Record<string, unknown>;
        const uuid = typeof row.uuid === "string" ? row.uuid : "";
        const name = typeof row.name === "string" ? row.name : "";
        if (!uuid || !name) {
          return null;
        }
        return {
          uuid,
          name,
          displayName: typeof row.displayName === "string" && row.displayName ? row.displayName : name,
          worldKey: typeof row.worldKey === "string" ? row.worldKey : "",
          x: Number.isFinite(row.x) ? Number(row.x) : 0,
          y: Number.isFinite(row.y) ? Number(row.y) : 0,
          z: Number.isFinite(row.z) ? Number(row.z) : 0,
          yaw: Number.isFinite(row.yaw) ? Number(row.yaw) : 0,
          chunkReady: row.chunkReady === true,
          faceUrl: typeof row.faceUrl === "string" && row.faceUrl
            ? row.faceUrl
            : typeof row.face_url === "string" && row.face_url
              ? row.face_url
              : "/images/clear.png",
          health: Number.isFinite(row.health) ? Number(row.health) : 0,
          armor: Number.isFinite(row.armor) ? Number(row.armor) : 0,
        };
      })
      .filter((entry): entry is PlayerEntry => entry !== null),
    max: Number.isFinite(source.max) ? Number(source.max) : 0,
    updatedAt: Number.isFinite(source.updatedAt) ? Number(source.updatedAt) : Date.now(),
  };
}

function normalizeWorldsResponse(payload: unknown): WorldSummary[] {
  if (!Array.isArray(payload)) {
    return [];
  }
  return payload
    .map((entry): WorldSummary | null => {
      if (!entry || typeof entry !== "object") {
        return null;
      }
      const row = entry as Record<string, unknown>;
      const key = typeof row.key === "string" ? row.key : "";
      const name = typeof row.name === "string" ? row.name : "";
      if (!key || !name) {
        return null;
      }
      return {
        key,
        name,
        displayName: typeof row.displayName === "string" && row.displayName ? row.displayName : name,
        order: Number.isFinite(row.order) ? Number(row.order) : 0,
        type: row.type === "nether" || row.type === "the_end" ? row.type : "normal",
        environment: typeof row.environment === "string" ? row.environment : "NORMAL",
        icon: typeof row.icon === "string" ? row.icon : null,
        chunks: Number.isFinite(row.chunks) ? Number(row.chunks) : 0,
        tileUrl: typeof row.tileUrl === "string" ? row.tileUrl : "",
        worldUrl: typeof row.worldUrl === "string" ? row.worldUrl : "",
        spawn: {
          x: Number.isFinite((row.spawn as Record<string, unknown> | undefined)?.x)
            ? Number((row.spawn as Record<string, unknown>).x)
            : 0,
          z: Number.isFinite((row.spawn as Record<string, unknown> | undefined)?.z)
            ? Number((row.spawn as Record<string, unknown>).z)
            : 0,
        },
      };
    })
    .filter((entry): entry is WorldSummary => entry !== null)
    .sort((left, right) => left.order - right.order);
}

function normalizeWorldDetail(payload: unknown): WorldDetail | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }
  const row = payload as Record<string, unknown>;
  const key = typeof row.key === "string" ? row.key : "";
  const name = typeof row.name === "string" ? row.name : "";
  if (!key || !name) {
    return null;
  }
  const playerTracker = row.playerTracker as Record<string, unknown> | undefined;
  const nameplates = playerTracker?.nameplates as Record<string, unknown> | undefined;
  const zoom = row.zoom as Record<string, unknown> | undefined;
  const spawn = row.spawn as Record<string, unknown> | undefined;
  return {
    key,
    name,
    displayName: typeof row.displayName === "string" && row.displayName ? row.displayName : name,
    environment: typeof row.environment === "string" ? row.environment : "NORMAL",
    type: row.type === "nether" || row.type === "the_end" ? row.type : "normal",
    chunks: Number.isFinite(row.chunks) ? Number(row.chunks) : 0,
    playerTracker: {
      enabled: playerTracker?.enabled !== false,
      nameplates: {
        enabled: nameplates?.enabled !== false,
        showHeads: nameplates?.showHeads !== false,
        showHealth: nameplates?.showHealth === true,
        showArmor: nameplates?.showArmor === true,
      },
    },
    zoom: {
      default: Number.isFinite(zoom?.default) ? Number(zoom?.default) : 0,
      maxNative: Number.isFinite(zoom?.maxNative) ? Number(zoom?.maxNative) : 0,
      extra: Number.isFinite(zoom?.extra) ? Number(zoom?.extra) : 4,
      min: Number.isFinite(zoom?.min) ? Number(zoom?.min) : -4,
      max: Number.isFinite(zoom?.max) ? Number(zoom?.max) : 4,
    },
    spawn: {
      x: Number.isFinite(spawn?.x) ? Number(spawn?.x) : 0,
      z: Number.isFinite(spawn?.z) ? Number(spawn?.z) : 0,
    },
    backgroundImage: typeof row.backgroundImage === "string" ? row.backgroundImage : "/images/overworld_sky.png",
    tileTemplate: typeof row.tileTemplate === "string" ? row.tileTemplate : "",
    changesUrl: typeof row.changesUrl === "string" ? row.changesUrl : "",
    markersUrl: typeof row.markersUrl === "string" ? row.markersUrl : "",
  };
}

function normalizeWorldChanges(payload: unknown): WorldChangesResponse {
  const source = payload && typeof payload === "object" ? (payload as Record<string, unknown>) : {};
  const rawTiles = Array.isArray(source.tiles) ? source.tiles : [];
  return {
    tiles: rawTiles
      .map((entry): ChangedTile | null => {
        if (!entry || typeof entry !== "object") {
          return null;
        }
        const row = entry as Record<string, unknown>;
        return {
          x: Number.isFinite(row.x) ? Number(row.x) : 0,
          z: Number.isFinite(row.z) ? Number(row.z) : 0,
          updatedAt: Number.isFinite(row.updatedAt) ? Number(row.updatedAt) : 0,
        };
      })
      .filter((entry): entry is ChangedTile => entry !== null),
    latest: Number.isFinite(source.latest) ? Number(source.latest) : 0,
  };
}

function SquaremapTileLayer(template: string, maxNativeZoom: number) {
  const SquaremapTileLayerImpl = L.TileLayer.extend({
    initialize(url: string, options: L.TileLayerOptions) {
      // @ts-ignore Leaflet internal initialize
      L.TileLayer.prototype.initialize.call(this, url, options);
      this._revision = 0;
    },
    getTileUrl(coords: L.Coords) {
      // @ts-ignore Leaflet internal getTileUrl
      const base = L.TileLayer.prototype.getTileUrl.call(this, coords);
      const separator = base.includes("?") ? "&" : "?";
      return `${base}${separator}v=${this._revision ?? 0}`;
    },
    setRevision(revision: number) {
      this._revision = revision;
    },
    createTile(coords: L.Coords, done: L.DoneCallback) {
      const tile = document.createElement("img");
      L.DomEvent.on(tile, "load", () => {
        URL.revokeObjectURL(tile.src);
        // @ts-ignore Leaflet private hook
        this._tileOnLoad(done, tile);
      });
      L.DomEvent.on(tile, "error", L.Util.bind(
        // @ts-ignore Leaflet private hook
        this._tileOnError,
        this,
        done,
        tile
      ));
      tile.alt = "";
      tile.setAttribute("role", "presentation");
      const url = this.getTileUrl(coords);
      fetch(url, { cache: "no-store" })
        .then(async (response) => {
          if (!response.ok) {
            // @ts-ignore Leaflet private hook
            this._tileOnError(done, tile, null);
            return;
          }
          tile.src = URL.createObjectURL(await response.blob());
        })
        .catch(() => {
          // @ts-ignore Leaflet private hook
          this._tileOnError(done, tile, null);
        });
      return tile;
    },
  });
  const TileLayerConstructor = SquaremapTileLayerImpl as unknown as new (url: string, options: L.TileLayerOptions) => WebMapTileLayer;
  return new TileLayerConstructor(template, {
    tileSize: 512,
    noWrap: true,
    minNativeZoom: 0,
    maxNativeZoom,
    minZoom: -6,
    maxZoom: maxNativeZoom + 6,
    errorTileUrl: "/images/clear.png",
  });
}

export default function WebMapPage() {
  const chunkFadeDurationMs = 420;
  const mapElementRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const activeTileLayerRef = useRef<WebMapTileLayer | null>(null);
  const incomingTileLayerRef = useRef<WebMapTileLayer | null>(null);
  const spawnMarkerRef = useRef<L.Marker | null>(null);
  const markerLayerRef = useRef<L.LayerGroup | null>(null);
  const waypointLayerRef = useRef<L.LayerGroup | null>(null);
  const chunkLoaderLayerRef = useRef<L.LayerGroup | null>(null);
  const chunkLoaderRendererRef = useRef<L.SVG | null>(null);
  const bundlesRef = useRef<Map<string, MarkerBundle>>(new Map());
  const waypointBundlesRef = useRef<Map<string, WaypointBundle>>(new Map());
  const chunkLoaderBundlesRef = useRef<Map<string, ChunkLoaderBundle>>(new Map());
  const latestTileUpdateRef = useRef(0);
  const lastForcedTileRefreshRef = useRef(0);
  const latestMarkerUpdateRef = useRef(0);
  const initializedWorldViewRef = useRef<string>("");
  const loadedWorldUrlRef = useRef<string>("");
  const tileSwapPendingRef = useRef(false);
  const longPressTimerRef = useRef<number | null>(null);
  const longPressMovedRef = useRef(false);

  const [bootstrap, setBootstrap] = useState<BootstrapResponse>(DEFAULT_BOOTSTRAP);
  const [status, setStatus] = useState<StatusResponse | null>(null);
  const [worlds, setWorlds] = useState<WorldSummary[]>([]);
  const [worldDetail, setWorldDetail] = useState<WorldDetail | null>(null);
  const [players, setPlayers] = useState<PlayersResponse | null>(null);
  const [activeWorldKey, setActiveWorldKey] = useState("");
  const [followUuid, setFollowUuid] = useState(parseQuery().follow);
  const [coords, setCoords] = useState<MapCoords | null>(null);
  const [contextMenu, setContextMenu] = useState<MapContextMenuState | null>(null);
  const [chunkLoadVisible, setChunkLoadVisible] = useState(false);
  const [persistentChunkVisible, setPersistentChunkVisible] = useState(false);
  const [isMobileLayout, setIsMobileLayout] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  const closeContextMenu = () => setContextMenu(null);

  const openContextMenuAt = (clientX: number, clientY: number, latlng: L.LatLng) => {
    const point = toPoint(latlng);
    setCoords(point);
    setContextMenu({ x: clientX, y: clientY, coords: point });
  };

  const buildShareUrl = () => {
    const url = new URL(window.location.href);
    const map = mapRef.current;
    const currentWorld = worlds.find((world) => world.key === activeWorldKey);
    if (map && currentWorld) {
      const center = toPoint(map.getCenter());
      url.searchParams.set("world", currentWorld.key);
      url.searchParams.set("x", String(center.x));
      url.searchParams.set("z", String(center.z));
      url.searchParams.set("zoom", String(map.getZoom()));
    }
    if (followUuid) {
      url.searchParams.set("follow", followUuid);
    } else {
      url.searchParams.delete("follow");
    }
    return url.toString();
  };

  useEffect(() => {
    const media = window.matchMedia("(max-width: 900px)");
    const update = () => {
      const mobile = media.matches;
      setIsMobileLayout(mobile);
      if (!mobile) {
        setMobileSidebarOpen(false);
      }
    };
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  const activePlayers = useMemo(
    () => (players?.players ?? []).filter((player) => player.worldKey === activeWorldKey),
    [players, activeWorldKey]
  );

  const renderablePlayers = useMemo(
    () => activePlayers.filter((player) => player.chunkReady),
    [activePlayers]
  );

  const activeWorld = useMemo(
    () => worlds.find((world) => world.key === activeWorldKey) ?? null,
    [worlds, activeWorldKey]
  );

  useEffect(() => {
    if (!mapElementRef.current) {
      return;
    }
    const map = L.map(mapElementRef.current, {
      crs: L.CRS.Simple,
      center: [0, 0],
      zoom: 0,
      minZoom: -6,
      maxZoom: 6,
      preferCanvas: true,
      attributionControl: false,
      noWrap: true,
      zoomControl: true,
    } as L.MapOptions);
    map.createPane("chunkloader").style.zIndex = "450";
    map.createPane("waypoint").style.zIndex = "650";
    map.createPane("nameplate").style.zIndex = "1000";
    chunkLoaderRendererRef.current = L.svg({ pane: "chunkloader" });
    chunkLoaderRendererRef.current.addTo(map);
    markerLayerRef.current = L.layerGroup().addTo(map);
    waypointLayerRef.current = L.layerGroup().addTo(map);
    chunkLoaderLayerRef.current = L.layerGroup().addTo(map);

    const clearLongPress = () => {
      if (longPressTimerRef.current != null) {
        window.clearTimeout(longPressTimerRef.current);
        longPressTimerRef.current = null;
      }
    };

    map.on("click", () => {
      setFollowUuid("");
      setMobileSidebarOpen(false);
      closeContextMenu();
    });
    map.on("mousemove", (event) => {
      setCoords(toPoint(event.latlng));
    });
    map.on("contextmenu", (event) => {
      event.originalEvent.preventDefault();
      openContextMenuAt(event.originalEvent.clientX, event.originalEvent.clientY, event.latlng);
    });
    map.on("touchstart", (event: L.LeafletEvent) => {
      const originalEvent = (event as L.LeafletEvent & { originalEvent?: TouchEvent }).originalEvent;
      const touch = originalEvent?.touches?.[0];
      if (!touch) {
        return;
      }
      longPressMovedRef.current = false;
      clearLongPress();
      const clientX = touch.clientX;
      const clientY = touch.clientY;
      longPressTimerRef.current = window.setTimeout(() => {
        if (longPressMovedRef.current) {
          return;
        }
        const latlng = map.mouseEventToLatLng({ clientX, clientY } as MouseEvent);
        openContextMenuAt(clientX, clientY, latlng);
      }, 520);
    });
    map.on("touchmove", () => {
      longPressMovedRef.current = true;
      clearLongPress();
    });
    map.on("touchend touchcancel", clearLongPress);
    map.on("movestart zoomstart dragstart", () => {
      setMobileSidebarOpen(false);
      closeContextMenu();
    });
    map.on("moveend zoomend", () => {
      const currentWorld = worlds.find((world) => world.key === activeWorldKey);
      if (!currentWorld) {
        return;
      }
      const center = toPoint(map.getCenter());
      const url = new URL(window.location.href);
      url.searchParams.set("world", currentWorld.key);
      url.searchParams.set("x", String(center.x));
      url.searchParams.set("z", String(center.z));
      url.searchParams.set("zoom", String(map.getZoom()));
      if (followUuid) {
        url.searchParams.set("follow", followUuid);
      } else {
        url.searchParams.delete("follow");
      }
      window.history.replaceState(null, "", url.toString());
    });
    mapRef.current = map;
    return () => {
      clearLongPress();
      map.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const loadBootstrap = async () => {
      try {
        const response = await fetch("/api/v1/bootstrap", { cache: "no-store" });
        if (response.ok) {
          setBootstrap((await response.json()) as BootstrapResponse);
        }
      } catch {
        // keep defaults while server is temporarily unavailable
      }
    };
    loadBootstrap();
  }, []);

  useEffect(() => {
    const refreshStatus = async () => {
      try {
        const response = await fetch(bootstrap.server.statusUrl, { cache: "no-store" });
        if (response.ok) {
          setStatus((await response.json()) as StatusResponse);
        }
      } catch {
        // ignore transient network errors
      }
    };
    const refreshWorlds = async () => {
      try {
        const response = await fetch(bootstrap.server.worldsUrl, { cache: "no-store" });
        if (!response.ok) {
          return;
        }
        const nextWorlds = normalizeWorldsResponse(await response.json());
        setWorlds(nextWorlds);
        if (!activeWorldKey && nextWorlds.length > 0) {
          const query = parseQuery();
          const chosen = nextWorlds.find((world) => world.key === query.world) ?? nextWorlds[0];
          setActiveWorldKey(chosen.key);
        }
      } catch {
        // ignore transient network errors
      }
    };
    const refreshPlayers = async () => {
      try {
        const response = await fetch(bootstrap.server.playersUrl, { cache: "no-store" });
        if (response.ok) {
          setPlayers(normalizePlayersResponse(await response.json()));
        }
      } catch {
        // ignore transient network errors
      }
    };
    void refreshStatus();
    void refreshWorlds();
    void refreshPlayers();
    const statusTimer = window.setInterval(() => void refreshStatus(), 5000);
    const worldTimer = window.setInterval(() => void refreshWorlds(), 15000);
    const playerTimer = window.setInterval(() => void refreshPlayers(), 2000);
    return () => {
      window.clearInterval(statusTimer);
      window.clearInterval(worldTimer);
      window.clearInterval(playerTimer);
    };
  }, [bootstrap, activeWorldKey]);

  useEffect(() => {
    if (!activeWorldKey) {
      return;
    }
    const selectedWorld = worlds.find((world) => world.key === activeWorldKey);
    if (!selectedWorld) {
      return;
    }
    if (loadedWorldUrlRef.current === selectedWorld.worldUrl) {
      return;
    }
    loadedWorldUrlRef.current = selectedWorld.worldUrl;
    const loadWorld = async () => {
      try {
        const response = await fetch(selectedWorld.worldUrl, { cache: "no-store" });
        if (!response.ok) {
          loadedWorldUrlRef.current = "";
          return;
        }
        setWorldDetail(normalizeWorldDetail(await response.json()));
      } catch {
        loadedWorldUrlRef.current = "";
        // ignore transient network errors
      }
    };
    void loadWorld();
  }, [activeWorldKey, worlds]);

  useEffect(() => {
    if (!mapRef.current || !worldDetail) {
      return;
    }
    const map = mapRef.current;
    document.title = bootstrap.server.title.replace("{world}", worldDetail.displayName);
    if (activeTileLayerRef.current) {
      activeTileLayerRef.current.remove();
      activeTileLayerRef.current = null;
    }
    if (incomingTileLayerRef.current) {
      incomingTileLayerRef.current.remove();
      incomingTileLayerRef.current = null;
    }
    const initialLayer = SquaremapTileLayer(worldDetail.tileTemplate, worldDetail.zoom.maxNative);
    initialLayer.addTo(map).setZIndex(1);
    initialLayer.setOpacity(1);
    activeTileLayerRef.current = initialLayer;
    latestTileUpdateRef.current = 0;
    tileSwapPendingRef.current = false;

    if (spawnMarkerRef.current) {
      spawnMarkerRef.current.remove();
    }
    spawnMarkerRef.current = L.marker(toLatLng(worldDetail.spawn.x, worldDetail.spawn.z), {
      icon: makeSpawnIcon(),
      keyboard: false,
    }).addTo(map);

    map.setMinZoom(worldDetail.zoom.min);
    map.setMaxZoom(worldDetail.zoom.max);
    if (initializedWorldViewRef.current !== worldDetail.key) {
      const query = parseQuery();
      const x = Number.isFinite(query.x) ? query.x : worldDetail.spawn.x;
      const z = Number.isFinite(query.z) ? query.z : worldDetail.spawn.z;
      const zoom = Number.isFinite(query.zoom) ? query.zoom : worldDetail.zoom.default;
      map.setView(toLatLng(x, z), zoom);
      initializedWorldViewRef.current = worldDetail.key;
    }

    const mapNode = document.getElementById("map");
    if (mapNode) {
      mapNode.style.backgroundImage = `url('${worldDetail.backgroundImage}')`;
    }
  }, [worldDetail, bootstrap]);

  useEffect(() => {
    if (!activeTileLayerRef.current || !mapRef.current || !worldDetail || !worldDetail.changesUrl) {
      return;
    }
    const activateTileLayer = (nextLayer: WebMapTileLayer, revision: number) => {
      const previousLayer = activeTileLayerRef.current;
      nextLayer.setOpacity(1);
      window.setTimeout(() => {
        if (previousLayer && previousLayer !== nextLayer) {
          previousLayer.remove();
        }
        nextLayer.setZIndex(1);
        activeTileLayerRef.current = nextLayer;
        incomingTileLayerRef.current = null;
        tileSwapPendingRef.current = false;
        latestTileUpdateRef.current = Math.max(latestTileUpdateRef.current, revision);
      }, 260);
    };
    const swapTileLayer = (revision: number, advanceLatest: boolean) => {
      const map = mapRef.current;
      const currentLayer = activeTileLayerRef.current;
      if (!map || !currentLayer || tileSwapPendingRef.current) {
        return;
      }
      if (incomingTileLayerRef.current) {
        incomingTileLayerRef.current.remove();
        incomingTileLayerRef.current = null;
      }
      const nextLayer = SquaremapTileLayer(worldDetail.tileTemplate, worldDetail.zoom.maxNative);
      nextLayer.setRevision(revision);
      nextLayer.addTo(map).setZIndex(2);
      nextLayer.setOpacity(0);
      incomingTileLayerRef.current = nextLayer;
      tileSwapPendingRef.current = true;
      const timeout = window.setTimeout(() => {
        const incoming = incomingTileLayerRef.current;
        if (incoming === nextLayer) {
          incoming.remove();
          incomingTileLayerRef.current = null;
        }
        tileSwapPendingRef.current = false;
      }, 5000);
      nextLayer.once("load", () => {
        window.clearTimeout(timeout);
        activateTileLayer(nextLayer, advanceLatest ? revision : latestTileUpdateRef.current);
      });
    };
    const refreshTimer = window.setInterval(async () => {
      if (tileSwapPendingRef.current) {
        return;
      }
      try {
        const since = latestTileUpdateRef.current;
        const response = await fetch(`${worldDetail.changesUrl}?since=${since}`, { cache: "no-store" });
        if (!response.ok) {
          return;
        }
        const changes = normalizeWorldChanges(await response.json());
        const now = Date.now();
        if (changes.tiles.length > 0) {
          swapTileLayer(Math.max(changes.latest, now), true);
          lastForcedTileRefreshRef.current = now;
        } else if (now - lastForcedTileRefreshRef.current >= 5000) {
          swapTileLayer(now, false);
          lastForcedTileRefreshRef.current = now;
        } else {
          latestTileUpdateRef.current = Math.max(latestTileUpdateRef.current, changes.latest);
        }
      } catch {
        tileSwapPendingRef.current = false;
      }
    }, 1000);
    return () => {
      window.clearInterval(refreshTimer);
    };
  }, [worldDetail]);

  useEffect(() => {
    if (!mapRef.current || !waypointLayerRef.current || !chunkLoaderLayerRef.current || !worldDetail?.markersUrl) {
      return;
    }
    const waypointLayer = waypointLayerRef.current;
    const chunkLayer = chunkLoaderLayerRef.current;
    const waypointBundles = waypointBundlesRef.current;
    const chunkLoaderBundles = chunkLoaderBundlesRef.current;
    let active = true;

    const renderMarkers = (markers: WorldMarker[]) => {
      const seenWaypoints = new Set<string>();
      const seenChunkLoaders = new Set<string>();
      for (const marker of markers) {
        if (marker.kind === "loadedchunk" || marker.kind === "persistentchunk") {
          seenChunkLoaders.add(marker.id);
          const isVisible = marker.kind === "loadedchunk" ? chunkLoadVisible : persistentChunkVisible;
          const visibleOpacity = marker.kind === "loadedchunk" ? 0.72 : 0.86;
          const visibleFillOpacity = marker.kind === "loadedchunk" ? 0.26 : 0.34;
          let bundle = chunkLoaderBundles.get(marker.id);
          if (!bundle) {
            const rectangle = L.rectangle(toChunkBounds(marker.chunkX, marker.chunkZ), {
              pane: "chunkloader",
              renderer: chunkLoaderRendererRef.current ?? undefined,
              className: "chunkloader-area",
              color: marker.color,
              weight: 1,
              fill: true,
              fillColor: marker.color,
              fillOpacity: isVisible ? visibleFillOpacity : 0,
              opacity: isVisible ? visibleOpacity : 0,
              interactive: false,
            }).addTo(chunkLayer);
            bundle = {
              rectangle,
              color: marker.color,
              fading: false,
              fadeTimer: null,
              visibleOpacity,
              visibleFillOpacity,
            };
            chunkLoaderBundles.set(marker.id, bundle);
          } else {
            if (bundle.fadeTimer != null) {
              window.clearTimeout(bundle.fadeTimer);
              bundle.fadeTimer = null;
            }
            bundle.fading = false;
            bundle.rectangle.setBounds(toChunkBounds(marker.chunkX, marker.chunkZ));
            if (bundle.color !== marker.color) {
              bundle.rectangle.setStyle({
                color: marker.color,
                fillColor: marker.color,
              });
              bundle.color = marker.color;
            }
            bundle.visibleOpacity = visibleOpacity;
            bundle.visibleFillOpacity = visibleFillOpacity;
          }
          if (!isVisible) {
            bundle.rectangle.setStyle({ opacity: 0, fillOpacity: 0 });
            continue;
          }
          bundle.rectangle.setStyle({ opacity: bundle.visibleOpacity, fillOpacity: bundle.visibleFillOpacity });
          continue;
        }
        seenWaypoints.add(marker.id);
        const point = toLatLng(marker.x, marker.z);
        const waypointName = marker.name || marker.displayName || "Waypoint";
        const tooltipHtml = `${formatWaypointLabel(waypointName)}<br>X ${marker.x} / Z ${marker.z}`;
        let bundle = waypointBundles.get(marker.id);
        if (!bundle) {
          const waypoint = L.marker(point, {
            icon: makeWaypointIcon(marker),
            keyboard: false,
            pane: "waypoint",
          }).addTo(waypointLayer);
          waypoint.bindTooltip(tooltipHtml, {
            direction: "top",
            offset: [0, -10],
            opacity: 0.96,
            pane: "nameplate",
            className: "waypoint-tooltip",
          });
          waypoint.on("contextmenu", (event) => {
            event.originalEvent.preventDefault();
            event.originalEvent.stopPropagation();
            openContextMenuAt(
              event.originalEvent.clientX,
              event.originalEvent.clientY,
              toLatLng(marker.x, marker.z)
            );
          });
          bundle = {
            marker: waypoint,
            tooltipHtml,
          };
          waypointBundles.set(marker.id, bundle);
        } else {
          bundle.marker.setLatLng(point);
          bundle.marker.setIcon(makeWaypointIcon(marker));
          if (bundle.tooltipHtml !== tooltipHtml) {
            bundle.marker.setTooltipContent(tooltipHtml);
            bundle.tooltipHtml = tooltipHtml;
          }
        }
      }
      for (const [id, bundle] of waypointBundles) {
        if (seenWaypoints.has(id)) {
          continue;
        }
        waypointLayer.removeLayer(bundle.marker);
        bundle.marker.remove();
        waypointBundles.delete(id);
      }
      for (const [id, bundle] of chunkLoaderBundles) {
        if (seenChunkLoaders.has(id)) {
          continue;
        }
        if (!chunkLoadVisible && !persistentChunkVisible) {
          chunkLayer.removeLayer(bundle.rectangle);
          bundle.rectangle.remove();
          chunkLoaderBundles.delete(id);
          continue;
        }
        if (bundle.fading) {
          continue;
        }
        bundle.fading = true;
        bundle.rectangle.setStyle({ opacity: 0, fillOpacity: 0 });
        bundle.fadeTimer = window.setTimeout(() => {
          chunkLayer.removeLayer(bundle.rectangle);
          bundle.rectangle.remove();
          chunkLoaderBundles.delete(id);
        }, chunkFadeDurationMs);
      }
    };

    const refreshMarkers = async () => {
      try {
        const response = await fetch(worldDetail.markersUrl, { cache: "no-store" });
        if (!response.ok || !active) {
          return;
        }
        const nextMarkers = normalizeMarkersResponse(await response.json());
        if (nextMarkers.updatedAt < latestMarkerUpdateRef.current) {
          return;
        }
        latestMarkerUpdateRef.current = nextMarkers.updatedAt;
        renderMarkers(nextMarkers.markers);
      } catch {
        // ignore transient network errors
      }
    };

    void refreshMarkers();
    const markerTimer = window.setInterval(() => void refreshMarkers(), 2_000);
    return () => {
      active = false;
      window.clearInterval(markerTimer);
      for (const bundle of waypointBundles.values()) {
        waypointLayer.removeLayer(bundle.marker);
        bundle.marker.remove();
      }
      waypointBundles.clear();
      for (const bundle of chunkLoaderBundles.values()) {
        if (bundle.fadeTimer != null) {
          window.clearTimeout(bundle.fadeTimer);
        }
        chunkLayer.removeLayer(bundle.rectangle);
        bundle.rectangle.remove();
      }
      chunkLoaderBundles.clear();
    };
  }, [worldDetail, chunkLoadVisible, persistentChunkVisible]);

  useEffect(() => {
    if (!mapRef.current || !markerLayerRef.current || !worldDetail) {
      return;
    }
    const map = mapRef.current;
    const layer = markerLayerRef.current;
    const bundles = bundlesRef.current;
    const seen = new Set<string>();

    for (const player of renderablePlayers) {
      seen.add(player.uuid);
      const latlng = toLatLng(player.x, player.z);
      let bundle = bundles.get(player.uuid);
      if (!bundle) {
        const marker = L.marker(latlng, {
          icon: makePlayerIcon(player.faceUrl),
          keyboard: false,
        }).addTo(layer);
        marker.on("click", () => setFollowUuid(player.uuid));
        marker.on("contextmenu", (event) => {
          event.originalEvent.preventDefault();
          event.originalEvent.stopPropagation();
          openContextMenuAt(
            event.originalEvent.clientX,
            event.originalEvent.clientY,
            toLatLng(player.x, player.z)
          );
        });
        if (worldDetail.playerTracker.nameplates.enabled) {
          marker.bindTooltip(playerTooltip(player, worldDetail), {
            permanent: true,
            direction: "right",
            offset: [10, 0],
            pane: "nameplate",
          });
        }
        bundle = { marker };
        bundles.set(player.uuid, bundle);
      } else {
        bundle.marker.setLatLng(latlng);
        bundle.marker.setIcon(makePlayerIcon(player.faceUrl));
        const tooltip = bundle.marker.getTooltip();
        if (tooltip) {
          tooltip.setContent(playerTooltip(player, worldDetail));
        }
      }
      if (followUuid === player.uuid) {
        map.panTo(latlng, { animate: true, duration: 0.25 });
      }
    }

    for (const [uuid, bundle] of Array.from(bundles.entries())) {
      if (seen.has(uuid)) {
        continue;
      }
      layer.removeLayer(bundle.marker);
      bundle.marker.remove();
      bundles.delete(uuid);
    }
  }, [renderablePlayers, followUuid, worldDetail]);

  const coordsLabel = coords ? `X ${coords.x} / Y ? / Z ${coords.z}` : "X --- / Y --- / Z ---";
  const sidebarShown = isMobileLayout ? mobileSidebarOpen : true;

  return (
    <>
      <div id="map" ref={mapElementRef} />
      {isMobileLayout ? (
        <button
          type="button"
          className={`mobile-hamburger${mobileSidebarOpen ? " is-open" : ""}`}
          onClick={(event) => {
            event.stopPropagation();
            setMobileSidebarOpen((value) => !value);
          }}
          aria-label={mobileSidebarOpen ? "メニューを閉じる" : "メニューを開く"}
          aria-controls="sidebar"
          aria-expanded={sidebarShown}
        >
          <span />
          <span />
          <span />
        </button>
      ) : null}
      <div
        id="sidebar"
        className={sidebarShown ? "show" : ""}
        onClick={(event) => event.stopPropagation()}
      >
        <fieldset id="worlds">
          <legend>Worlds</legend>
          {worlds.map((world) => (
            <a
              key={world.key}
              className={world.key === activeWorldKey ? "following" : ""}
              onClick={() => {
                setFollowUuid("");
                setActiveWorldKey(world.key);
                if (isMobileLayout) {
                  setMobileSidebarOpen(false);
                }
              }}
            >
              <img src={worldIcon(world.type, world.icon)} alt="" />
              <span>{world.displayName}</span>
            </a>
          ))}
        </fieldset>
        <fieldset id="players">
          <legend>Players ({activePlayers.length}/{players?.max ?? 0})</legend>
          {activePlayers.map((player) => (
            <a
              key={player.uuid}
              id={player.uuid}
              className={player.uuid === followUuid ? "following" : ""}
              onClick={() => {
                setFollowUuid(player.uuid);
                if (isMobileLayout) {
                  setMobileSidebarOpen(false);
                }
              }}
            >
              <img className="head" src={player.faceUrl} alt="" />
              <span>{player.displayName}</span>
            </a>
          ))}
        </fieldset>
      </div>
      <div className="leaflet-bottom leaflet-left">
        <div className="leaflet-control-layers coordinates">{coordsLabel}</div>
      </div>
      {contextMenu ? (
        <div
          className="map-context-menu"
          style={{
            left: Math.max(8, Math.min(window.innerWidth - 220, contextMenu.x)),
            top: Math.max(8, Math.min(window.innerHeight - 180, contextMenu.y)),
          }}
          onClick={(event) => event.stopPropagation()}
        >
          <div className="map-context-title">{copyableCoords(contextMenu.coords)}</div>
          <button
            type="button"
            onClick={async () => {
              await copyText(copyableCoords(contextMenu.coords));
              setContextMenu(null);
            }}
          >
            座標をコピー
          </button>
          <button
            type="button"
            onClick={async () => {
              await copyText(buildShareUrl());
              setContextMenu(null);
            }}
          >
            URLをコピー
          </button>
          <button
            type="button"
            onClick={() => {
              setChunkLoadVisible((value) => !value);
              setContextMenu(null);
            }}
          >
            Loaded Chunks: {chunkLoadVisible ? "ON" : "OFF"}
          </button>
          <button
            type="button"
            onClick={() => {
              setPersistentChunkVisible((value) => !value);
              setContextMenu(null);
            }}
          >
            Persistent Chunks: {persistentChunkVisible ? "ON" : "OFF"}
          </button>
        </div>
      ) : null}
      {status?.running === false ? <div className="map-warning">WebMap Offline</div> : null}
    </>
  );
}

