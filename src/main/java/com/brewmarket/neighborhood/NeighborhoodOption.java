package com.brewmarket.neighborhood;

import java.time.Instant;

public record NeighborhoodOption(Long id, String name, Instant expiresAt) {}
