ALTER TABLE brew_market.neighborhoods
    ADD COLUMN boundary public.geometry(MultiPolygon, 5179);
