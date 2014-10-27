/*
 * Copyright 2014 Archarithms Inc.
 */

package io.bigio.benchmark.pingpong;

import io.bigio.Parameters;

/**
 *
 * @author atrimble
 */
public class PingPongConsumer {
    public static void main(String[] args) {
        Parameters.INSTANCE.setProperty("com.a2i.benchmark.role", "consumer");
        new PingPong().bootstrap().go();
    }
}