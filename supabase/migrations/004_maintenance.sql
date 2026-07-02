-- ============================================
-- Migración 004: Mantenimiento Predictivo
-- Tablas: maintenance_rules, odometer_logs, obd_readings, maintenance_alerts
-- ============================================

CREATE TABLE public.maintenance_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  brand TEXT NOT NULL,
  model TEXT NOT NULL,
  year_from INTEGER,
  year_to INTEGER,
  component TEXT NOT NULL,
  interval_km INTEGER NOT NULL,
  interval_months INTEGER,
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_maintenance_rules_brand ON public.maintenance_rules(brand, model);

CREATE TABLE public.odometer_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_id UUID NOT NULL REFERENCES public.vehicles(id) ON DELETE CASCADE,
  km_reading NUMERIC NOT NULL,
  source TEXT DEFAULT 'gps' CHECK (source IN ('gps', 'obd', 'manual')),
  recorded_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_odometer_vehicle ON public.odometer_logs(vehicle_id);
CREATE INDEX idx_odometer_recorded ON public.odometer_logs(recorded_at DESC);

CREATE TABLE public.obd_readings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_id UUID NOT NULL REFERENCES public.vehicles(id) ON DELETE CASCADE,
  dtc_code TEXT,
  rpm INTEGER,
  coolant_temp INTEGER,
  battery_voltage NUMERIC,
  fuel_level NUMERIC,
  engine_load NUMERIC,
  recorded_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_obd_vehicle ON public.obd_readings(vehicle_id);
CREATE INDEX idx_obd_recorded ON public.obd_readings(recorded_at DESC);

CREATE TABLE public.maintenance_alerts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_id UUID NOT NULL REFERENCES public.vehicles(id) ON DELETE CASCADE,
  org_id UUID NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
  rule_id UUID REFERENCES public.maintenance_rules(id) ON DELETE SET NULL,
  component TEXT NOT NULL,
  km_remaining NUMERIC,
  days_remaining INTEGER,
  severity TEXT DEFAULT 'info' CHECK (severity IN ('info', 'warning', 'critical')),
  status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'acknowledged', 'resolved')),
  created_at TIMESTAMPTZ DEFAULT now(),
  resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_maintenance_alerts_vehicle ON public.maintenance_alerts(vehicle_id);
CREATE INDEX idx_maintenance_alerts_org ON public.maintenance_alerts(org_id);
CREATE INDEX idx_maintenance_alerts_status ON public.maintenance_alerts(status);

-- ============================================
-- RLS
-- ============================================
ALTER TABLE public.maintenance_alerts ENABLE ROW LEVEL SECURITY;

CREATE POLICY "members_select_maintenance_alerts"
  ON public.maintenance_alerts FOR SELECT
  USING (
    org_id IN (
      SELECT org_id FROM public.organization_members
      WHERE user_id = auth.uid()
    )
  );

CREATE POLICY "service_insert_maintenance_alerts"
  ON public.maintenance_alerts FOR INSERT
  WITH CHECK (true);

CREATE POLICY "service_update_maintenance_alerts"
  ON public.maintenance_alerts FOR UPDATE
  USING (true);

ALTER TABLE public.maintenance_rules ENABLE ROW LEVEL SECURITY;

CREATE POLICY "authenticated_read_rules"
  ON public.maintenance_rules FOR SELECT
  USING (auth.uid() IS NOT NULL);

ALTER TABLE public.odometer_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "service_insert_odometer"
  ON public.odometer_logs FOR INSERT
  WITH CHECK (true);

ALTER TABLE public.obd_readings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "service_insert_obd"
  ON public.obd_readings FOR INSERT
  WITH CHECK (true);
