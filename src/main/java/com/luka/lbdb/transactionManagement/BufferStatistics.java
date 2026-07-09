package com.luka.lbdb.transactionManagement;

/// Holds information about the system's current number of reads, writes and
/// hits of the buffer manager's cache.
public record BufferStatistics(int numReads, int numWrites, int numCacheHits) { }
