package heart.beat.exam.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heart.beat.exam.abstrat.AbstractConnection;
import heart.beat.exam.abstrat.GlobalConnectionCache;
import heart.beat.exam.abstrat.Request;
import heart.beat.exam.exceptions.ReadDataException;

/**
 * Note, if a thread stop, it is with no ability to start again
 *
 */
public class ClientEndpoint extends AbstractConnection {
	// log
	private static final Logger log = LoggerFactory.getLogger(ClientEndpoint.class);

	long checkDelay = 10;
	long keepAliveDelay = 5000;
	long receiveTimeDelay = 15000;
	long lastReceiveTime;

	static int MAX_COUNT_RECEIVED = 4;

	Selector selector;
	Thread receiveWatchDog;

	AtomicInteger countReceived = new AtomicInteger(0);
	AtomicBoolean running = new AtomicBoolean(false);

	SocketAddress address;

	public ClientEndpoint(SocketChannel chn) {
		super(chn);
		try {
			address = chn.getRemoteAddress();
		} catch (IOException e) {
			log.error("wrong occurs when saving remote address : " + e.getMessage());
		}
	}

	public void start() {
		if (running.get()) {
			return;
		}
		running.set(true);
		try {
			selector = Selector.open();
			chn.register(selector, SelectionKey.OP_READ, chn);
			receiveWatchDog = new Thread(new ReceiveWatchDog());
			receiveWatchDog.start();
		} catch (IOException e) {
			log.error("wrong occurs whening starting : " + e.getMessage());
			killWatchDog();
		}
	}

	public void stop() {
		killWatchDog();
		selector = null;
		receiveWatchDog = null;
		countReceived = null;
		running = null;
		address = null;
	}

	protected void dispatch(Request request, SelectionKey key) {
		log.info("接收：\t" + request.getMsg());
		countReceived.incrementAndGet();
		if (countReceived.get() >= MAX_COUNT_RECEIVED) {
			killWatchDog();
			countReceived.set(0);
			running.set(false);
		} else {
			if (countReceived.get() % 3 == 0) {
				writeHBResponse(key);
			}
		}
	}

	class ReceiveWatchDog implements Runnable {
		public void run() {
			lastReceiveTime = System.currentTimeMillis();
			while (running.get()) {
				if (System.currentTimeMillis() - lastReceiveTime > receiveTimeDelay) {
					killWatchDog();
				} else {
					try {
						read0();
					} catch (ReadDataException e) {
						log.error("远程主机强迫关闭了一个现有的连接。");
						killWatchDog();
					}
				}
			}
		}
	}

	private void read0() {
		try {
			selector.select(checkDelay);
		} catch (IOException e) {
			throw new ReadDataException(e);
		}
		Iterator<?> it = selector.selectedKeys().iterator();
		while (it.hasNext()) {
			SelectionKey key = (SelectionKey) it.next();
			it.remove();

			if (!key.isValid()) {
				continue;
			}

			if (key.isReadable() && chnEquals((SocketChannel) key.attachment())) {
				lastReceiveTime = System.currentTimeMillis();
				read(key);
			}
		}
	}

	private void killWatchDog() {
		running.set(false);
		if (null != address) {
			log.info("关闭：" + address);
			GlobalConnectionCache.remove(address.toString());
		}
		if (null != selector) {
			for (SelectionKey k : selector.keys()) {
				k.cancel();
			}
			selector = null;
		}
		if (null != chn) {
			try {
				chn.close();
			} catch (IOException e) {
				log.error("wrong occurs when closing the connection :" + e.getMessage());
			}
			chn = null;
		}
	}
}