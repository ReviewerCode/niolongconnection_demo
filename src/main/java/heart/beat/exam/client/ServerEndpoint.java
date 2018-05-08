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
import heart.beat.exam.exceptions.ConnectionException;
import heart.beat.exam.exceptions.HeartBeatException;

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
	long lastReceiveTime;
	long lastSendTime;

	AtomicBoolean running = new AtomicBoolean(false);
	AtomicInteger connectCount = new AtomicInteger(0);

	@SuppressWarnings("deprecation")
	public void deConstructor() {
		if (null != keepAliveWatchDog) {
			keepAliveWatchDog.stop();
			keepAliveWatchDog = null;
		}
		if (null != receiveWatchDog) {
			receiveWatchDog.stop();
			receiveWatchDog = null;
		}
		if (null != chn) {
			try {
				chn.close();
				chn = null;
			} catch (IOException e) {
				log.error("wrong occurs when deconstructing : " + e.getMessage());
			}
		}
		selector = null;
		running = null;
		connectCount = null;
	}

	protected void buildConnection() {
		try {
			if (connectCount.get() <= CONNECT_RETRIES) {
				Thread.sleep(connectCount.get() * CONNECT_INTERVAL_UNIT);
				SocketAddress address = chn.getRemoteAddress();

				chn = SocketChannel.open(address);
				chn.configureBlocking(false);
				if (!running.get()) {
					running.set(true);
				}

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
		start();
	}

	public void start() {
		if (running.get())
			return;

		try {
			selector = Selector.open();
			chn.register(selector, SelectionKey.OP_READ, chn);
			running.set(true);

			if (null == keepAliveWatchDog) {
				keepAliveWatchDog = new Thread(new KeepAliveWatchDog());
			}
			keepAliveWatchDog.start();

			if (null == receiveWatchDog) {
				receiveWatchDog = new Thread(new ReceiveWatchDog());
			}
			receiveWatchDog.start();
		} catch (IOException e) {
			log.error("start fails... : " + e.getMessage());
		}

	}

	public void stop() {
		if (running.get()) {
			running.set(false);
		}
	}

	class KeepAliveWatchDog implements Runnable {

		public void run() {
			lastSendTime = System.currentTimeMillis();
			while (running.get()) {
				if (System.currentTimeMillis() - lastSendTime > keepAliveDelay) {
					try {
						writeHB();
					} catch (Exception e) {
						if (e instanceof IOException) {
							// 序列化异常
							log.error("wrong occurs when serializing hb msg : " + e.getMessage());
							continue;
						}

						if (e instanceof HeartBeatException) {
							// 服务器断线
							buildConnection();
						}
					}
					lastSendTime = System.currentTimeMillis();
				} else {
					try {
						Thread.sleep(checkDelay);
					} catch (InterruptedException e) {
						e.printStackTrace();
						stop();
					}
				}
			}
		}
	}

	class ReceiveWatchDog implements Runnable {
		public void run() {
			lastReceiveTime = System.currentTimeMillis();
			while (running.get()) {
				if (System.currentTimeMillis() - lastReceiveTime > receiveTimeDelay) {
					stop();
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
								lastReceiveTime = System.currentTimeMillis();
							}
						}
					} catch (Exception e) {
						log.error("远程主机强迫关闭了一个现有的连接。");
						stop();
					}
				}
			}
		}
	}

	public boolean isActive() {
		return running.get();
	}
}
