package heart.beat.exam.abstrat;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Id generator
 */
public class IdGenerator {
	static class RamdonHolder {
		static final Random random = new Random();
		static final Lock lock = new ReentrantLock();
		static final AtomicLong increament = new AtomicLong(0);
	}

	protected static final int LEFTSHIFT = 20;
	protected static final int UPPERBOUND = 2 << 13 - 1;

	/**
	 * 获取 id
	 */
	public static long getPesudoId() {
		long currentTime = System.currentTimeMillis();
		long shiftAmount;
		try {
			RamdonHolder.lock.tryLock();
			shiftAmount = RamdonHolder.increament.incrementAndGet();
			RamdonHolder.increament.set(shiftAmount % UPPERBOUND);
			return (currentTime << LEFTSHIFT) + RamdonHolder.increament.get();
		} finally {
			RamdonHolder.lock.unlock();
		}
	}
}
