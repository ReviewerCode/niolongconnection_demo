package heart.beat.exam.abstrat;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalConnectionCache {
	static final Logger log = LoggerFactory.getLogger(GlobalConnectionCache.class);
	
	static class CacheHolder {
		private static final ConcurrentMap<String, AbstractConnection> cache = new ConcurrentHashMap<String, AbstractConnection>();
	}

	public static void put(String key, AbstractConnection value) {
		CacheHolder.cache.put(key, value);
		log.info("put an new connection");
	}

	public static AbstractConnection remove(String key) {
		log.info("remove an old connection");
		return CacheHolder.cache.remove(key);
	}

	public static Collection<AbstractConnection> getAll() {
		return CacheHolder.cache.values();
	}
}
