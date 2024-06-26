package ru.yandex.clickhouse.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ClickHouseVersionNumberUtilTest {

    @Test(groups = "unit")
    public void testMajorNull() {
        try {
            ClickHouseVersionNumberUtil.getMajorVersion(null);
            Assert.fail();
        } catch (NullPointerException npe) {
            /* expected */ }
    }

    @Test(groups = "unit")
    public void testMinorNull() {
        try {
            ClickHouseVersionNumberUtil.getMinorVersion(null);
            Assert.fail();
        } catch (NullPointerException npe) {
            /* expected */ }
    }

    @Test(groups = "unit")
    public void testMajorGarbage() {
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion(""));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion("  \t"));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion("  \n "));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion("."));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion(". . "));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion("F.O.O"));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMajorVersion("42.foo"));
    }

    @Test(groups = "unit")
    public void testMajorSimple() {
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMajorVersion("1.0"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMajorVersion("1.0.42"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMajorVersion("23.42"), 23);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMajorVersion("1.0.foo"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMajorVersion("   1.0"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMajorVersion("1.0-SNAPSHOT"), 1);
    }

    @Test(groups = "unit")
    public void testMinorGarbage() {
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion(""));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion("  \t"));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion("  \n "));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion("."));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion(". . "));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion("F.O.O"));
        Assert.assertEquals(0, ClickHouseVersionNumberUtil.getMinorVersion("42.foo"));
    }

    @Test(groups = "unit")
    public void testMinorSimple() {
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMinorVersion("0.1"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMinorVersion("42.1.42"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMinorVersion("1.42.foo"), 42);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMinorVersion("   1.1"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.getMinorVersion("1.1-SNAPSHOT"), 1);
    }

    @Test(groups = "unit")
    public void testCompare() {
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("1", "1"), 0);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("21.3", "21.12"), -1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("21 .3", "21. 12"), -1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("21 .3", "21. 12-test"), -1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("21.3", "21.1"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("21. 3", "21 .1"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("21. 3-test", "21 .1"), 1);
        Assert.assertEquals(ClickHouseVersionNumberUtil.compare("18.16", "18.16.0"), 0);
    }
}
