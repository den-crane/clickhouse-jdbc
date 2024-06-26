package ru.yandex.clickhouse;

import java.net.SocketException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import ru.yandex.clickhouse.except.ClickHouseException;
import ru.yandex.clickhouse.settings.ClickHouseProperties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class BalancedClickhouseDataSourceTest extends JdbcIntegrationTest {

    private BalancedClickhouseDataSource dataSource;
    private BalancedClickhouseDataSource doubleDataSource;

    @Test(groups = "unit")
    public void testUrlSplit() throws Exception {
        assertEquals(Arrays.asList("jdbc:clickhouse://localhost:1234/ppc"),
                BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://localhost:1234/ppc"));

        assertEquals(Arrays.asList("jdbc:clickhouse://localhost:1234/ppc",
                "jdbc:clickhouse://another.host.com:4321/ppc"),
                BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://localhost:1234,another.host.com:4321/ppc"));

        assertEquals(Arrays.asList("jdbc:clickhouse://localhost:1234", "jdbc:clickhouse://another.host.com:4321"),
                BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://localhost:1234,another.host.com:4321"));

    }


    @Test(groups = "unit")
    public void testUrlSplitValidHostName() throws Exception {
        assertEquals(Arrays.asList("jdbc:clickhouse://localhost:1234", "jdbc:clickhouse://_0another-host.com:4321"),
                BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://localhost:1234,_0another-host.com:4321"));

    }


    @Test(groups = "unit", expectedExceptions = IllegalArgumentException.class)
    public void testUrlSplitInvalidHostName() throws Exception {
        BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://localhost:1234,_0ano^ther-host.com:4321");

    }

    @BeforeClass(groups = "integration")
    public void setUp() throws Exception {
        dataSource = newBalancedDataSource();
        String address = getClickHouseHttpAddress();
        doubleDataSource = newBalancedDataSource(address, address);
    }

    @Test(groups = "integration")
    public void testSingleDatabaseConnection() throws Exception {
        Connection connection = dataSource.getConnection();
        connection.createStatement().execute("CREATE DATABASE IF NOT EXISTS test");

        connection.createStatement().execute("DROP TABLE IF EXISTS test.insert_test");
        connection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS test.insert_test (i Int32, s String) ENGINE = TinyLog"
        );
        PreparedStatement statement = connection.prepareStatement("INSERT INTO test.insert_test (s, i) VALUES (?, ?)");
        statement.setString(1, "asd");
        statement.setInt(2, 42);
        statement.execute();


        ResultSet rs = connection.createStatement().executeQuery("SELECT * from test.insert_test");
        rs.next();

        assertEquals("asd", rs.getString("s"));
        assertEquals(42, rs.getInt("i"));
    }

    @Test(groups = "integration")
    public void testDoubleDatabaseConnection() throws Exception {
        Connection connection = doubleDataSource.getConnection();
        connection.createStatement().execute("CREATE DATABASE IF NOT EXISTS test");
        connection = doubleDataSource.getConnection();
        connection.createStatement().execute("DROP TABLE IF EXISTS test.insert_test");
        connection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS test.insert_test (i Int32, s String) ENGINE = TinyLog"
        );

        connection = doubleDataSource.getConnection();

        PreparedStatement statement = connection.prepareStatement("INSERT INTO test.insert_test (s, i) VALUES (?, ?)");
        statement.setString(1, "asd");
        statement.setInt(2, 42);
        statement.execute();

        ResultSet rs = connection.createStatement().executeQuery("SELECT * from test.insert_test");
        rs.next();

        assertEquals("asd", rs.getString("s"));
        assertEquals(42, rs.getInt("i"));

        connection = doubleDataSource.getConnection();

        statement = connection.prepareStatement("INSERT INTO test.insert_test (s, i) VALUES (?, ?)");
        statement.setString(1, "asd");
        statement.setInt(2, 42);
        statement.execute();

        rs = connection.createStatement().executeQuery("SELECT * from test.insert_test");
        rs.next();

        assertEquals("asd", rs.getString("s"));
        assertEquals(42, rs.getInt("i"));

    }

    @Test(groups = "integration")
    public void testCorrectActualizationDatabaseConnection() throws Exception {
        dataSource.actualize();
        Connection connection = dataSource.getConnection();
    }


    @Test(groups = "integration")
    public void testDisableConnection() throws Exception {
        BalancedClickhouseDataSource badDatasource = newBalancedDataSource("not.existed.url:8123");
        badDatasource.actualize();
        try {
            Connection connection = badDatasource.getConnection();
            fail();
        } catch (Exception e) {
            // There is no enabled connections
        }
    }


    @Test(groups = "integration")
    public void testWorkWithEnabledUrl() throws Exception {
        BalancedClickhouseDataSource halfDatasource = newBalancedDataSource("not.existed.url:8123", getClickHouseHttpAddress());

        halfDatasource.actualize();
        Connection connection = halfDatasource.getConnection();

        connection.createStatement().execute("CREATE DATABASE IF NOT EXISTS test");
        connection = halfDatasource.getConnection();
        connection.createStatement().execute("DROP TABLE IF EXISTS test.insert_test");
        connection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS test.insert_test (i Int32, s String) ENGINE = TinyLog"
        );

        connection = halfDatasource.getConnection();

        PreparedStatement statement = connection.prepareStatement("INSERT INTO test.insert_test (s, i) VALUES (?, ?)");
        statement.setString(1, "asd");
        statement.setInt(2, 42);
        statement.execute();

        ResultSet rs = connection.createStatement().executeQuery("SELECT * from test.insert_test");
        rs.next();

        assertEquals("asd", rs.getString("s"));
        assertEquals(42, rs.getInt("i"));

        connection = halfDatasource.getConnection();

        statement = connection.prepareStatement("INSERT INTO test.insert_test (s, i) VALUES (?, ?)");
        statement.setString(1, "asd");
        statement.setInt(2, 42);
        statement.execute();

        rs = connection.createStatement().executeQuery("SELECT * from test.insert_test");
        rs.next();

        assertEquals("asd", rs.getString("s"));
        assertEquals(42, rs.getInt("i"));
    }

    @Test(groups = "integration")
    public void testConstructWithClickHouseProperties() {
        final ClickHouseProperties properties = new ClickHouseProperties();
        properties.setMaxThreads(3);
        properties.setSocketTimeout(67890);
        properties.setPassword("888888");
        //without connection parameters
        String hostAddr = getClickHouseHttpAddress();
        String ipAddr   = getClickHouseHttpAddress(true);
        BalancedClickhouseDataSource dataSource = newBalancedDataSourceWithSuffix(
            "click", properties, hostAddr, ipAddr);
        ClickHouseProperties dataSourceProperties = dataSource.getProperties();
        assertEquals(dataSourceProperties.getMaxThreads().intValue(), 3);
        assertEquals(dataSourceProperties.getSocketTimeout(), 67890);
        assertEquals(dataSourceProperties.getPassword(), "888888");
        assertEquals(dataSourceProperties.getDatabase(), "click");
        assertEquals(2, dataSource.getAllClickhouseUrls().size());
        assertEquals(dataSource.getAllClickhouseUrls().get(0), "jdbc:clickhouse://" + hostAddr + "/click");
        assertEquals(dataSource.getAllClickhouseUrls().get(1), "jdbc:clickhouse://" + ipAddr + "/click");
        // with connection parameters
        dataSource = newBalancedDataSourceWithSuffix(
                "click?socket_timeout=12345&user=readonly", properties, hostAddr, ipAddr);
        dataSourceProperties = dataSource.getProperties();
        assertEquals(dataSourceProperties.getMaxThreads().intValue(), 3);
        assertEquals(dataSourceProperties.getSocketTimeout(), 12345);
        assertEquals(dataSourceProperties.getUser(), "readonly");
        assertEquals(dataSourceProperties.getPassword(), "888888");
        assertEquals(dataSourceProperties.getDatabase(), "click");
        assertEquals(2, dataSource.getAllClickhouseUrls().size());
        assertEquals(dataSource.getAllClickhouseUrls().get(0), "jdbc:clickhouse://" + hostAddr + "/click?socket_timeout" +
                "=12345&user=readonly");
        assertEquals(dataSource.getAllClickhouseUrls().get(1), "jdbc:clickhouse://" + ipAddr + "/click?socket_timeout=12345&user=readonly");
    }

    @Test(groups = "integration")
    public void testConnectionWithAuth() throws SQLException {
        final ClickHouseProperties properties = new ClickHouseProperties();
        final String hostAddr = getClickHouseHttpAddress();
        final String ipAddr = getClickHouseHttpAddress(true);

        final BalancedClickhouseDataSource dataSource0 = newBalancedDataSourceWithSuffix(
                "default?user=foo&password=bar",
                properties,
                hostAddr,
                ipAddr);
        assertTrue(dataSource0.getConnection().createStatement().execute("SELECT 1"));

        final BalancedClickhouseDataSource dataSource1 = newBalancedDataSourceWithSuffix(
                "default?user=foo",
                properties,
                hostAddr,
                ipAddr);
        // assertThrows(RuntimeException.class,
        //    () -> dataSource1.getConnection().createStatement().execute("SELECT 1"));


        final BalancedClickhouseDataSource dataSource2 = newBalancedDataSourceWithSuffix(
                "default?user=oof",
                properties,
                hostAddr,
                ipAddr);
        assertTrue(dataSource2.getConnection().createStatement().execute("SELECT 1"));

        properties.setUser("foo");
        properties.setPassword("bar");
        final BalancedClickhouseDataSource dataSource3 = newBalancedDataSourceWithSuffix(
                "default",
                properties,
                hostAddr,
                ipAddr);
        assertTrue(dataSource3.getConnection().createStatement().execute("SELECT 1"));

        properties.setPassword("bar");
        final BalancedClickhouseDataSource dataSource4 = newBalancedDataSourceWithSuffix(
                "default?user=oof",
                properties,
                hostAddr,
                ipAddr);
        // JDK 1.8
        // assertThrows(RuntimeException.class,
        //    () -> dataSource4.getConnection().createStatement().execute("SELECT 1"));
        try {
            dataSource4.getConnection().createStatement().execute("SELECT 1");
            fail();
        } catch (ClickHouseException e) {
            // expected
        }

        // it is not allowed to have query parameters per host
        try {
            newBalancedDataSourceWithSuffix(
                "default?user=oof",
                properties,
                hostAddr + "/default?user=foo&password=bar",
                ipAddr);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected
        }

        // the following behavior is quite unexpected, honestly
        // but query params always have precedence over properties
        final BalancedClickhouseDataSource dataSource5 = newBalancedDataSourceWithSuffix(
                "default?user=foo&password=bar",
                properties,
                hostAddr,
                ipAddr);
        assertTrue(
            dataSource5.getConnection("broken", "hacker").createStatement().execute("SELECT 1"));

        // now the other way round, also strange
        final BalancedClickhouseDataSource dataSource6 = newBalancedDataSourceWithSuffix(
                "default?user=broken&password=hacker",
                properties,
                hostAddr,
                ipAddr);
        // JDK 1.8
        // assertThrows(RuntimeException.class,
        //    () -> dataSource6.getConnection("foo", "bar").createStatement().execute("SELECT 1"));
        try {
            dataSource6.getConnection("foo", "bar").createStatement().execute("SELECT 1");
            fail();
        } catch (ClickHouseException e) {
            // expected
        }
    }

    @Test(groups = "integration")
    public void testIPv6() throws Exception {
        // dedup is not supported at all :<
        assertEquals(Arrays.asList("jdbc:clickhouse://[::1]:12345", "jdbc:clickhouse://[0:0:0:0:0:0:0:1]:12345"),
                BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://[::1]:12345,[0:0:0:0:0:0:0:1]:12345"));
        assertEquals(Arrays.asList("jdbc:clickhouse://[192:168:0:0:0:0:0:1]:12345", "jdbc:clickhouse://[192:168:0:0:0:0:0:2]:12345"),
                BalancedClickhouseDataSource.splitUrl("jdbc:clickhouse://[192:168:0:0:0:0:0:1]:12345,[192:168:0:0:0:0:0:2]:12345"));
        
        ClickHouseProperties properties = new ClickHouseProperties();
        String hostAddr = getClickHouseHttpAddress();
        String ipAddr = getClickHouseHttpAddress("[::1]");

        try {
            assertEquals(newBalancedDataSource(properties, ipAddr).getConnection().getServerVersion(),
                newBalancedDataSource(properties, hostAddr).getConnection().getServerVersion());
        } catch (SQLException e) {
            // acceptable if IPv6 is not enabled
            Throwable cause = e.getCause();
            assertTrue(cause instanceof SocketException);
            assertTrue("Protocol family unavailable".equals(cause.getMessage()) || cause.getMessage().contains("Connection refused"));
        }
    }
}
