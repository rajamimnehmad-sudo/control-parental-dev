update storage.buckets set allowed_mime_types = array['application/json','application/vnd.android.package-archive','application/octet-stream','text/html'] where id = 'dev-updates';
