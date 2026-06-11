package io.kestra.core.secret;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SecretService<META> {
    private static final String SECRET_PREFIX = "SECRET_";
    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson();

    private Map<String, String> decodedSecrets;

    @PostConstruct
    private void postConstruct() {
        this.decode();
    }

    public void decode() {
        decodedSecrets = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SECRET_PREFIX)).<Map.Entry<String, String>> mapMulti((entry, consumer) ->
            {
                try {
                    String value = entry.getValue().replaceAll("\\R", "");
                    consumer.accept(Map.entry(entry.getKey(), new String(Base64.getDecoder().decode(value))));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure it is Base64-encoded: {}", entry.getKey(), e.getMessage());
                }
            })
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                    Map.Entry::getValue
                )
            );
    }

    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        String secret = decodedSecrets.get(key.toUpperCase());
        if (secret == null) {
            throw new SecretNotFoundException("Cannot find secret for key '" + key + "'.");
        }
        return secret;
    }

    /**
     * Finds the secret for the given key and returns it as a map of fields.
     * <p>
     * The default implementation parses the secret value as a JSON object. Secret managers
     * with natively structured secrets can return their fields directly.
     *
     * @throws SecretNotFoundException if no secret exists for the given key.
     * @throws SecretException if the secret value is not a JSON object.
     */
    public Map<String, String> findSecretAsMap(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        return parseSecretObject(key, findSecret(tenantId, namespace, key));
    }

    /**
     * Parses a secret value as a JSON object and returns its top-level entries.
     * Non-scalar entry values are kept as their JSON representation.
     *
     * @throws SecretException if the value is not a JSON object.
     */
    public static Map<String, String> parseSecretObject(String key, String value) {
        JsonNode node;
        try {
            node = OBJECT_MAPPER.readTree(value);
        } catch (JsonProcessingException e) {
            throw new SecretException("Secret '" + key + "' does not contain a valid JSON object value.");
        }
        if (!node.isObject()) {
            throw new SecretException("Secret '" + key + "' does not contain a valid JSON object value.");
        }
        Map<String, String> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> result.put(
            entry.getKey(),
            entry.getValue().isValueNode() ? entry.getValue().asText() : entry.getValue().toString()
        ));
        return result;
    }

    public ArrayListTotal<META> list(Pageable pageable, String tenantId, List<QueryFilter> filters) throws IOException {
        final Predicate<String> queryPredicate = filters.stream()
            .filter(filter -> QueryFilter.Field.QUERY.equals(filter.field()) && filter.value() != null)
            .findFirst()
            .map(filter ->
            {
                if (QueryFilter.Op.EQUALS.equals(filter.operation())) {
                    return (Predicate<String>) s -> Strings.CI.contains(s, (String) filter.value());
                } else if (QueryFilter.Op.NOT_EQUALS.equals(filter.operation())) {
                    return (Predicate<String>) s -> !Strings.CI.contains(s, (String) filter.value());
                } else {
                    throw new IllegalArgumentException("Unsupported operation for QUERY filter: " + filter.operation());
                }
            })
            .orElse(s -> true);

        //noinspection unchecked
        return ArrayListTotal.of(
            pageable,
            decodedSecrets.keySet().stream().filter(queryPredicate).map(s -> (META) s).toList()
        );
    }

    public Map<String, Set<String>> inheritedSecrets(String tenantId, String namespace) throws IOException {
        return Map.of(namespace, decodedSecrets.keySet());
    }

    public Map<String, Set<String>> ownAndInheritedSecrets(String tenantId, String namespace) throws IOException {
        return inheritedSecrets(tenantId, namespace);
    }
}
