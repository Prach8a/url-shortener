CREATE INDEX idx_url_mappings_short_code ON url_mappings(short_code);
CREATE INDEX idx_url_mappings_long_url ON url_mappings(long_url);
CREATE INDEX idx_url_mappings_expires_at ON url_mappings(expires_at);