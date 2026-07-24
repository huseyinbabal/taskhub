package io.github.huseyinbabal.taskhub.tag;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A label attachable to many tasks (SPEC §2). Names are unique across the system.
 *
 * <p>Second-level cached (spec/hibernate-l2-cache-hazelcast.md): tags are reference-like —
 * read often, changed rarely — and are resolved by id when hydrating a task's tag set.
 * {@code READ_WRITE} keeps a rename/recolour consistent.
 */
@Entity
@Table(name = "tags")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String color;

    protected Tag() {
        // for JPA
    }

    public Tag(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
