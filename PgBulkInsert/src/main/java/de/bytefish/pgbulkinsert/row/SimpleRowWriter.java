package de.bytefish.pgbulkinsert.row;

import de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter;
import de.bytefish.pgbulkinsert.pgsql.handlers.ValueHandlerProvider;
import de.bytefish.pgbulkinsert.util.PostgreSqlUtils;
import de.bytefish.pgbulkinsert.util.StringUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.PGCopyOutputStream;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SimpleRowWriter {

    public static class Table {

        private final String schema;
        private final String table;
        private final String[] columns;

        public Table(String table, String... columns) {
            this(null, table, columns);
        }

        public Table(String schema, String table, String... columns) {
            this.schema = schema;
            this.table = table;
            this.columns = columns;
        }

        public String getSchema() {
            return schema;
        }

        public String getTable() {
            return table;
        }

        public String[] getColumns() {
            return columns;
        }

        public String GetFullyQualifiedTableName(boolean usePostgresQuoting) {
            return PostgreSqlUtils.getFullyQualifiedTableName(schema, table, usePostgresQuoting);
        }
    }

    private final Table table;
    private final boolean usePostgresQuoting;
    private final PgBinaryWriter writer;
    private final ValueHandlerProvider provider;
    private final Map<String, Integer> lookup;

    public SimpleRowWriter(Table table) {
        this(table, false);
    }

    public SimpleRowWriter(Table table, boolean usePostgresQuoting) {
        this.table = table;
        this.usePostgresQuoting = usePostgresQuoting;

        this.writer = new PgBinaryWriter();
        this.provider = new ValueHandlerProvider();
        this.lookup = new HashMap<>();

        for (int ordinal = 0; ordinal < table.columns.length; ordinal++) {
            lookup.put(table.columns[ordinal], ordinal);
        }
    }

    public void open(PGConnection connection) throws SQLException  {
        writer.open(new PGCopyOutputStream(connection, getCopyCommand(table, usePostgresQuoting), 1));
    }

    public synchronized void startRow(Consumer<SimpleRow> consumer) {

        writer.startRow(table.columns.length);

        SimpleRow row = new SimpleRow(provider, lookup);

        consumer.accept(row);

        row.writeRow(writer);
    }

    public void close() throws SQLException  {
        writer.close();
    }

    private static String getCopyCommand(Table table, boolean usePostgresQuoting) {

        String commaSeparatedColumns = Arrays.stream(table.columns)
                .map(x -> usePostgresQuoting ? PostgreSqlUtils.quoteIdentifier(x) : x)
                .collect(Collectors.joining(", "));

        return String.format("COPY %1$s(%2$s) FROM STDIN BINARY",
                table.GetFullyQualifiedTableName(usePostgresQuoting),
                commaSeparatedColumns);
    }
}