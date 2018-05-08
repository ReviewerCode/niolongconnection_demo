package heart.beat.exam.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heart.beat.exam.abstrat.AbstractConnection;
import heart.beat.exam.abstrat.Request;
import heart.beat.exam.exceptions.ReadDataException;

public class ClientEndpoint extends AbstractConnection {
	// log
	private static final Logger log = LoggerFactory.getLogger(ClientEndpoint.class);

	long checkDelay = 10;
	long keepAliveDelay = 5000;
	long receiveTimeDelay = 15000;
	long lastReceiveTime;

	Selector selector;
	Thread receiveWatchDog;

	AtomicInteger countReceived = new AtomicInteger(0);
	AtomicBoolean running = new AtomicBoolean(false);
	AtomicBoolean run = new AtomicBoolean(true);

	public ClientEndpoint(SocketChannel chn) {
		super(chn);
	}

	public void deConstructor() {
		if (null != receiveWatchDog) {
			notifyToStop();
			receiveWatchDog = null;
		}
		if (null != chn) {
			try {
				chn.close();
			} catch (IOException e) {
				log.error("wrong occurs whening close the connection : " + e.getMessage());
			}
			chn = null;
		}
		selector = null;
		running = null;
		run = null;
	}

	public void start() {
		if (running.get())
			return;
		running.set(true);
		try {
			selector = Selector.open();
			chn.register(selector, SelectionKey.OP_READ, chn);
		} catch (IOException e) {
			log.error("wrong occurs whening starting : " + e.getMessage());
		}
		if (null == receiveWatchDog) {
			receiveWatchDog = new Thread(new ReceiveWatchDog());
		}
		receiveWatchDog.start();
	}

	public void stop() {
		if (running.get())
			running.set(false);
	}

	protected void dispatch(Request request, SelectionKey key) {
		log.info("接收：\t" + request.getMsg());
		countReceived.incrementAndGet();
		if (countReceived.get() % 3 == 0) {
			writeHBResponse(key);
		}
	}

	class ReceiveWatchDog implements Runnable {
		public void run() {
			lastReceiveTime = System.currentTimeMillis();
			while (running.get() && run.get()) {
				if (System.currentTimeMillis() - lastReceiveTime > receiveTimeDelay) {
					overThis();
				} else {
					read0();
				}
			}
		}
	}

	protected void notifyToStop() {
		run.set(false);
	}

	private void overThis() {
		if (run.get())
			run.set(false);
		if (chn != null) {
			try {
				log.info("关闭：" + chn.getRemoteAddress().toString());
				chn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			chn = null;
		}
	}

	private void read0() {
		try {
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
		} catch (ReadDataException e) {
			overThis();
			log.error("远程主机强迫关闭了一个现有的连接。");
		}
	}

	public boolean isActive() {
		return running.get();
	}
}