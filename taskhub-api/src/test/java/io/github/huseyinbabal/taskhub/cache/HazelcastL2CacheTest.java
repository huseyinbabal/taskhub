package io.github.huseyinbabal.taskhub.cache;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.huseyinbabal.taskhub.project.Project;
import io.github.huseyinbabal.taskhub.tag.Tag;
import io.github.huseyinbabal.taskhub.task.Task;
import io.github.huseyinbabal.taskhub.task.TaskPriority;
import io.github.huseyinbabal.taskhub.task.TaskStatus;
import io.github.huseyinbabal.taskhub.user.Role;
import io.github.huseyinbabal.taskhub.user.User;
import jakarta.persistence.SharedCacheMode;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour of the Hibernate second-level cache over the real TaskHub entities
 * (spec/hibernate-l2-cache-hazelcast.md). Each test runs against a fresh in-memory H2 with
 * an <em>embedded</em> Hazelcast member ({@code HazelcastLocalCacheRegionFactory}), so
 * {@code ./mvnw verify} needs no Docker and the tests are fully isolated from one another;
 * the client/server path is verified live in T5.
 *
 * <p>The first test doubles as the Hibernate-7 compatibility guard: it fails fast if the
 * {@code hazelcast-hibernate53} region factory (built against Hibernate 6.5) stops loading
 * under this project's Hibernate 7.x — the risk that gated T0.
 */
