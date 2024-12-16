package pl.szajsjem.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoricalMapping {
    private final Map<String, Integer> valueToIndex = new HashMap<>();
    private final List<String> indexToValue = new ArrayList<>();
    private final String columnName;

    public CategoricalMapping(String columnName) {
        this.columnName = columnName;
    }

    public int getOrCreateIndex(String value) {
        return valueToIndex.computeIfAbsent(value, k -> {
            indexToValue.add(k);
            return indexToValue.size() - 1;
        });
    }

    public String getValue(int index) {
        return indexToValue.get(index);
    }

    public int getCategories() {
        return indexToValue.size();
    }

    public String getColumnName() {
        return columnName;
    }

    public List<String> getAllValues() {
        return new ArrayList<>(indexToValue);
    }
}
