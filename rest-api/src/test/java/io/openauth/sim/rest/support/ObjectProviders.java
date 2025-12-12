package io.openauth.sim.rest.support;

import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;

public final class ObjectProviders {

    private ObjectProviders() {
        throw new AssertionError("No instances");
    }

    public static <T> ObjectProvider<T> fixed(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }

            @Override
            public Stream<T> stream() {
                return instance == null ? Stream.empty() : Stream.of(instance);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }
}
