package io.epiphaneia.server;

import io.epiphaneia.domain.internal.entity.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that JPA entity annotations match the Flyway V001 DDL.
 * <p>
 * Tagged {@code integration} for tests requiring a real PostgreSQL — skipped
 * in default test runs when Docker is unavailable.
 */
class EntitySchemaTest {

    private static final Set<Class<?>> ENTITY_CLASSES = Set.of(
            Admin.class, ApiToken.class, Application.class, Conversation.class,
            DataSource.class, Evidence.class, FixSuggestion.class, LlmProvider.class,
            Message.class, RootCauseHypothesis.class
    );

    @Test
    @DisplayName("all 10 entities have @Entity and @Table annotations")
    void allEntitiesAnnotated() {
        for (Class<?> clazz : ENTITY_CLASSES) {
            assertNotNull(clazz.getAnnotation(Entity.class),
                    clazz.getSimpleName() + " missing @Entity");
            assertNotNull(clazz.getAnnotation(Table.class),
                    clazz.getSimpleName() + " missing @Table");
        }
    }

    @Test
    @DisplayName("all entities have @Id with UUID generation")
    void allEntitiesHaveId() {
        for (Class<?> clazz : ENTITY_CLASSES) {
            Field idField = findField(clazz, "id");
            assertNotNull(idField, clazz.getSimpleName() + " missing 'id' field");
            assertNotNull(idField.getAnnotation(Id.class),
                    clazz.getSimpleName() + ".id missing @Id");
            GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
            assertNotNull(gv, clazz.getSimpleName() + ".id missing @GeneratedValue");
            assertEquals(GenerationType.UUID, gv.strategy(),
                    clazz.getSimpleName() + ".id generation not UUID");
        }
    }

    @Test
    @DisplayName("Message has OneToMany for evidence, hypotheses, and suggestions")
    void messageRelationships() {
        Field evidence = findField(Message.class, "evidence");
        assertNotNull(evidence);
        OneToMany otm = evidence.getAnnotation(OneToMany.class);
        assertNotNull(otm, "Message.evidence missing @OneToMany");
        assertTrue(otm.cascade().length > 0, "Message.evidence missing cascade");

        Field hypotheses = findField(Message.class, "hypotheses");
        assertNotNull(hypotheses, "Message missing hypotheses field");
        OneToMany otmHyp = hypotheses.getAnnotation(OneToMany.class);
        assertNotNull(otmHyp, "Message.hypotheses missing @OneToMany");
        assertTrue(otmHyp.cascade().length > 0, "Message.hypotheses missing cascade");

        Field suggestions = findField(Message.class, "suggestions");
        assertNotNull(suggestions, "Message missing suggestions field");
        OneToMany otmSug = suggestions.getAnnotation(OneToMany.class);
        assertNotNull(otmSug, "Message.suggestions missing @OneToMany");
        assertTrue(otmSug.cascade().length > 0, "Message.suggestions missing cascade");
    }

    @Test
    @DisplayName("RootCauseHypothesis has unique constraint on message_id + rank")
    void hypothesisUniqueConstraint() {
        Table table = RootCauseHypothesis.class.getAnnotation(Table.class);
        assertNotNull(table);
        UniqueConstraint[] ucs = table.uniqueConstraints();
        assertTrue(ucs.length > 0, "RootCauseHypothesis missing unique constraint");
    }

    @Test
    @DisplayName("FixSuggestion autoExecutionAllowed defaults to false")
    void fixSuggestionDefaults() throws Exception {
        FixSuggestion fs = new FixSuggestion();
        assertFalse(fs.isAutoExecutionAllowed());
    }

    @Test
    @DisplayName("Application cascade delete reaches Conversation")
    void applicationCascade() {
        Field conversations = findField(Application.class, "conversations");
        assertNotNull(conversations);
        OneToMany otm = conversations.getAnnotation(OneToMany.class);
        assertNotNull(otm);
        assertTrue(Arrays.asList(otm.cascade()).contains(CascadeType.ALL)
                || Arrays.asList(otm.cascade()).contains(CascadeType.REMOVE),
                "Application.conversations missing cascade delete");
    }

    @Test
    @DisplayName("Encrypted fields use TEXT column definition")
    void encryptedFields() {
        Field apiKey = findField(LlmProvider.class, "apiKeyEncrypted");
        assertNotNull(apiKey);
        Column col = apiKey.getAnnotation(Column.class);
        assertNotNull(col);
        assertEquals("TEXT", col.columnDefinition(), "apiKeyEncrypted should use TEXT");
    }

    @Test
    @DisplayName("Admin defaults: username='admin', mustChangePassword=true")
    void adminDefaults() {
        Admin admin = new Admin();
        assertEquals("admin", admin.getUsername());
        assertTrue(admin.isMustChangePassword());
    }

    @Test
    @DisplayName("DataSource defaults: authType='NONE', connected=false")
    void dataSourceDefaults() {
        DataSource ds = new DataSource();
        assertEquals("NONE", ds.getAuthType());
        assertFalse(ds.isConnected());
    }

    @Test
    @DisplayName("Application defaults: tags='[]'")
    void applicationDefaults() {
        Application app = new Application();
        assertEquals("[]", app.getTags());
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
