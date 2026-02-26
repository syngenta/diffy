package ai.diffy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsMasterHeadersTest {

    private Map<String, String> parse(String input) {
        Settings settings = new Settings(
            0,
            "localhost:8080",
            "localhost:8080",
            "localhost:8080",
            "http",
            "test",
            "",
            20.0,
            0.03,
            "",
            false,
            false,
            8192,
            "",
            "primary",
            false,
            input != null ? input : ""
        );
        return settings.masterHeaders();
    }

    @Test
    public void parsesValidHeaders() {
        Map<String, String> headers = parse("Authorization:Bearer abc123, X-Custom:hello");
        assertEquals("Bearer abc123", headers.get("Authorization"));
        assertEquals("hello", headers.get("X-Custom"));
    }

    @Test
    public void parsesHeaderWithColonInValue() {
        Map<String, String> headers = parse("Authorization:Bearer abc:123");
        assertEquals("Bearer abc:123", headers.get("Authorization"));
    }

    @Test
    public void emptyInputProducesEmptyMap() {
        assertTrue(parse("").isEmpty());
        assertTrue(parse(null).isEmpty());
    }

    @Test
    public void malformedEntryMissingColonIsSkipped() {
        Map<String, String> headers = parse("AuthorizationBearerToken, X-Custom:hello");
        assertFalse(headers.containsKey("AuthorizationBearerToken"));
        assertEquals("hello", headers.get("X-Custom"));
    }

    @Test
    public void malformedEntryEmptyNameIsSkipped() {
        Map<String, String> headers = parse(":value, X-Custom:hello");
        assertFalse(headers.containsKey(""));
        assertEquals("hello", headers.get("X-Custom"));
    }
}
