package com.luka.lbdb.benchmarkTests.productOrderTests.orders;

import com.luka.lbdb.benchmarkTests.productOrderTests.AbstractProductOrderBenchmarkTest;
import com.luka.lbdb.benchmarkTests.productOrderTests.ProductOrderTestType;
import org.junit.jupiter.api.Tag;

@Tag("SMALL_BIG")
public class SmallBigOrderTest extends AbstractProductOrderBenchmarkTest {
    public SmallBigOrderTest() {
        super(ProductOrderTestType.SMALL_BIG);
    }
}
