package io.github.huseyinbabal.taskhub.cache;

import java.util.Map;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SharedCacheMode;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T0 gate for spec/hibernate-l2-cache-hazelcast.md.
 *
 * <p>The native Hazelcast region factory ({@code hazelcast-hibernate53:5.2.0}) is built
 * against Hibernate 6.5, but this project runs Hibernate 7.4. This test proves — in
 * total isolation from Spring, Flyway and Postgres, on an in-memory H2 with an embedded
 * Hazelcast member — that the factory loads under Hibernate 7.4's {@code RegionFactory}
 * SPI and actually produces a second-level-cache hit. If it cannot, the native path is a
 * dead end for this Hibernate version and the fallback (JCache bridge) is required.
 */
class HazelcastL2CompatibilitySpikeTest {

    @Test
    void nativeRegionFactoryProducesASecondLevelCacheHitUnderHibernate7() {
        // Keep the embedded member offline during the spike.
        System.setProperty("hazelcast.phone.home.enabled", "false");

        Configuration cfg = new Configuration();
        cfg.addAnnotatedClass(CachedThing.class);
        hibernateProperties().forEach(cfg::setProperty);

        try (SessionFactory sf = cfg.buildSessionFactory()) {
            // Arrange: one row exists, caches and stats reset so counts are unambiguous.
            sf.inTransaction(session -> session.persist(new CachedThing(1L, "alpha")));
            sf.getCache().evictAllRegions();
            Statistics stats = sf.getStatistics();
            stats.clear();

            // Act: load the same id in two separate sessions. The first populates the
            // second-level cache from the DB; the second must be served from the cache.
            sf.inTransaction(session -> assertThat(session.find(CachedThing.class, 1L)).isNotNull());
            sf.inTransaction(session -> assertThat(session.find(CachedThing.class, 1L)).isNotNull());

            assertThat(stats.getSecondLevelCachePutCount())
                    .as("first load populates the L2 cache")
                    .isGreaterThan(0);
            assertThat(stats.getSecondLevelCacheHitCount())
                    .as("second load is served from the Hazelcast L2 cache, not the DB")
                    .isGreaterThan(0);
        }
    }

    private Map<String, String> hibernateProperties() {
        return Map.ofEntries(
                Map.entry(AvailableSettings.JAKARTA_JDBC_URL, "jdbc:h2:mem:l2spike;DB_CLOSE_DELAY=-1"),
                Map.entry(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver"),
                Map.entry(AvailableSettings.HBM2DDL_AUTO, "create"),
                Map.entry(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true"),
                Map.entry(AvailableSettings.CACHE_REGION_FACTORY,
                        "com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory"),
                Map.entry(AvailableSettings.JAKARTA_SHARED_CACHE_MODE,
                        SharedCacheMode.ENABLE_SELECTIVE.name()),
                Map.entry(AvailableSettings.GENERATE_STATISTICS, "true"));
    }

    @Entity(name = "CachedThing")
    @Cacheable
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    static class CachedThing {

        @Id
        Long id;

        String name;

        CachedThing() {
        }

        CachedThing(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
