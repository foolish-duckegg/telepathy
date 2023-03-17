package me.laiketong.telepathy.tool;

import java.util.HashMap;

public class KVMap<K, V> extends HashMap<K, V> {
    private HashMap<V, K> mapForValue;

    public KVMap() {
        super();
        mapForValue = new HashMap<>();
    }

    @Override
    public V put(K key, V value) {
        if (get(key) == null && mapForValue.get(value) != null)
            throw new IllegalArgumentException("key or value is exist");
        mapForValue.put(value, key);
        return super.put(key, value);
    }

    @Override
    public V remove(Object key) {
        V value = super.remove(key);
        mapForValue.remove(value);
        return value;
    }

    public K getFromValue(V value) {
        return mapForValue.get(value);
    }

    public K putFromValue(V value, K key) {
        if (get(key) == null && mapForValue.get(value) != null)
            throw new IllegalArgumentException("key or value is exist");
        super.put(key, value);
        return mapForValue.put(value, key);
    }

    public K removeFromValue(V value) {
        K key = mapForValue.remove(value);
        remove(key);
        return key;
    }
}









