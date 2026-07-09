package com.luka.lbdb.benchmarkTests.productOrderTests;

public enum ProductOrderTestType {
    BIG_SMALL("SELECT * FROM big, small;", 1000),
    SMALL_BIG("SELECT * FROM small, big;", 1000);

    public final String query;
    public final int numRecords;

    ProductOrderTestType(String query, int numRecords) {
        this.query = query;
        this.numRecords = numRecords;
    }
}

// mvn test -Dgroups="BIG_SMALL" -Dbenchmark | grep --color=never -E "^[┌├│└]"
// mvn test -Dgroups="BIG_SMALL" -Dbenchmark -Dnaive | grep --color=never -E "^[┌├│└]"
// mvn test -Dgroups="SMALL_BIG" -Dbenchmark | grep --color=never -E "^[┌├│└]"
// mvn test -Dgroups="SMALL_BIG" -Dbenchmark -Dnaive | grep --color=never -E "^[┌├│└]"
