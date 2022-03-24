package heart.beat.exam.client;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heart.beat.exam.abstrat.AbstractConnection;

public class ServerEndpoint extends AbstractConnection {
	// log
	private static final Logger log = LoggerFactory.getLogger(ServerEndpoint.class);

	static final long checkDelay = 10;
	static final long keepAliveDelay = 5000;
	static final long receiveTimeDelay = 17000;
	static final int CONNECT_RETRIES = 5;
	static final long CONNECT_INTERVAL_UNIT = 1000;

	Selector selector;
	Thread keepAliveWatchDog;

	long lastReceivedTime;
	long lastSendTime;

	SocketAddress remoteAddress;

	NIOClient client;

	public String getAddress() {
		return remoteAddress.toString();
	}

	AtomicBoolean running = new AtomicBoolean(true);
	AtomicBoolean run = new AtomicBoolean(false);

	AtomicInteger connectCount = new AtomicInteger(0);
	Lock lock = new ReentrantLock();

	public ServerEndpoint(SocketChannel chn, NIOClient client) {
		super(chn);
		this.client = client;
		try {
			remoteAddress = chn.getRemoteAddress();
		} catch (IOException e) {
			log.error("wrong occurs when saving remote address : " + e.getMessage());
		}
		start();
	}

	public void buildConnection() {
		if (lock.tryLock()) {
			try {
				if (connectCount.incrementAndGet() <= CONNECT_RETRIES) {
					Thread.sleep(connectCount.get() << CONNECT_INTERVAL_UNIT);
					chn = SocketChannel.open(remoteAddress);
					chn.configureBlocking(false);
					connectCount.incrementAndGet();
					setConnectEstablised();
					start();
					lastReceivedTime = System.currentTimeMillis();
					lastSendTime = System.currentTimeMillis();
				}
			} catch (Exception e) {
				// 重连失败,退出
				client.delConnection(this);
			} finally {
				lock.unlock();
			}
		}
	}

	class KeepAliveWatchDog implements Runnable {
		public void run() {
			lastReceivedTime = System.currentTimeMillis();
			lastSendTime = System.currentTimeMillis();
		
			while (running.get() && run.get()) {

				if (System.currentTimeMillis() - lastSendTime > keepAliveDelay) {
					if (!lost()) {
						writeHB();
						lastSendTime = System.currentTimeMillis();
					} else {
						buildConnection();
						continue;
					}
				}
				if (System.currentTimeMillis() - lastReceivedTime > receiveTimeDelay) {
					setLost();
				} else {
					read0();
					lastReceivedTime = System.currentTimeMillis();
				}
			}
		}
	}

	private void read0() {
		try {
			selector.select(10);
			Iterator<?> it = selector.selectedKeys().iterator();

			while (it.hasNext()) {
				SelectionKey key = (SelectionKey) it.next();
				it.remove();
				if (!key.isValid()) {
					continue;
				}

				if (key.isReadable() && chnEquals((SocketChannel) key.attachment())) {
					read(key);
					lastReceivedTime = System.currentTimeMillis();
				}
			}
		} catch (Exception e) {
			log.error("远程主机强迫关闭了一个现有的连接。");
		}
	}

	@Override
	public void start() {
		try {
			selector = Selector.open();
			chn.register(selector, SelectionKey.OP_READ, chn);
			run.set(true);
			if (null == keepAliveWatchDog) {
				keepAliveWatchDog = new Thread(new KeepAliveWatchDog());
				keepAliveWatchDog.start();
			}
		} catch (IOException e) {
			log.error("start fails... : " + e.getMessage());
		}
	}

	@Override
	public void stop() {
		running.set(false);
		run.set(false);
		selector = null;
		keepAliveWatchDog = null;
		remoteAddress = null;
		client = null;
		connectCount = null;
		lock = null;
	}
}
