package heart.beat.exam.abstrat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heart.beat.exam.exceptions.HeartBeatException;
import heart.beat.exam.exceptions.ReadDataException;
import heart.beat.exam.exceptions.WriteDataException;

public abstract class AbstractConnection {
	private static final Logger log = LoggerFactory.getLogger(AbstractConnection.class);

	protected SocketChannel chn;
	private AtomicBoolean cached = new AtomicBoolean(false);
	private ByteBuffer cacheBuffer = ByteBuffer.allocate(1024);

	// protected constructor
	protected AbstractConnection(SocketChannel chn) {
		this.chn = chn;
	}

	/**
	 * -------------------------------------------------------------------------
	 * -----------------------public method-------------------------------------
	 * -------------------------------------------------------------------------
	 */
	public abstract void start();

	public abstract boolean isActive();

	public abstract void deConstructor();

	// 一个client的write事件不一定唯一对应server的read事件，所以需要缓存不完整的包，以便拼接成完整的包
	// 包协议：包=包头(4byte)+包体，包头内容为包体的数据长度
	public void read(SelectionKey selectionKey) {
		SocketChannel channel = (SocketChannel) selectionKey.attachment();
		if (!chnEquals(channel)) {
			return;
		}
		// 数据头长度
		int headLength = 5;
		byte[] check = new byte[5];

		try {
			ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
			int bodyLen = -1;
			if (cached.get()) {
				cacheBuffer.flip();
				byteBuffer.put(cacheBuffer);
				cached.set(false);
			}
			// 当前read事件
			channel.read(byteBuffer);
			byteBuffer.flip();
			// write mode to read mode
			while (byteBuffer.remaining() > 0) {
				if (bodyLen == -1) {
					// 还没有读出包头，先读出包头
					if (byteBuffer.remaining() >= headLength) {
						// 可以读出包头，否则缓存
						byteBuffer.mark();
						byteBuffer.get(check);
						if (check[0] == ((byte) (check[1] ^ check[2] ^ check[3] ^ check[4]))) {
							bodyLen = SerializingUtils.byteArrayToInt(check);
						}
					} else {
						byteBuffer.reset();
						cached.set(true);
						cacheBuffer.clear();
						cacheBuffer.put(byteBuffer);
						break;
					}
				} else {// 已经读出包头
					if (byteBuffer.remaining() >= bodyLen) {
						// 大于等于一个包，否则缓存
						byte[] bodyByte = new byte[bodyLen];
						byteBuffer.get(bodyByte, 0, bodyLen);
						bodyLen = -1;

						Request request = SerializingUtils.deserialize(bodyByte, Request.class);
						dispatch(request, selectionKey);
					} else {
						byteBuffer.reset();
						cacheBuffer.clear();
						cacheBuffer.put(byteBuffer);
						cached.set(true);
						break;
					}
				}
			}
			selectionKey.interestOps(SelectionKey.OP_READ);
		} catch (IOException e) {
			throw new ReadDataException(e);
		}
	}

	public void write(SelectionKey selectionKey) {
		SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
		String httpResponse = "HTTP/1.1 200 OK\r\n" + "Content-Length: 38\r\n" + "Content-Type: text/html\r\n" + "\r\n"
				+ "<html><body>Hello World!</body></html>";
		log.info("response from server to client");
		try {
			ByteBuffer byteBuffer = ByteBuffer.wrap(httpResponse.getBytes());
			while (byteBuffer.hasRemaining()) {
				socketChannel.write(byteBuffer);
			}
			selectionKey.cancel();
		} catch (IOException e) {
			selectionKey.cancel();
			throw new WriteDataException(e);
		}
	}

	protected void writeHBResponse(SelectionKey key) {
		if (!chnEquals((SocketChannel) key.channel())) {
			log.error("two instance of socketchannel is not the same while sending hb response");
			return;
		}
		Request request = Request.buildHeartBResponse();
		request.setMsg("response for hb msg");
		byte[] msg;
		try {
			msg = SerializingUtils.serialize(request);
		} catch (IOException e1) {
			throw new HeartBeatException(e1);
		}

		int bodyLen = msg.length;
		byte[] check = SerializingUtils.intToByteArray(bodyLen);
		check[0] = (byte) (check[1] ^ check[2] ^ check[3] ^ check[4]);
		ByteBuffer byteBuffer = ByteBuffer.allocate(5 + bodyLen);
		byteBuffer.put(check);
		byteBuffer.put(msg);
		byteBuffer.flip();

		log.info("server response heart beat msg");
		while (byteBuffer.hasRemaining()) {
			try {
				chn.write(byteBuffer);
			} catch (IOException e) {
				throw new HeartBeatException(e);
			}
		}
	}

	protected void writeHB() throws IOException {
		Request request = Request.buildHeartB();
		request.setMsg("request for hb msg");
		byte[] msg;
		msg = SerializingUtils.serialize(request);

		int bodyLen = msg.length;
		byte[] check = SerializingUtils.intToByteArray(bodyLen);
		check[0] = (byte) (check[1] ^ check[2] ^ check[3] ^ check[4]);
		ByteBuffer byteBuffer = ByteBuffer.allocate(5 + bodyLen);
		byteBuffer.put(check);
		byteBuffer.put(msg);
		byteBuffer.flip();

		log.info("client send heart beat msg");
		while (byteBuffer.hasRemaining()) {
			try {
				chn.write(byteBuffer);
			} catch (IOException e) {
				throw new HeartBeatException(e);
			}
		}
	}

	// destroy this abstract connection
	protected void destroy() {
		if (null != chn) {
			try {
				chn.close();
				chn = null;
			} catch (IOException e) {
				log.error("wrong occurs when closing connection");
			}
		}
	}

	// test whether the passed-in socket channel equals to the one within class
	protected boolean chnEquals(SocketChannel chn) {
		if (null == chn) {
			return false;
		}

		return (chn == this.chn);
	}

	// dispatch the request
	protected void dispatch(Request request, SelectionKey key) {
		if (null != request) {
			log.info("receive from client with content : " + request.getMsg());
		}
	}

}
