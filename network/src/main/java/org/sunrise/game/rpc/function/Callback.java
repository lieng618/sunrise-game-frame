package org.sunrise.game.rpc.function;

public interface Callback<T> {
    void process(T t);
}
