-- ============================================
-- Migración 006: Billing (Google Play + Wompi)
-- Tablas: products, purchases, subscriptions, billing_events, wompi_card_tokens
-- ============================================

CREATE TABLE public.products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  description TEXT,
  type TEXT NOT NULL CHECK (type IN ('subscription', 'one_time')),
  channel TEXT NOT NULL CHECK (channel IN ('google_play', 'wompi', 'manual')),
  price_cop INTEGER NOT NULL,
  currency TEXT DEFAULT 'COP',
  external_product_id TEXT,
  external_price_id TEXT,
  features JSONB DEFAULT '[]',
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE public.purchases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  org_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL,
  product_id UUID NOT NULL REFERENCES public.products(id),
  channel TEXT NOT NULL CHECK (channel IN ('google_play', 'wompi', 'manual')),
  external_id TEXT,
  status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'declined', 'refunded', 'expired')),
  amount_cop INTEGER NOT NULL,
  currency TEXT DEFAULT 'COP',
  metadata JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_purchases_user ON public.purchases(user_id);
CREATE INDEX idx_purchases_external ON public.purchases(external_id);
CREATE INDEX idx_purchases_status ON public.purchases(status);

CREATE TABLE public.subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  org_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL,
  product_id UUID NOT NULL REFERENCES public.products(id),
  status TEXT DEFAULT 'active' CHECK (status IN ('active', 'expired', 'cancelled', 'on_hold', 'grace_period')),
  current_period_start TIMESTAMPTZ,
  current_period_end TIMESTAMPTZ,
  next_billing_date TIMESTAMPTZ,
  cancel_at_period_end BOOLEAN DEFAULT false,
  cancelled_at TIMESTAMPTZ,
  wompi_card_token_id UUID,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_subscriptions_user ON public.subscriptions(user_id);
CREATE INDEX idx_subscriptions_org ON public.subscriptions(org_id);
CREATE INDEX idx_subscriptions_status ON public.subscriptions(status);

CREATE TABLE public.billing_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source TEXT NOT NULL CHECK (source IN ('google_play', 'wompi')),
  event_type TEXT NOT NULL,
  payload JSONB,
  processed BOOLEAN DEFAULT false,
  error_message TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_billing_events_source ON public.billing_events(source);
CREATE INDEX idx_billing_events_processed ON public.billing_events(processed);
CREATE INDEX idx_billing_events_created ON public.billing_events(created_at DESC);

CREATE TABLE public.wompi_card_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  wompi_token TEXT NOT NULL,
  last_four TEXT,
  brand TEXT,
  is_default BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_wompi_tokens_user ON public.wompi_card_tokens(user_id);

-- ============================================
-- RLS
-- ============================================
ALTER TABLE public.purchases ENABLE ROW LEVEL SECURITY;

CREATE POLICY "users_select_own_purchases"
  ON public.purchases FOR SELECT
  USING (user_id = auth.uid());

CREATE POLICY "service_insert_purchases"
  ON public.purchases FOR INSERT
  WITH CHECK (true);

CREATE POLICY "service_update_purchases"
  ON public.purchases FOR UPDATE
  USING (true);

ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "users_select_own_subscriptions"
  ON public.subscriptions FOR SELECT
  USING (user_id = auth.uid());

CREATE POLICY "service_insert_subscriptions"
  ON public.subscriptions FOR INSERT
  WITH CHECK (true);

CREATE POLICY "service_update_subscriptions"
  ON public.subscriptions FOR UPDATE
  USING (true);

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

CREATE POLICY "authenticated_read_products"
  ON public.products FOR SELECT
  USING (auth.uid() IS NOT NULL);

ALTER TABLE public.billing_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "service_all_billing_events"
  ON public.billing_events FOR ALL
  USING (true);

ALTER TABLE public.wompi_card_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY "users_select_own_tokens"
  ON public.wompi_card_tokens FOR SELECT
  USING (user_id = auth.uid());

CREATE POLICY "users_insert_own_tokens"
  ON public.wompi_card_tokens FOR INSERT
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "users_delete_own_tokens"
  ON public.wompi_card_tokens FOR DELETE
  USING (user_id = auth.uid());

-- ============================================
-- TRIGGERS
-- ============================================
CREATE TRIGGER set_products_updated_at
  BEFORE UPDATE ON public.products
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TRIGGER set_purchases_updated_at
  BEFORE UPDATE ON public.purchases
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TRIGGER set_subscriptions_updated_at
  BEFORE UPDATE ON public.subscriptions
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- ============================================
-- SEED DATA: Productos iniciales
-- ============================================
INSERT INTO public.products (name, description, type, channel, price_cop, external_product_id, features) VALUES
  ('Premium Mensual', 'Plan premium con funciones avanzadas', 'subscription', 'google_play', 1090000, 'premium_monthly', '["buffer_30s", "anti_sleep_advanced", "maintenance", "obd", "collision_call"]'),
  ('Fleet Mensual', 'Plan empresarial con dashboard', 'subscription', 'google_play', 990000, 'fleet_monthly', '["premium_features", "geofencing", "facial", "dashboard", "multi_vehicle"]'),
  ('Por Evento', 'Pago único por video de incidente', 'one_time', 'google_play', 1990000, 'per_event', '["video_processing", "download"]'),
  ('Instalación OBD', 'Servicio de instalación asistida', 'one_time', 'wompi', 3990000, 'obd_installation', '["video_call", "remote_setup"]'),
  ('Premium Mensual (Wompi)', 'Plan premium vía web', 'subscription', 'wompi', 1090000, 'premium_monthly_wompi', '["buffer_30s", "anti_sleep_advanced", "maintenance", "obd", "collision_call"]'),
  ('Fleet Mensual (Wompi)', 'Plan empresarial vía web', 'subscription', 'wompi', 990000, 'fleet_monthly_wompi', '["premium_features", "geofencing", "facial", "dashboard", "multi_vehicle"]')
ON CONFLICT DO NOTHING;
