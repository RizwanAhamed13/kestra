package io.kestra.jdbc.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;

public abstract class JdbcFlowRepositoryService {
    public static Table<Record> lastRevision(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, boolean asterisk) {
        return lastRevision(jdbcRepository, asterisk, false);
    }

    /**
     * Same shape as {@link #lastRevision(AbstractJdbcRepository, boolean)} but, when
     * {@code excludeDraft} is true, ignores draft revisions before computing the row number.
     * A flow whose latest revision is a draft therefore resolves to its most recent non-draft
     * revision, and a flow with only draft revisions returns no row at all. This is the lookup
     * used when starting a new execution without an explicit revision.
     */
    public static Table<Record> lastNonDraftRevision(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, boolean asterisk) {
        return lastRevision(jdbcRepository, asterisk, true);
    }

    private static Table<Record> lastRevision(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, boolean asterisk, boolean excludeDraft) {
        List<SelectFieldOrAsterisk> fields = new ArrayList<>();
        if (asterisk) {
            // There is an issue in jOOQ with MySQL due to some limitations on MySQL.
            // So we need to qualify the asterisk see https://github.com/jOOQ/jOOQ/issues/15975.
            fields.add(jdbcRepository.getTable().asterisk());
        } else {
            fields.add(field("key", String.class));
            fields.add(field("revision", Integer.class));
        }

        fields.add(
            DSL.rowNumber()
                .over()
                .partitionBy(List.of(field("tenant_id"), field("namespace"), field("id")))
                .orderBy(field("revision").desc())
                .as("revision_rows")
        );

        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var inner = context.select(fields)
                    .from(jdbcRepository.getTable());

                var innerQuery = excludeDraft
                    ? inner.where(field("draft", Boolean.class).isFalse())
                    : inner;

                return context.select(DSL.asterisk())
                    .from(innerQuery.asTable("rev_ord"))
                    .where(field("revision_rows").eq(1))
                    .asTable("rev");
            });
    }

    public static Condition findCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) ->
            {
                Field<String> field = DSL.field("JQ_STRING(\"value\", '.labels." + key + "')", String.class);

                if (value == null) {
                    conditions.add(field.isNotNull());
                } else {
                    conditions.add(field.eq(value));
                }
            });
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(List.of("source_code"), query);
    }
}
