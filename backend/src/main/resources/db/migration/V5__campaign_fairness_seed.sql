-- Provably-fair commit/reveal: store the secret server seed committed at publish.
-- seed_hash (public commitment) and revealed_seed (published after sell-out) already exist on kuji_campaigns.
ALTER TABLE kuji_campaigns ADD COLUMN server_seed TEXT;
