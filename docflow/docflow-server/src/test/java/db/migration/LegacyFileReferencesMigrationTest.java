package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyFileReferencesMigrationTest {
    private static final String FILE = "/files/2026/10/16/attachment.png";
    private record Asset(Long owner, String kind) { }
    private record Scope(String url, String type, long id) { }
    private record Row(long cursor, long id, String content, String cover, Long owner) {
        Row(long id, String content, String cover, Long owner) { this(id, id, content, cover, owner); }
    }

    @Test
    void sameOwnerLegacyCopiesAndTemplatesKeepAuthoritativeAttachmentImagesAfterSourcePurge() throws Exception {
        Fixture fixture = new Fixture();
        fixture.assets.put(FILE, new Asset(7L, "DOCUMENT"));
        fixture.scopes.add(new Scope(FILE, "DOCUMENT", 1));
        fixture.documents.add(new Row(2, "<img src='" + FILE + "'>", null, 7L));
        fixture.templates.add(new Row(3, "![image](" + FILE + ")", null, 7L));

        fixture.migrate();
        fixture.scopes.remove(new Scope(FILE, "DOCUMENT", 1));

        assertThat(fixture.scopes).contains(new Scope(FILE, "DOCUMENT", 2), new Scope(FILE, "TEMPLATE", 3));
    }

    @Test
    void grantsKnownPrivateAssetsOnlyToAnOwnerWithCurrentSourceAccess() throws Exception {
        Fixture fixture = new Fixture();
        fixture.assets.put(FILE, new Asset(7L, "DOCUMENT"));
        fixture.readers.add(8L);
        fixture.documents.add(new Row(2, "<img src='" + FILE + "'>", null, 8L));
        fixture.documents.add(new Row(3, "<img src='" + FILE + "'>", null, 9L));
        fixture.templates.add(new Row(4, "<img src='" + FILE + "'>", null, null));

        fixture.migrate();

        assertThat(fixture.scopes).containsExactly(new Scope(FILE, "DOCUMENT", 2));
    }

    @Test
    void neverGrantsFeedbackAndDoesNotClaimUnreferencedLegacyOrphans() throws Exception {
        Fixture fixture = new Fixture();
        fixture.assets.put(FILE, new Asset(7L, "FEEDBACK"));
        fixture.documents.add(new Row(2, "<img src='" + FILE + "'>", null, 7L));
        fixture.documents.add(new Row(3, "<img src='/files/referenced-old.png'>", null, 7L));

        fixture.migrate();

        assertThat(fixture.scopes).containsExactly(new Scope("/files/referenced-old.png", "DOCUMENT", 3));
        assertThat(fixture.assets).doesNotContainKey("/files/unreferenced-orphan.png");
    }

    @Test
    void boundsBufferedBodiesToOneRowAndVisitsEveryVersionOfTheSameDocument() throws Exception {
        Fixture fixture = new Fixture();
        for (long id : List.of(2L, 17L, 93L)) fixture.documents.add(new Row(id, "body", null, 7L));
        fixture.versions.add(new Row(101L, 2L, "![first](/files/version-one.png)", null, 7L));
        fixture.versions.add(new Row(106L, 2L, "![second](/files/version-two.png)", null, 7L));

        fixture.migrate();

        assertThat(fixture.scopes).contains(new Scope("/files/version-one.png", "DOCUMENT", 2),
                new Scope("/files/version-two.png", "DOCUMENT", 2));
        assertThat(fixture.maxSourceRows).isEqualTo(1);
    }

    @Test
    void closesEachSourceResultBeforeIssuingMetadataOrGrantQueries() throws Exception {
        Fixture fixture = new Fixture();
        fixture.requireClosedSource = true;
        fixture.documents.add(new Row(2, "![image](/files/old-image.png)", null, 7L));

        fixture.migrate();

        assertThat(fixture.scopes).containsExactly(new Scope("/files/old-image.png", "DOCUMENT", 2));
        assertThat(fixture.openSourceResults).isZero();
    }

    /** A small JDBC fixture: existing V5's owner-null SQL filter is evaluated as well as plain grants. */
    private static class Fixture {
        final Map<String, Asset> assets = new HashMap<>();
        final Set<Scope> scopes = new LinkedHashSet<>();
        final Set<Long> readers = new HashSet<>();
        final List<Row> documents = new ArrayList<>();
        final List<Row> versions = new ArrayList<>();
        final List<Row> templates = new ArrayList<>();
        int maxSourceRows;
        int openSourceResults;
        boolean requireClosedSource;
        final Map<String, Integer> sourceQueries = new HashMap<>();

        void migrate() throws Exception {
            Connection connection = mock(Connection.class);
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                return rows(sourceRows(sql), false);
            });
            when(connection.prepareStatement(anyString())).thenAnswer(call -> prepared(call.getArgument(0)));
            new V5__legacy_file_references().migrate(context);
        }

        PreparedStatement prepared(String sql) throws Exception {
            PreparedStatement statement = mock(PreparedStatement.class);
            Map<Integer, Object> parameters = new HashMap<>();
            doAnswer(call -> { parameters.put(call.getArgument(0), call.getArgument(1)); return null; })
                    .when(statement).setString(anyInt(), nullable(String.class));
            doAnswer(call -> { parameters.put(call.getArgument(0), call.getArgument(1)); return null; })
                    .when(statement).setLong(anyInt(), anyLong());
            doAnswer(call -> { parameters.put(call.getArgument(0), call.getArgument(1)); return null; })
                    .when(statement).setTimestamp(anyInt(), any(Timestamp.class));
            when(statement.executeUpdate()).thenAnswer(call -> {
                if (requireClosedSource) assertThat(openSourceResults).as("source result must close before nested SQL").isZero();
                if (sql.startsWith("INSERT IGNORE INTO stored_file(")) {
                    assets.putIfAbsent((String) parameters.get(1), new Asset(null, "DOCUMENT"));
                    return 1;
                }
                String url = (String) parameters.get(3);
                Asset asset = assets.get(url);
                if (asset == null || !"DOCUMENT".equals(asset.kind())) return 0;
                if (sql.contains("AND owner_id IS NULL") && asset.owner() != null) return 0;
                scopes.add(new Scope(url, (String) parameters.get(1), (Long) parameters.get(2)));
                return 1;
            });
            when(statement.executeQuery()).thenAnswer(call -> {
                if (sql.contains("LIMIT 1")) {
                    assertThat(sourceQueries.merge(sql, 1, Integer::sum))
                            .as("keyset cursor must advance using each source row's primary key")
                            .isLessThanOrEqualTo(sourceRows(sql).size() + 1);
                    long cursor = (Long) parameters.get(1);
                    return rows(sourceRows(sql).stream().filter(row -> row.cursor() > cursor)
                            .sorted(Comparator.comparingLong(Row::cursor)).limit(1).toList(), true);
                }
                if (requireClosedSource) assertThat(openSourceResults).as("source result must close before nested SQL").isZero();
                ResultSet result = mock(ResultSet.class);
                if (sql.startsWith("SELECT owner_id")) {
                    Asset asset = assets.get((String) parameters.get(1));
                    when(result.next()).thenReturn(asset != null, false);
                    if (asset != null) {
                        when(result.getObject(1, Long.class)).thenReturn(asset.owner());
                        when(result.getString(2)).thenReturn(asset.kind());
                    }
                } else {
                    when(result.next()).thenReturn(true, false);
                    when(result.getInt(1)).thenReturn(readers.contains(parameters.get(2)) ? 1 : 0);
                }
                return result;
            });
            return statement;
        }

        List<Row> sourceRows(String sql) {
            return sql.contains("doc_version") ? versions : sql.contains("document_template") ? templates : documents;
        }

        ResultSet rows(List<Row> values, boolean paged) throws Exception {
            ResultSet result = mock(ResultSet.class);
            maxSourceRows = Math.max(maxSourceRows, values.size());
            openSourceResults++;
            boolean[] closed = {false};
            doAnswer(call -> { if (!closed[0]) { closed[0] = true; openSourceResults--; } return null; })
                    .when(result).close();
            int[] index = {-1};
            when(result.next()).thenAnswer(call -> ++index[0] < values.size());
            when(result.getLong(1)).thenAnswer(call -> paged ? values.get(index[0]).cursor() : values.get(index[0]).id());
            when(result.getLong(2)).thenAnswer(call -> values.get(index[0]).id());
            when(result.getString(2)).thenAnswer(call -> values.get(index[0]).content());
            when(result.getString(3)).thenAnswer(call -> paged ? values.get(index[0]).content() : values.get(index[0]).cover());
            when(result.getString(4)).thenAnswer(call -> values.get(index[0]).cover());
            when(result.getObject(4, Long.class)).thenAnswer(call -> values.get(index[0]).owner());
            when(result.getObject(5, Long.class)).thenAnswer(call -> values.get(index[0]).owner());
            return result;
        }
    }
}
