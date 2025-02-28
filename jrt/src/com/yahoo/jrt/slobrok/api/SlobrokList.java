// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class SlobrokList {

    private final Internal internal;
    private String[] slobroks;
    private int idx = 0;

    public SlobrokList() {
        this.internal = new Internal();
    }

    public SlobrokList(SlobrokList sibling) {
        this.internal = sibling.internal;
    }

    private void checkUpdate() {
        synchronized (internal) {
            if (slobroks != internal.slobroks) {
                slobroks = internal.slobroks;
                idx = 0;
            }
        }
    }

    public String nextSlobrokSpec() {
        checkUpdate();
        if (idx < slobroks.length) {
            return slobroks[idx++];
        }
        idx = 0;
        return null;
    }

    public void setup(String[] slobroks) {
        internal.setup(slobroks);
    }

    public int length() {
        return internal.length();
    }

    public boolean contains(String slobrok) {
        checkUpdate();
        for (String s : slobroks) {
            if (s.equals(slobrok)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return internal.toString();
    }

    private static class Internal {

        String[] slobroks = new String[0];

        void setup(String[] slobroks) {
            String[] next = new String[slobroks.length];
            for (int i = 0; i < slobroks.length; i++) {
                next[i] = slobroks[i];
            }
            for (int i = 0; i + 1 < next.length; i++) {
                int lim = next.length - i;
                int x = ThreadLocalRandom.current().nextInt(lim);
                if (x != 0) {
                    String tmp = next[i];
                    next[i] = next[i+x];
                    next[i+x] = tmp;
                }
            }
            synchronized (this) {
                this.slobroks = next;
            }
        }

        synchronized int length() {
            return slobroks.length;
        }

        @Override
        public synchronized String toString() {
            return Arrays.toString(slobroks);
        }
    }

}
