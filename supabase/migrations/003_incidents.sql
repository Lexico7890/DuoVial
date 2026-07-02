-- ============================================
-- Migración 003: Incidents + Geofencing + Telemetry
-- Tablas: incidents, geofence_events, vehicle_telemetry
-- ============================================

CREATE TABLE public.incidents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
  vehicle_id UUID REFERENCES public.vehicles(id) ON DELETE SET NULL,
  driver_id UUID REFERENCES public.drivers(id) ON DELETE SET NULL,
  trigger_type TEXT NOT NULL CHECK (trigger_type IN ('panic', 'accel', 'collision', 'geofence')),
  video_path TEXT,
  mux_asset_id TEXT,
  mux_playback_id TEXT,
  streaming_url TEXT,
  status TEXT DEFAULT 'uploading' CHECK (status IN ('uploading', 'processing', 'ready', 'error')),
  g_force NUMERIC,
  speed_kmh NUMERIC,
  location GEOGRAPHY(POINT, 4326),
  notes TEXT,
  processed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_incidents_org ON public.incidents(org_id);
CREATE INDEX idx_incidents_vehicle ON public.incidents(vehicle_id);
CREATE INDEX idx_incidents_created ON public.incidents(created_at DESC);
CREATE INDEX idx_incidents_status ON public.incidents(status);

CREATE TABLE public.geofence_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
  vehicle_id UUID REFERENCES public.vehicles(id) ON DELETE SET NULL,
  driver_id UUID REFERENCES public.drivers(id) ON DELETE SET NULL,
  fence_name TEXT NOT NULL,
  event_type TEXT NOT NULL CHECK (event_type IN ('enter', 'exit')),
  location GEOGRAPHY(POINT, 4326),
  speed_kmh NUMERIC,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_geofence_events_org ON public.geofence_events(org_id);
CREATE INDEX idx_geofence_events_created ON public.geofence_events(created_at DESC);

CREATE TABLE public.vehicle_telemetry (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES public.vehicles(id) ON DELETE CASCADE,
  driver_id UUID REFERENCES public.drivers(id) ON DELETE SET NULL,
  latitude NUMERIC,
  longitude NUMERIC,
  location GEOGRAPHY(POINT, 4326),
  speed_kmh NUMERIC,
  g_force NUMERIC,
  heading NUMERIC,
  battery_level INTEGER,
  storage_free_mb INTEGER,
  device_temp_celsius NUMERIC,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_telemetry_vehicle ON public.vehicle_telemetry(vehicle_id);
CREATE INDEX idx_telemetry_created ON public.vehicle_telemetry(created_at DESC);

ALTER PUBLICATION supabase_realtime ADD TABLE public.vehicle_telemetry;

-- ============================================
-- RLS
-- ============================================
ALTER TABLE public.incidents ENABLE ROW LEVEL SECURITY;

CREATE POLICY "members_select_incidents"
  ON public.incidents FOR SELECT
  USING (
    org_id IN (
      SELECT org_id FROM public.organization_members
      WHERE user_id = auth.uid()
    )
  );

CREATE POLICY "service_insert_incidents"
  ON public.incidents FOR INSERT
  WITH CHECK (true);

CREATE POLICY "service_update_incidents"
  ON public.incidents FOR UPDATE
  USING (true);

ALTER TABLE public.geofence_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "members_select_geofence_events"
  ON public.geofence_events FOR SELECT
  USING (
    org_id IN (
      SELECT org_id FROM public.organization_members
      WHERE user_id = auth.uid()
    )
  );

CREATE POLICY "service_insert_geofence_events"
  ON public.geofence_events FOR INSERT
  WITH CHECK (true);

ALTER TABLE public.vehicle_telemetry ENABLE ROW LEVEL SECURITY;

CREATE POLICY "members_select_telemetry"
  ON public.vehicle_telemetry FOR SELECT
  USING (
    org_id IN (
      SELECT org_id FROM public.organization_members
      WHERE user_id = auth.uid()
    )
  );

CREATE POLICY "service_insert_telemetry"
  ON public.vehicle_telemetry FOR INSERT
  WITH CHECK (true);
