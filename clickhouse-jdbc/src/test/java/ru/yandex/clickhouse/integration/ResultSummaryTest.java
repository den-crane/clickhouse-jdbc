package ru.yandex.clickhouse.integration;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ru.yandex.clickhouse.*;
import ru.yandex.clickhouse.settings.ClickHouseQueryParam;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertTrue;

public class ResultSummaryTest extends JdbcIntegrationTest {
    private ClickHouseConnection connection;

    @BeforeClass(groups = "integration")
    public void setUp() throws Exception {
        connection = newConnection();
    }

    @AfterClass(groups = "integration")
    public void tearDown() throws Exception {
        closeConnection(connection);
    }

    @Test(groups = "integration")
    public void select() throws Exception {
        ClickHouseStatement st = connection.createStatement();
        st.executeQuery("SELECT * FROM numbers(10)", Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertTrue(st.getResponseSummary().getReadRows() >= 10);
        assertTrue(st.getResponseSummary().getReadBytes() > 0);
    }

    @Test(groups = "integration")
    public void largeSelect() throws Exception {
        ClickHouseStatement st = connection.createStatement();
        st.executeQuery("SELECT * FROM numbers(10000000)", Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertTrue(st.getResponseSummary().getReadRows() < 10000000);
        assertTrue(st.getResponseSummary().getReadBytes() > 0);
    }

    @Test(groups = "integration")
    public void largeSelectWaitEndOfQuery() throws Exception {
        ClickHouseStatement st = connection.createStatement();
        st.executeQuery("SELECT * FROM numbers(10000000)", largeSelectWaitEndOfQueryParams());

        assertTrue(st.getResponseSummary().getReadRows() >= 10000000);
        assertTrue(st.getResponseSummary().getReadBytes() > 0);
    }

    private Map<ClickHouseQueryParam, String> largeSelectWaitEndOfQueryParams() {
        Map<ClickHouseQueryParam, String> res = new HashMap<>();
        res.put(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true");
        res.put(ClickHouseQueryParam.WAIT_END_OF_QUERY, "true");
        return res;
    }

    @Test(groups = "integration")
    public void selectWithoutParam() throws Exception {
        ClickHouseStatement st = connection.createStatement();
        st.executeQuery("SELECT * FROM numbers(10)", Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertTrue(st.getResponseSummary().getReadRows() >= 10);
        assertTrue(st.getResponseSummary().getReadBytes() > 0);
    }

    @Test(groups = "integration")
    public void insertSingle() throws Exception {
        createInsertTestTable();

        ClickHousePreparedStatement ps = (ClickHousePreparedStatement) connection.prepareStatement("INSERT INTO insert_test VALUES(?)");
        ps.setLong(1, 1);
        ps.executeQuery(Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertEquals(ps.getResponseSummary().getWrittenRows(), 1);
        assertTrue(ps.getResponseSummary().getWrittenBytes() > 0);
    }

    @Test(groups = "integration")
    public void insertBatch() throws Exception {
        createInsertTestTable();

        ClickHousePreparedStatement ps = (ClickHousePreparedStatement) connection.prepareStatement("INSERT INTO insert_test VALUES(?)");
        for (long i = 0; i < 10; i++) {
            ps.setLong(1, i);
            ps.addBatch();
        }
        ps.executeBatch(Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertEquals(ps.getResponseSummary().getWrittenRows(), 10);
        assertTrue(ps.getResponseSummary().getWrittenBytes() > 0);
    }

    @Test(groups = "integration")
    public void insertSelect() throws Exception {
        createInsertTestTable();

        ClickHousePreparedStatement ps = (ClickHousePreparedStatement) connection.prepareStatement("INSERT INTO insert_test SELECT number FROM numbers(10)");
        ps.executeQuery(Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertEquals(ps.getResponseSummary().getWrittenRows(), 10);
        assertTrue(ps.getResponseSummary().getWrittenBytes() > 0);
    }

    @Test(groups = "integration")
    public void insertLargeSelect() throws Exception {
        createInsertTestTable();

        ClickHousePreparedStatement ps = (ClickHousePreparedStatement) connection.prepareStatement("INSERT INTO insert_test SELECT number FROM numbers(10000000)");
        ps.executeQuery(Collections.singletonMap(ClickHouseQueryParam.SEND_PROGRESS_IN_HTTP_HEADERS, "true"));

        assertEquals(ps.getResponseSummary().getWrittenRows(), 10000000);
        assertTrue(ps.getResponseSummary().getWrittenBytes() > 0);
    }

    @Test(groups = "integration")
    public void noSummary() throws Exception {
        ClickHouseStatement st = connection.createStatement();
        st.executeQuery("SELECT * FROM numbers(10)");

        assertNull(st.getResponseSummary());
    }

    private void createInsertTestTable() throws SQLException {
        connection.createStatement().execute("DROP TABLE IF EXISTS insert_test");
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS insert_test (value UInt32) ENGINE = TinyLog");
    }
}
