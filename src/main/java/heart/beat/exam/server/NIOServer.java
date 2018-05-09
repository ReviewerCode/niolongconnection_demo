package heart.beat.exam.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heart.beat.exam.abstrat.AbstractConnection;
import heart.beat.exam.abstrat.GlobalConnectionCache;
import heart.beat.exam.exceptions.AcceptionException;

public class NIOServer {
	// log
	static final Logger log = LoggerFactory.getLogger(ClientEndpoint.class);

	public static void main(String[] args) {
		int port = 42213;
		NIOServer server = new NIOServer(port);
		server.start();
	}

	int port;

	Thread connWatchDog;
	Selector selector;
	ServerSocketChannel serverChn;
	AtomicBoolean running = new AtomicBoolean(false);

	public NIOServer(int port) {
		this.port = port;
	}

	public void start() {
		if (running.get())
			return;
		try {
			running.set(true);
			selector = Selector.open();
			serverChn = ServerSocketChannel.open();
			serverChn.bind(new InetSocketAddress("localhost", port));
			log.info("Server started, listening on :" + serverChn.socket().getLocalPort());
			serverChn.configureBlocking(false);
			serverChn.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			throw new AcceptionException(e);
		}

		connWatchDog = new Thread(new ConnWatchDog());
		connWatchDog.start();
	}

	@SuppressWarnings("deprecation")
	public void stop() {
		Collection<AbstractConnection> all = GlobalConnectionCache.getAll();
		for (AbstractConnection ac : all) {
			ac.stop();
		}
		if (running.get())
			running.set(false);
		if (connWatchDog != null)
			connWatchDog.stop();

		if (null != serverChn) {
			try {
				serverChn.close();
			} catch (IOException e) {
				log.error("wrong occurs when closing the server : " + e.getMessage());
			}
			serverChn = null;
		}
		selector = null;
		running = null;
	}

	private void accept(SelectionKey key) {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

		try {
			SocketChannel channel = serverChannel.accept();
			channel.configureBlocking(false);
			String adderss = channel.getRemoteAddress().toString();
			log.info("accept connection from : " + adderss);
			AbstractConnection ac = new ClientEndpoint(channel);
			ac.start();
			GlobalConnectionCache.put(adderss, ac);
		} catch (IOException e) {
			throw new AcceptionException(e);
		}

	}

	class ConnWatchDog implements Runnable {
		public void run() {
			while (running.get()) {
				try {
					// wait for events
					selector.select();
					// work on selected keys
					Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
					while (keys.hasNext()) {
						SelectionKey key = (SelectionKey) keys.next();
						// this is necessary to prevent the same key from coming
						// up again the next time around.
						keys.remove();

						if (!key.isValid()) {
							continue;
						}

						if (key.isAcceptable()) {
							accept(key);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					stop();
				}
			}
		}
	}
}
