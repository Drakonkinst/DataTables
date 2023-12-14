package io.github.drakonkinst.datatables.json;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

// Simplified library for easily parsing JSON
public class JsonStack {

    private final Deque<Member> elementPath = new ArrayDeque<>();
    private final List<String> errors = new ArrayList<>();

    public JsonStack(JsonElement root) {
        elementPath.push(new Member(root.getAsJsonObject(), ""));
    }

    public void push(String key) {
        JsonObject maybeChild = maybeChild(key, JsonType.OBJECT).orElseGet(() -> {
            errors.add("Missing " + JsonType.OBJECT.name + ' ' + joinPath() + '/' + key);
            return JsonType.OBJECT.dummy();
        });
        elementPath.push(new Member(maybeChild, key));
    }

    private <T extends JsonElement> Optional<T> maybeChild(String key, JsonType<T> expected) {
        assert elementPath.peek() != null;
        JsonObject head = elementPath.peek().json;
        if (head.has(key)) {
            JsonElement element = head.get(key);
            if (expected.is(element)) {
                return Optional.of(expected.cast(element));
            } else {
                errors.add(joinPath() + '/' + key + " must be " + expected.name + " not "
                        + JsonType.of(element).name);
            }
        }
        return Optional.empty();
    }

    private String joinPath() {
        return Streams.stream(elementPath.descendingIterator())
                .map(Member::name)
                .collect(joining("/"));
    }

    public void allow(String... allowed) {
        allow(Set.of(allowed));
    }

    public void allow(Set<String> allowed) {
        assert elementPath.peek() != null;
        Set<String> present = elementPath.peek().json.keySet();
        Set<String> unexpected = Sets.difference(present, allowed);
        if (!unexpected.isEmpty()) {
            errors.add(
                    joinPath() + " allows children " + allowed + ". Did not expect " + unexpected);
        }
    }

    public boolean getBooleanOrElse(String key, boolean defaultValue) {
        Optional<JsonPrimitive> child = maybeChild(key, JsonType.BOOLEAN);
        return child.map(JsonPrimitive::getAsBoolean).orElse(defaultValue);
    }

    public OptionalInt maybeInt(String key) {
        Optional<JsonPrimitive> child = maybeChild(key, JsonType.NUMBER);
        return child.map(jsonPrimitive -> OptionalInt.of(jsonPrimitive.getAsInt()))
                .orElseGet(OptionalInt::empty);
    }

    public Optional<String> maybeString(String key) {
        Optional<JsonPrimitive> child = maybeChild(key, JsonType.STRING);
        return child.map(JsonPrimitive::getAsString);
    }

    public JsonObject peek() {
        if (elementPath.isEmpty()) {
            return null;
        }
        return elementPath.peek().json();
    }

    public void addError(String msg) {
        errors.add(msg);
    }

    public List<String> getErrors() {
        return errors;
    }

    private record Member(JsonObject json, String name) {}
}
