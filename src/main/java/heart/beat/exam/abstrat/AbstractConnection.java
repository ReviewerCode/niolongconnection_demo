package heart.beat.exam.abstrat;

import java.io.IOException;
import java.net.SocketException;
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
	// connect status
	ConnectionStatus conectStatus = ConnectionStatus.CONNECTION_ESTABLISHED;
	// deal the unknown case that if server canncel/close, client will read all
	// time

	protected synchronized boolean lost() {
		return conectStatus == ConnectionStatus.CONNECTION_LOST;
	}

	protected void setLost() {
		conectStatus = ConnectionStatus.CONNECTION_LOST;
	}

	protected void setConnectEstablised() {
		conectStatus = ConnectionStatus.CONNECTION_ESTABLISHED;
	}

	// protected constructor
	protected AbstractConnection(SocketChannel chn) {
		this.chn = chn;
		try {
			chn.socket().setTcpNoDelay(false);
		} catch (SocketException e) {
			log.error("set tcp no delay fails");
		}
		try {
			chn.socket().setSoTimeout(1000);
		} catch (SocketException e) {
			log.error("set so time out fails");
		}
	}

	/**
	 * -------------------------------------------------------------------------
	 * -----------------------public method-------------------------------------
	 * -------------------------------------------------------------------------
	 */
	public abstract void start();

	public abstract void stop();

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
			if (-1 == channel.read(byteBuffer)) {
				// 掉线了,有read事件,但却无数据可读.
				selectionKey.cancel();
				synchronized (conectStatus) {
					conectStatus = ConnectionStatus.CONNECTION_LOST;
				}
				log.info("connection losts");
				return;
			}
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
		} catch (IOException e) {
			throw new ReadDataException(e);
		}
	}

	public void write(SelectionKey selectionKey, Request request) {
		if (!chnEquals((SocketChannel) selectionKey.attachment())) {
			return;
		}
		try {
			write0(request);
		} catch (IOException e) {
			log.error("wrong occurs when writting biz data to remote : " + e.getMessage());
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

	protected void writeHB() {
		Request request = Request.buildHeartB();
		request.setMsg("request for hb msg");
		log.info("client send heart beat msg");
		try {
			write0(request);
		} catch (IOException e) {
			throw new WriteDataException(e);
		}
	}

	// write common request
	private void write0(Request request) throws IOException {
		byte[] msg;
		msg = SerializingUtils.serialize(request);

		int bodyLen = msg.length;
		byte[] check = SerializingUtils.intToByteArray(bodyLen);
		check[0] = (byte) (check[1] ^ check[2] ^ check[3] ^ check[4]);
		ByteBuffer byteBuffer = ByteBuffer.allocate(5 + bodyLen);
		byteBuffer.put(check);
		byteBuffer.put(msg);
		byteBuffer.flip();
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
