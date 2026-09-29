package com.pocasluces.backend.repository;

/**
 * {@link AbstractTimeZoneIndependenceTest} against H2 (the dev profile database, where
 * {@code upsert} falls back to a JPA read-then-write). Inherits the {@code @DataJpaTest}
 * setup from the base class.
 */
class TimeZoneIndependenceH2Test extends AbstractTimeZoneIndependenceTest {
}
