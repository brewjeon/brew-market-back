package com.brewmarket.neighborhood;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NeighborhoodRepository extends JpaRepository<Neighborhood, Long> {
    @Query(value = """
            SELECT id FROM brew_market.neighborhoods
            WHERE public.ST_Contains(boundary, public.ST_Transform(
                public.ST_SetSRID(public.ST_MakePoint(:longitude, :latitude), 4326), 5179
            ))
            ORDER BY id
            LIMIT 2
            """, nativeQuery = true)
    List<Long> findContaining(@Param("longitude") double longitude,
                              @Param("latitude") double latitude);
}
