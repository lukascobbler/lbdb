package com.luka.lbdb.benchmarkTests.bufferStrategyTests;

public enum BufferTestType {
    HOT_BUFFERS("SELECT * FROM table1, table2;", 1000, 50),
    CONTINUOUS_READS("SELECT * FROM table1;", 50000, 0),
    HOT_BUFFERS_M("SELECT * FROM table1, table2;", 1000, 50),
    CONTINUOUS_READS_M("SELECT * FROM table1;", 50000, 0);

    public final String query;
    public final int numRecords1;
    public final int numRecords2;

    BufferTestType(String query, int numRecords1, int numRecords2) {
        this.query = query;
        this.numRecords1 = numRecords1;
        this.numRecords2 = numRecords2;
    }
}

// mvn test -Dgroups="HOT_BUFFERS" -Dbenchmark | grep --color=never -E "^[┌├│└]"
// mvn test -Dgroups="CONTINUOUS_READS" -Dbenchmark | grep --color=never -E "^[┌├│└]"
// mvn test -Dgroups="HOT_BUFFERS_M" -Dbenchmark | grep --color=never -E "^[┌├│└]"
// mvn test -Dgroups="CONTINUOUS_READS_M" -Dbenchmark | grep --color=never -E "^[┌├│└]"
