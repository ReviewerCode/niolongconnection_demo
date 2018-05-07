package heart.beat.exam.abstrat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GlobalConnectionCache {
	static class CacheHolder {
		private static final ConcurrentMap<String, AbstractConnection> cache = new ConcurrentHashMap<String, AbstractConnection>();
	}

	public static void put(String key, AbstractConnection value) {
		CacheHolder.cache.put(key, value);
	}

	public static AbstractConnection remove(String key) {
		return CacheHolder.cache.remove(key);
	}
}
