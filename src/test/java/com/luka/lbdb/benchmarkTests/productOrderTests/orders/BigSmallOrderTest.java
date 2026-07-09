package com.luka.lbdb.benchmarkTests.productOrderTests.orders;

import com.luka.lbdb.benchmarkTests.productOrderTests.AbstractProductOrderBenchmarkTest;
import com.luka.lbdb.benchmarkTests.productOrderTests.ProductOrderTestType;
import org.junit.jupiter.api.Tag;

@Tag("BIG_SMALL")
public class BigSmallOrderTest extends AbstractProductOrderBenchmarkTest {
    public BigSmallOrderTest() {
        super(ProductOrderTestType.BIG_SMALL);
    }
}
