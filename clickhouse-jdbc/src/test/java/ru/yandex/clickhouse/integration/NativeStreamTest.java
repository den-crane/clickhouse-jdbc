package ru.yandex.clickhouse.integration;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ru.yandex.clickhouse.ClickHouseConnection;
import ru.yandex.clickhouse.ClickHouseStatement;
import ru.yandex.clickhouse.JdbcIntegrationTest;
import ru.yandex.clickhouse.util.ClickHouseRowBinaryStream;
import ru.yandex.clickhouse.util.ClickHouseStreamCallback;

import java.io.IOException;
import java.sql.Date;
import java.sql.ResultSet;

import static org.testng.Assert.assertEquals;

public class NativeStreamTest extends JdbcIntegrationTest {
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
    public void testLowCardinality() throws Exception{
        final ClickHouseStatement statement = connection.createStatement();
        connection.createStatement().execute("DROP TABLE IF EXISTS low_cardinality");
        connection.createStatement().execute(
            "CREATE TABLE low_cardinality (date Date, " +
                    "lowCardinality LowCardinality(String), " +
                    "string String," +
                    "fixedString FixedString(3)," +
                    "fixedStringLC LowCardinality(FixedString(6))" +
                    ") ENGINE = MergeTree partition by toYYYYMM(date) order by date"
        );

        // Code: 368, e.displayText() = DB::Exception: Bad cast from type DB::ColumnString to DB::ColumnLowCardinality
        if (connection.getMetaData().getDatabaseMajorVersion() <= 19) {
            return;
        }

        final Date date1 = new Date(1497474018000L);

        statement.sendNativeStream(
            "INSERT INTO low_cardinality (date, lowCardinality, string, fixedString, fixedStringLC)",
            new ClickHouseStreamCallback() {
                @Override
                public void writeTo(ClickHouseRowBinaryStream stream) throws IOException {
                    stream.writeUnsignedLeb128(5); // Columns number
                    stream.writeUnsignedLeb128(1); // Rows number

                    stream.writeString("date"); // Column name
                    stream.writeString("Date");  // Column type
                    stream.writeDate(date1);  // value

                    stream.writeString("lowCardinality"); // Column name
                    stream.writeString("String");  // Column type
                    stream.writeString("string");  // value

                    stream.writeString("string"); // Column name
                    stream.writeString("String");  // Column type
                    stream.writeString("string");  // value

                    stream.writeString("fixedString"); // Column name
                    stream.writeString("FixedString(3)");  // Column type
                    stream.writeFixedString("str");  // value

                    stream.writeString("fixedStringLC"); // Column name
                    stream.writeString("FixedString(6)");  // Column type
                    stream.writeFixedString("str1", 6);  // value
                }
            }
        );

        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM low_cardinality");

        Assert.assertTrue(rs.next());
        assertEquals(rs.getString("lowCardinality"), "string");
        assertEquals(rs.getString("string"), "string");
        assertEquals(rs.getString("fixedString"), "str");
        assertEquals(rs.getString("fixedStringLC"), "str1\0\0");
    }
}
