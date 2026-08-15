package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import io.github.aayush10016.trail.retrieval.domain.port.SolvedProblemLexicalSearchRepository;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL weighted solved Problem Session lexical/error/technology search adapter.
 */
@Repository
public class JdbcSolvedProblemLexicalSearchRepository implements SolvedProblemLexicalSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcSolvedProblemLexicalSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<SolvedProblemSearchResult> search(SolvedProblemSearchQuery query) {
        Objects.requireNonNull(query, "query");
        PreparedStatementCreator statementCreator = connection -> {
            var statement = connection.prepareStatement(sql());
            var technologyArray = connection.createArrayOf("text", query.technologies().toArray(String[]::new));
            var errorArray = connection.createArrayOf("text", query.errorPhrases().toArray(String[]::new));
            var problemTypeArray = connection.createArrayOf("text", query.problemTypes().toArray(String[]::new));
            bind(statement, query, technologyArray, errorArray, problemTypeArray);
            return statement;
        };
        return jdbcTemplate.execute(statementCreator, preparedStatement -> {
            try (var resultSet = preparedStatement.executeQuery()) {
                var results = new ArrayList<SolvedProblemSearchResult>();
                while (resultSet.next()) {
                    results.add(mapResult(resultSet));
                }
                return List.copyOf(results);
            }
        });
    }

    private static void bind(
            PreparedStatement statement,
            SolvedProblemSearchQuery query,
            Array technologyArray,
            Array errorArray,
            Array problemTypeArray)
            throws SQLException {
        var queryText = query.queryText() == null ? "" : query.queryText();
        var hasQuery = query.hasQueryText();
        statement.setBoolean(1, query.isRecentOnly());
        statement.setBoolean(2, hasQuery);
        statement.setString(3, queryText);
        statement.setBoolean(4, hasQuery);
        statement.setString(5, queryText);
        statement.setArray(6, errorArray);
        statement.setArray(7, technologyArray);
        statement.setObject(8, query.userId());
        statement.setBoolean(9, query.hasProblemTypeFilter());
        statement.setArray(10, problemTypeArray);
        statement.setBoolean(11, query.isRecentOnly());
        statement.setBoolean(12, hasQuery);
        statement.setString(13, queryText);
        statement.setArray(14, errorArray);
        statement.setArray(15, technologyArray);
        statement.setBoolean(16, query.hasProblemTypeFilter());
        statement.setBoolean(17, query.hasProblemTypeFilter());
        statement.setInt(18, query.limit());
        statement.setInt(19, query.offset());
    }

    private static String sql() {
        return """
                select
                    id,
                    title,
                    final_solution,
                    generated_summary,
                    technologies,
                    error_phrases,
                    problem_type,
                    solution_authored_at,
                    duration_seconds,
                    updated_at,
                    lexical_matched,
                    error_overlap_count,
                    technology_overlap_count,
                    (
                        lexical_score
                        + (error_overlap_count * 8.0)
                        + (technology_overlap_count * 3.0)
                    ) as score
                from (
                    select
                        ps.id,
                        ps.title,
                        ps.final_solution,
                        ps.generated_summary,
                        ps.technologies,
                        ps.error_phrases,
                        ps.problem_type,
                        ps.solution_authored_at,
                        ps.duration_seconds,
                        ps.updated_at,
                        ? as recent_only,
                        case
                            when ? then ps.search_vector @@ plainto_tsquery('simple', ?)
                            else false
                        end as lexical_matched,
                        case
                            when ? then ts_rank_cd(ps.search_vector, plainto_tsquery('simple', ?)) * 10.0
                            else 0.0
                        end as lexical_score,
                        (
                            select count(*)
                            from unnest(ps.error_phrases) error_phrase
                            where error_phrase = any (?::text[])
                        ) as error_overlap_count,
                        (
                            select count(*)
                            from unnest(ps.technologies) technology
                            where technology = any (?::text[])
                        ) as technology_overlap_count
                    from problem_sessions ps
                    where ps.user_id = ?
                        and ps.status = 'SOLVED'
                        and ps.deleted_at is null
                        and (not ? or lower(ps.problem_type) = any (?::text[]))
                        and (
                            ?
                            or (? and ps.search_vector @@ plainto_tsquery('simple', ?))
                            or ps.error_phrases && ?::text[]
                            or ps.technologies && ?::text[]
                            or ?
                        )
                ) scored
                where (
                    recent_only
                    or
                    lexical_matched
                    or error_overlap_count > 0
                    or technology_overlap_count > 0
                    or ?
                )
                order by score desc, updated_at desc, id desc
                limit ?
                offset ?
                """;
    }

    private static SolvedProblemSearchResult mapResult(ResultSet resultSet) throws SQLException {
        return new SolvedProblemSearchResult(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getString("final_solution"),
                resultSet.getString("generated_summary"),
                textArray(resultSet, "technologies"),
                textArray(resultSet, "error_phrases"),
                resultSet.getString("problem_type"),
                resultSet.getDouble("score"),
                evidenceCodes(
                        resultSet.getBoolean("lexical_matched"),
                        resultSet.getInt("error_overlap_count"),
                        resultSet.getInt("technology_overlap_count")),
                instant(resultSet, "solution_authored_at"),
                durationSeconds(resultSet),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static java.time.Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        var timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer durationSeconds(ResultSet resultSet) throws SQLException {
        var value = resultSet.getInt("duration_seconds");
        return resultSet.wasNull() ? null : value;
    }

    private static List<SearchEvidenceCode> evidenceCodes(
            boolean lexicalMatched, int errorOverlapCount, int technologyOverlapCount) {
        var evidence = new ArrayList<SearchEvidenceCode>();
        if (lexicalMatched) {
            evidence.add(SearchEvidenceCode.LEXICAL_TEXT);
        }
        if (errorOverlapCount > 0) {
            evidence.add(SearchEvidenceCode.ERROR_PHRASE);
        }
        if (technologyOverlapCount > 0) {
            evidence.add(SearchEvidenceCode.TECHNOLOGY);
        }
        return List.copyOf(evidence);
    }

    private static List<String> textArray(ResultSet resultSet, String columnName) throws SQLException {
        var array = resultSet.getArray(columnName);
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((String[]) array.getArray()).toList();
    }
}
