export type BootstrapResponse = {
  static: boolean;
  server: {
    title: string;
    statusUrl: string;
    worldsUrl: string;
    playersUrl: string;
  };
};

export type WorldSummary = {
  key: string;
  name: string;
  displayName: string;
  order: number;
  type: "normal" | "nether" | "the_end";
  environment: string;
  icon: string | null;
  chunks: number;
  tileUrl: string;
  worldUrl: string;
  spawn: {
    x: number;
    z: number;
  };
};

export type WorldDetail = {
  key: string;
  name: string;
  displayName: string;
  environment: string;
  type: "normal" | "nether" | "the_end";
  chunks: number;
  playerTracker: {
    enabled: boolean;
    nameplates: {
      enabled: boolean;
      showHeads: boolean;
      showHealth: boolean;
      showArmor: boolean;
    };
  };
  zoom: {
    default: number;
    maxNative: number;
    extra: number;
    min: number;
    max: number;
  };
  spawn: {
    x: number;
    z: number;
  };
  backgroundImage: string;
  tileTemplate: string;
  changesUrl: string;
  markersUrl: string;
};

export type WorldMarker = {
  id: string;
  kind: "waypoint" | "loadedchunk" | "persistentchunk";
  name: string;
  displayName: string;
  color: string;
  x: number;
  z: number;
  chunkX: number;
  chunkZ: number;
  rotation: number;
};

export type WorldMarkersResponse = {
  markers: WorldMarker[];
  updatedAt: number;
};

export type PlayerEntry = {
  name: string;
  displayName: string;
  uuid: string;
  worldKey: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
  chunkReady: boolean;
  faceUrl: string;
  health: number;
  armor: number;
};

export type PlayersResponse = {
  players: PlayerEntry[];
  max: number;
  updatedAt: number;
};

export type StatusResponse = {
  running: boolean;
  globallyEnabled: boolean;
  paused: boolean;
  publicAccess: boolean;
  port: number;
  url: string;
  playerTracking: boolean;
  chunkGenPerTick: number;
};
