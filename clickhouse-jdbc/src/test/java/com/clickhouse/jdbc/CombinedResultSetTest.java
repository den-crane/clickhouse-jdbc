package com.clickhouse.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import com.clickhouse.client.ClickHouseColumn;
import com.clickhouse.client.ClickHouseConfig;
import com.clickhouse.client.data.ClickHouseSimpleResponse;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CombinedResultSetTest {
    @DataProvider(name = "multipleResultSetsProvider")
    private Object[][] getMultipleResultSets() {
        ClickHouseConfig config = new ClickHouseConfig();
        return new Object[][] {
                { new CombinedResultSet(null, new ClickHouseResultSet("", "",
                        ClickHouseSimpleResponse.of(config, ClickHouseColumn.parse("s String"),
                                new Object[][] { new Object[] { "a" },
                                        new Object[] { "b" } })),
                        new ClickHouseResultSet("", "",
                                ClickHouseSimpleResponse.of(config,
                                        ClickHouseColumn.parse("s String"),
                                        new Object[][] { new Object[] { "c" },
                                                new Object[] { "d" },
                                                new Object[] { "e" } }))) },
                { new CombinedResultSet(Arrays.asList(null, null,
                        new ClickHouseResultSet("", "",
                                ClickHouseSimpleResponse.of(config,
                                        ClickHouseColumn.parse("s String"),
                                        new Object[][] { new Object[] {
                                                "a" } })),
                        null,
                        new ClickHouseResultSet("", "",
                                ClickHouseSimpleResponse.of(config,
                                        ClickHouseColumn.parse("s String"),
                                        new Object[][] { new Object[] {
                                                "b" } })),
                        new ClickHouseResultSet("", "",
                                ClickHouseSimpleResponse.of(config,
                                        ClickHouseColumn.parse("s String"),
                                        new Object[][] {
                                                new Object[] { "c" },
                                                new Object[] { "d" },
                                                new Object[] { "e" } })))) } };
    }

    @DataProvider(name = "nullOrEmptyResultSetProvider")
    private Object[][] getNullOrEmptyResultSet() {
        return new Object[][] { { new CombinedResultSet() }, { new CombinedResultSet((ResultSet) null) },
                { new CombinedResultSet(null, null) }, { new CombinedResultSet(null, null, null) },
                { new CombinedResultSet(Collections.emptyList()) },
                { new CombinedResultSet(Collections.singleton(null)) },
                { new CombinedResultSet(Arrays.asList(null, null)) },
                { new CombinedResultSet(Arrays.asList(null, null, null)) } };
    }

    @DataProvider(name = "singleResultSetProvider")
    private Object[][] getSingleResultSet() {
        ClickHouseConfig config = new ClickHouseConfig();
        return new Object[][] {
                { new CombinedResultSet(new ClickHouseResultSet("", "",
                        ClickHouseSimpleResponse.of(config, ClickHouseColumn.parse("s String"),
                                new Object[][] { new Object[] { "a" },
                                        new Object[] { "b" } }))) },
                { new CombinedResultSet(Collections.singleton(
                        new ClickHouseResultSet("", "", ClickHouseSimpleResponse.of(config,
                                ClickHouseColumn.parse("s String"),
                                new Object[][] { new Object[] { "a" },
                                        new Object[] { "b" } })))) } };
    }

    @Test(dataProvider = "multipleResultSetsProvider", groups = "unit")
    public void testMultipleResultSets(CombinedResultSet combined) throws SQLException {
        Assert.assertFalse(combined.isClosed());
        Assert.assertEquals(combined.getRow(), 0);
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 1);
        Assert.assertEquals(combined.getString(1), "a");
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 2);
        Assert.assertEquals(combined.getString(1), "b");
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 3);
        Assert.assertEquals(combined.getString(1), "c");
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 4);
        Assert.assertEquals(combined.getString(1), "d");
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 5);
        Assert.assertEquals(combined.getString(1), "e");
        Assert.assertFalse(combined.next());
        Assert.assertFalse(combined.next());
        Assert.assertEquals(combined.getRow(), 5);
        combined.close();
        Assert.assertTrue(combined.isClosed());
    }

    @Test(dataProvider = "nullOrEmptyResultSetProvider", groups = "unit")
    public void testNullAndEmptyResultSet(CombinedResultSet combined) throws SQLException {
        Assert.assertFalse(combined.isClosed());
        Assert.assertEquals(combined.getRow(), 0);
        Assert.assertFalse(combined.next());
        Assert.assertEquals(combined.getRow(), 0);
        Assert.assertFalse(combined.next());
        Assert.assertEquals(combined.getRow(), 0);
        combined.close();
        Assert.assertTrue(combined.isClosed());
        Assert.assertThrows(SQLException.class, () -> combined.getString(1));
    }

    @Test(dataProvider = "singleResultSetProvider", groups = "unit")
    public void testSingleResultSet(CombinedResultSet combined) throws SQLException {
        Assert.assertFalse(combined.isClosed());
        Assert.assertEquals(combined.getRow(), 0);
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 1);
        Assert.assertEquals(combined.getString(1), "a");
        Assert.assertTrue(combined.next());
        Assert.assertEquals(combined.getRow(), 2);
        Assert.assertEquals(combined.getString(1), "b");
        Assert.assertFalse(combined.next());
        Assert.assertFalse(combined.next());
        Assert.assertEquals(combined.getRow(), 2);
        combined.close();
        Assert.assertTrue(combined.isClosed());
    }
}
