# Native Java API Reference

This directory hosts curated reference pages or indexes for the Native Java API seams. Full HTML
Javadoc for the facade entry points and their supporting helpers is generated on demand; the files
that live here are expected to stay small and focused (for example, overview pages that link to
per-protocol seams and their how-to guides).

To generate the Javadoc locally for the Native Java API:

- Run `./gradlew --no-daemon :application:nativeJavaApiJavadoc`.

This aggregation task runs both `:core:javadoc` and `:application:javadoc`:

- Core helpers and domain types: `core/build/docs/javadoc`.
- Application-level Native Java facades and telemetry adapters: `application/build/docs/javadoc`.

Publishing a trimmed index or curated HTML snapshots into ``docs/3-reference/native-java-api`/` remains
manual for now and will be wired into CI in a future increment.
