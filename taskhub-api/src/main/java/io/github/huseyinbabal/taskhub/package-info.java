/**
 * TaskHub REST API.
 *
 * <p>Strict layered architecture (SPEC §4): {@code Controller → Service → Repository}.
 * Controllers never touch repositories or entities directly; services never
 * return entities across the HTTP boundary — only DTOs. Code is organized
 * <em>package-by-feature</em> ({@code user}, {@code project}, {@code task},
 * {@code tag}), with cross-cutting concerns under {@code common} and
 * {@code config}. Features are added one slice per session.
 */
package io.github.huseyinbabal.taskhub;
