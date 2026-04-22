package com.luka.lbdb.db.settings;

/// Different buffer unpin strategies supported by the system.
public enum BufferStrategy {
    NAIVE,
    FIFO,
    LRU,
    CLOCK,
    FIRST_UNMODIFIED,
    LRM
}
