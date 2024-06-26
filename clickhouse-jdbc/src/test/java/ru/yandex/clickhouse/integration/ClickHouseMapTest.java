package ru.yandex.clickhouse.integration;

import static org.junit.Assert.assertArrayEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.clickhouse.client.ClickHouseVersion;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import ru.yandex.clickhouse.ClickHouseConnection;
import ru.yandex.clickhouse.ClickHouseDataSource;
import ru.yandex.clickhouse.ClickHouseStatement;
import ru.yandex.clickhouse.JdbcIntegrationTest;
import ru.yandex.clickhouse.except.ClickHouseException;
import ru.yandex.clickhouse.settings.ClickHouseProperties;
import ru.yandex.clickhouse.settings.ClickHouseQueryParam;
import ru.yandex.clickhouse.util.Utils;

public class ClickHouseMapTest extends JdbcIntegrationTest {
    private ClickHouseConnection conn;

    @BeforeClass(groups = "integration")
    public void setUp() throws Exception {
        ClickHouseProperties props = new ClickHouseProperties();
        props.setSessionId(UUID.randomUUID().toString());
        ClickHouseDataSource dataSource = newDataSource(props);
        conn = dataSource.getConnection();
        try (Statement s = conn.createStatement()) {
            s.execute("SET allow_experimental_map_type=1");
        } catch (ClickHouseException e) {
            conn = null;
        }
    }

    @AfterClass(groups = "integration")
    public void tearDown() throws Exception {
        if (conn == null) {
            return;
        }

        try (Statement s = conn.createStatement()) {
            s.execute("SET allow_experimental_map_type=0");
        }
    }

    private void assertMap(Object actual, Object expected) {
        Map<?, ?> m1 = (Map<?, ?>) actual;
        Map<?, ?> m2 = (Map<?, ?>) expected;
        assertEquals(m1.size(), m2.size());
        for (Map.Entry<?, ?> e : m1.entrySet()) {
            if (e.getValue().getClass().isArray()) {
                assertArrayEquals((Object[]) e.getValue(), (Object[]) m2.get(e.getKey()));
            } else {
                assertEquals(e.getValue(), m2.get(e.getKey()));
            }
        }
    }

    @Test(groups = "integration")
    public void testMapSupport() throws SQLException {
        if (conn == null) {
            return;
        }

        String testSql = "create table if not exists system.test_map_support(m Map(UInt8, String)) engine=Memory;"
                + "drop table if exists system.test_map_support;";
        try (ClickHouseConnection conn = newDataSource().getConnection(); Statement s = conn.createStatement()) {
            s.execute("set allow_experimental_map_type=0;" + testSql);
            if (ClickHouseVersion.check(conn.getServerVersion(), "(,21.8)")) {
                fail("Should fail without enabling map support");
            }
        } catch (SQLException e) {
            assertEquals(e.getErrorCode(), 44);
        }

        try (Connection conn = newDataSource().getConnection(); Statement s = conn.createStatement()) {
            assertFalse(s.execute("set allow_experimental_map_type=1;" + testSql));
        }

        try (ClickHouseConnection conn = newDataSource().getConnection();
                ClickHouseStatement s = conn.createStatement()) {
            Map<ClickHouseQueryParam, String> params = new EnumMap<>(ClickHouseQueryParam.class);
            params.put(ClickHouseQueryParam.ALLOW_EXPERIMENTAL_MAP_TYPE, "1");
            assertNull(s.executeQuery(testSql, params));

            params.put(ClickHouseQueryParam.ALLOW_EXPERIMENTAL_MAP_TYPE, "0");
            s.executeQuery(testSql, params);
            if (ClickHouseVersion.check(conn.getServerVersion(), "(,21.8)")) {
                fail("Should fail without enabling map support");
            }
        } catch (SQLException e) {
            assertEquals(e.getErrorCode(), 44);
        }
    }

    @Test(groups = "integration")
    public void testMaps() throws Exception {
        if (conn == null) {
            return;
        }

        ClickHouseVersion version = ClickHouseVersion.of(conn.getServerVersion());
        if (version.check("(,21.3]")) {
            // https://github.com/ClickHouse/ClickHouse/issues/25026
            return;
        }
        String columns = ", ma Map(Integer, Array(String)), mi Map(Integer, Integer)";
        String values = ",{1:['11','12'],2:['22','23']},{1:11,2:22}";
        String params = ",?,?";
        if (version.check("[21.4,21.9)")) {
            columns = "";
            values = "";
            params = "";
        }

        try (Statement s = conn.createStatement()) {
            s.execute("DROP TABLE IF EXISTS test_maps");
            s.execute("CREATE TABLE IF NOT EXISTS test_maps(ms Map(String, String)" + columns + ") ENGINE = Memory");
            s.execute("insert into test_maps values ({'k1':'v1','k2':'v2'}" + values + ")");

            try (ResultSet rs = s.executeQuery("select * from test_maps")) {
                assertTrue(rs.next());
                assertMap(rs.getObject("ms"), Utils.mapOf("k1", "v1", "k2", "v2"));
                if (!columns.isEmpty()) {
                    assertMap(rs.getObject("ma"),
                            Utils.mapOf(1, new String[] { "11", "12" }, 2, new String[] { "22", "23" }));
                    assertMap(rs.getObject("mi"), Utils.mapOf(1, 11, 2, 22));
                }
            }

            s.execute("truncate table test_maps");
        }

        try (PreparedStatement s = conn.prepareStatement("insert into test_maps values(?" + params + ")")) {
            s.setObject(1, Utils.mapOf("k1", "v1", "k2", "v2"));
            if (!columns.isEmpty()) {
                s.setObject(2, Utils.mapOf(1, new String[] { "11", "12" }, 2, new String[] { "22", "23" }));
                s.setObject(3, Utils.mapOf(1, 11, 2, 22));
            }
            s.execute();
        }

        try (Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery("select * from test_maps")) {
                assertTrue(rs.next());
                assertMap(rs.getObject("ms"), Utils.mapOf("k1", "v1", "k2", "v2"));
                if (!columns.isEmpty()) {
                    assertMap(rs.getObject("ma"),
                            Utils.mapOf(1, new String[] { "11", "12" }, 2, new String[] { "22", "23" }));
                    assertMap(rs.getObject("mi"), Utils.mapOf(1, 11, 2, 22));
                }
            }
        }
    }
}
