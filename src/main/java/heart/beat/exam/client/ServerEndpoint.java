package heart.beat.exam.client;

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
import heart.beat.exam.exceptions.ConnectionException;

public class ServerEndpoint extends AbstractConnection {
	// log
	private static final Logger log = LoggerFactory.getLogger(ServerEndpoint.class);

	static final long checkDelay = 10;
	static final long keepAliveDelay = 5000;
	static final long receiveTimeDelay = 17000;
	static final int CONNECT_RETRIES = 5;
	static final long CONNECT_INTERVAL_UNIT = 5 * 60 * 1000;

	Selector selector;
	Thread keepAliveWatchDog;
	Thread receiveWatchDog;
	long lastReceivedTime;
	long lastSendTime;
	SocketAddress remoteAddress;

	AtomicBoolean running = new AtomicBoolean(true);
	AtomicBoolean run = new AtomicBoolean(false);

	AtomicInteger connectCount = new AtomicInteger(0);

	public void stop() {
		killWatchDog();
		if (null != chn) {
			try {
				for (SelectionKey k : selector.keys()) {
					k.cancel();
				}
				chn.close();
			} catch (IOException e) {
				log.error("wrong occurs when closing connection : " + e.getMessage());
			}
		}
		if (null != remoteAddress) {
			GlobalConnectionCache.remove(remoteAddress.toString());
		}
		running = null;
		connectCount = null;
		selector = null;
	}

	private void killWatchDog() {
		run.set(false);
		keepAliveWatchDog = null;
		receiveWatchDog = null;
		for (SelectionKey k : selector.keys()) {
			k.cancel();
		}
		selector = null;
		chn = null;
	}

	protected void buildConnection() {
		try {
			if (running.get() && connectCount.get() <= CONNECT_RETRIES) {
				Thread.sleep(connectCount.get() * CONNECT_INTERVAL_UNIT);
				chn = SocketChannel.open(remoteAddress);
				chn.configureBlocking(false);
				connectCount.incrementAndGet();
				start();
			} else {
				stop();
			}
		} catch (Exception e) {
			throw new ConnectionException(e);
		}
	}

	public ServerEndpoint(SocketChannel chn) {
		super(chn);
		try {
			remoteAddress = chn.getRemoteAddress();
		} catch (IOException e) {
			log.error("wrong occurs whening saving remote address : " + e.getMessage());
		}
		start();
	}

	public void start() {
		if (running.get() && run.get()) {
			return;
		}
		try {
			selector = Selector.open();
			chn.register(selector, SelectionKey.OP_READ, chn);
			run.set(true);
			keepAliveWatchDog = new Thread(new KeepAliveWatchDog());
			keepAliveWatchDog.start();
			receiveWatchDog = new Thread(new ReceiveWatchDog());
			receiveWatchDog.start();
		} catch (IOException e) {
			log.error("start fails... : " + e.getMessage());
		}

	}

	class KeepAliveWatchDog implements Runnable {
		public void run() {
			lastSendTime = System.currentTimeMillis();
			while (running.get() && run.get()) {
				if (System.currentTimeMillis() - lastSendTime > keepAliveDelay) {
					writeHB();
					lastSendTime = System.currentTimeMillis();
				} else {
					try {
						Thread.sleep(checkDelay);
					} catch (InterruptedException e) {
						log.error(e.getMessage());
						killWatchDog();
					}
				}
			}
		}
	}

	class ReceiveWatchDog implements Runnable {
		public void run() {
			lastReceivedTime = System.currentTimeMillis();
			while (running.get() && run.get()) {
				if (System.currentTimeMillis() - lastReceivedTime > receiveTimeDelay) {
					if (isLost()) {
						killWatchDog();
						ServerEndpoint.this.buildConnection();
					}
				} else {
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
						killWatchDog();
					}
				}
			}
		}
	}

	public boolean isActive() {
		return running.get();
	}

	protected boolean isLost() {
		// 判断断线的条件根据环境设置
		return super.isLost() || (System.currentTimeMillis() - lastReceivedTime > receiveTimeDelay * 2);
	}
}
