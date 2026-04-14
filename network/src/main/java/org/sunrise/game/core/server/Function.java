package org.sunrise.game.core.server;

public interface Function<R, T> {
    R apply(T t);
}
