package heart.beat.exam.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heart.beat.exam.abstrat.AbstractConnection;
import heart.beat.exam.abstrat.GlobalConnectionCache;

public class NIOClient {
	// log
	private static final Logger log = LoggerFactory.getLogger(NIOClient.class);

	long checkDelay = 10;
	long keepAliveDelay = 5000;
	long receiveTimeDelay = 15000;
	long lastReceiveTime;

	public static void main(String[] args) throws UnknownHostException, IOException {
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put("localhost", 65432);
		NIOClient client = new NIOClient();
		client.start(map);
	}

	public void start(Map<String, Integer> map) throws UnknownHostException, IOException {
		for (Map.Entry<String, Integer> e : map.entrySet()) {
			InetSocketAddress address = new InetSocketAddress(e.getKey(), e.getValue());
			SocketChannel chn = SocketChannel.open(address);
			chn.configureBlocking(false);
			AbstractConnection ac = new ServerEndpoint(chn);
			GlobalConnectionCache.put(address.toString(), ac);
			log.info("本地端口：" + chn.socket().getLocalPort());
		}
	}
}