class HazelcastL2CacheTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private SessionFactory sessionFactory;

    @BeforeEach
    void buildSessionFactory() {
        System.setProperty("hazelcast.phone.home.enabled", "false");

        Configuration cfg = new Configuration();
        cfg.addAnnotatedClass(User.class);
        cfg.addAnnotatedClass(Project.class);
        cfg.addAnnotatedClass(Task.class);
        cfg.addAnnotatedClass(Tag.class);
        // A fresh in-memory schema per test keeps rows from leaking between tests.
        hibernateProperties("l2cache_" + DB_SEQ.incrementAndGet()).forEach(cfg::setProperty);
        this.sessionFactory = cfg.buildSessionFactory();
    }

    @AfterEach
    void closeSessionFactory() {
        if (this.sessionFactory != null) {
            this.sessionFactory.close();
        }
    }

    @Test
    void regionFactoryLoadsAndCachesUnderHibernate7() {
        long userId = persistUser("compat");
        find(User.class, userId); // load from DB, populate the L2 cache

        Statistics stats = statsAfterClear();
        find(User.class, userId);

        assertThat(stats.getSecondLevelCacheHitCount())
                .as("the native region factory serves the second read from Hazelcast")
                .isGreaterThan(0);
    }

    @Test
    void entityIsServedFromTheSecondLevelCacheOnTheSecondRead() {
        long userId = persistUser("hit");
        find(User.class, userId); // first read populates the cache

        Statistics stats = statsAfterClear();
        User second = find(User.class, userId);

        assertThat(second.getUsername()).isEqualTo("hit");
        assertThat(stats.getSecondLevelCacheHitCount()).as("second read is a cache hit").isGreaterThan(0);
        assertThat(stats.getEntityLoadCount()).as("no DB entity load on the cached read").isZero();
    }

    @Test
    void aMutationInvalidatesTheCachedEntity() {
        long userId = persistUser("before");
        find(User.class, userId); // warm the cache

        this.sessionFactory.inTransaction(session -> find(session, User.class, userId).setUsername("after"));

        assertThat(find(User.class, userId).getUsername())
                .as("no stale read after a committed mutation").isEqualTo("after");
    }

    @Test
    void aTasksTagCollectionIsCached() {
        long taskId = persistTaskWithTags("release", "urgent", "backend");
        this.sessionFactory.inTransaction(session -> find(session, Task.class, taskId).getTags().size()); // warm

        Statistics stats = statsAfterClear();
        this.sessionFactory.inTransaction(session ->
                assertThat(find(session, Task.class, taskId).getTags()).hasSize(2));

        assertThat(stats.getSecondLevelCacheHitCount())
                .as("task + its tag collection served from cache, no re-query").isGreaterThan(0);
    }

    @Test
    void aCacheableQueryIsServedFromTheQueryCache() {
        persistUser("q1");
        persistUser("q2");
        settleUpdateTimestampWindow();
        runCacheableUserQuery(); // populates a now-valid query-cache entry

        Statistics stats = statsAfterClear();
        runCacheableUserQuery();

        assertThat(stats.getQueryCacheHitCount())
                .as("second run of the same cacheable query is a hit").isGreaterThan(0);
    }

    @Test
    void writingTheTableInvalidatesACachedQuery() {
        persistUser("initial");
        runCacheableUserQuery();

        // A write to the users table bumps its update timestamp, invalidating the query.
        persistUser("added");

        Statistics stats = statsAfterClear();
        long results = runCacheableUserQuery();

        assertThat(results).as("the freshly inserted row is visible").isEqualTo(2);
        assertThat(stats.getQueryCacheMissCount())
                .as("the cached query was invalidated by the write and re-executed").isGreaterThan(0);
    }

    // --- helpers -------------------------------------------------------------

    private long runCacheableUserQuery() {
        return this.sessionFactory.fromTransaction(session -> (long) session
                .createQuery("from User order by id", User.class)
                .setCacheable(true)
                .getResultList()
                .size());
    }

    private long persistUser(String username) {
        return this.sessionFactory.fromTransaction(session -> {
            User user = new User(username + "@example.com", username, "hash", Set.of(Role.USER));
            session.persist(user);
            return user.getId();
        });
    }

    private long persistTaskWithTags(String title, String... tagNames) {
        return this.sessionFactory.fromTransaction(session -> {
            User owner = new User(title + "-owner@example.com", title + "-owner", "hash", Set.of(Role.USER));
            session.persist(owner);
            Project project = new Project(title + "-project", "desc", owner);
            session.persist(project);
            Task task = new Task(title, "desc", TaskStatus.TODO, TaskPriority.HIGH, null, project, owner);
            for (String tagName : tagNames) {
                Tag tag = new Tag(tagName, "#000000");
                session.persist(tag);
                task.getTags().add(tag);
            }
            session.persist(task);
            return task.getId();
        });
    }

    private <T> T find(Class<T> type, long id) {
        return this.sessionFactory.fromTransaction(session -> find(session, type, id));
    }

    private <T> T find(Session session, Class<T> type, long id) {
        return session.find(type, id);
    }

    /**
     * Waits past the write-invalidation window. Hibernate marks a table's cached queries
     * invalid until real time passes the write's update timestamp (millisecond resolution),
     * so a query cached in the same instant as the setup writes is treated as stale. A short
     * settle guarantees the following query caches as valid.
     */
    private void settleUpdateTimestampWindow() {
        try {
            Thread.sleep(200);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Clears statistics so the next assertion measures only the action under test. */
    private Statistics statsAfterClear() {
        Statistics stats = this.sessionFactory.getStatistics();
        stats.clear();
        return stats;
    }

    private Map<String, String> hibernateProperties(String dbName) {
        return Map.ofEntries(
                Map.entry(AvailableSettings.JAKARTA_JDBC_URL, "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1"),
                Map.entry(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver"),
                Map.entry(AvailableSettings.HBM2DDL_AUTO, "create"),
                Map.entry(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true"),
                Map.entry(AvailableSettings.USE_QUERY_CACHE, "true"),
                Map.entry(AvailableSettings.CACHE_REGION_FACTORY,
                        "com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory"),
                Map.entry(AvailableSettings.JAKARTA_SHARED_CACHE_MODE,
                        SharedCacheMode.ENABLE_SELECTIVE.name()),
                Map.entry(AvailableSettings.GENERATE_STATISTICS, "true"));
    }
}
