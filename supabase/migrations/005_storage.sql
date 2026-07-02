-- ============================================
-- Migración 005: Storage Bucket
-- ============================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'incident-videos',
  'incident-videos',
  false,
  104857600,
  ARRAY['video/mp4', 'video/quicktime', 'video/webm']
) ON CONFLICT (id) DO NOTHING;

CREATE POLICY "authenticated_upload_videos"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (bucket_id = 'incident-videos');

CREATE POLICY "org_members_select_videos"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'incident-videos'
    AND (storage.foldername(name))[1] IN (
      SELECT org_id::TEXT FROM public.organization_members
      WHERE user_id = auth.uid()
    )
  );

CREATE POLICY "service_all_videos"
  ON storage.objects FOR ALL
  TO service_role
  USING (bucket_id = 'incident-videos')
  WITH CHECK (bucket_id = 'incident-videos');

CREATE POLICY "authenticated_delete_own_videos"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'incident-videos'
    AND auth.uid()::TEXT = (storage.foldername(name))[2]
  );
