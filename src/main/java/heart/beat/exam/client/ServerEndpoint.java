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

	long checkDelay = 10;
	long keepAliveDelay = 5000;
	long receiveTimeDelay = 17000;
	long lastReceiveTime;

	private Selector selector;
	private AtomicBoolean running = new AtomicBoolean(false);
	private long lastSendTime;

	static final int CONNECT_RETRIES = 5;
	AtomicInteger connectCount = new AtomicInteger(0);

	Thread keepAliveWatchDog;
	Thread receiveWatchDog;

	private void connect() {
		try {
			if (connectCount.get() <= CONNECT_RETRIES) {
				SocketAddress address = chn.getRemoteAddress();
				chn = SocketChannel.open(address);
				chn.configureBlocking(false);
				chn.register(selector, SelectionKey.OP_READ, chn);
				if (!running.get()) {
					running.set(true);
				}
				keepAliveWatchDog = new Thread(new KeepAliveWatchDog());
				keepAliveWatchDog.start();
				receiveWatchDog = new Thread(new ReceiveWatchDog());
				receiveWatchDog.start();
			} else {
				stop();
			}
		} catch (IOException e) {
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

			keepAliveWatchDog = new Thread(new KeepAliveWatchDog());
			keepAliveWatchDog.start();
			receiveWatchDog = new Thread(new ReceiveWatchDog());
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
					// try {
					// writeHB();
					// } catch (HeartBeatException e) {
					// log.error("wrong occurs when sending heart-beat msg : " +
					// e.getMessage());
					// // reconnect
					// //connect();
					// }
					writeHB();
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
}
