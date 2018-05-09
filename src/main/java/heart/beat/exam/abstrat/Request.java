package heart.beat.exam.abstrat;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Request implements Serializable {
	private static final long serialVersionUID = -9156262389138909543L;

	static Lock lockHB = new ReentrantLock();
	static Lock lockHBR = new ReentrantLock();

	// build heart-beat request
	public static Request buildHeartB() {
		try {
			lockHB.tryLock();
			return new Request(RequestType.HEARTBEAT_REQUEST);
		} finally {
			lockHB.unlock();
		}
	}

	public static Request buildHeartBResponse() {
		try {
			lockHBR.tryLock();
			return new Request(RequestType.HEARTBEAT_RESPONSE);
		} finally {
			lockHBR.unlock();
		}
	}

	public Request(RequestType type) {
		requestType = type;
		requestId = IdGenerator.getPesudoId();
	}

	// request body
	private String msg;
	// requst type
	private RequestType requestType;
	// request params
	private Map<?, ?> requestParams;
	// request id
	private long requestId;

	public RequestType getRequestType() {
		return requestType;
	}

	public void setRequestType(RequestType requestType) {
		this.requestType = requestType;
	}

	public Map<?, ?> getRequestParams() {
		return requestParams;
	}

	public void setRequestParams(Map<?, ?> requestParams) {
		this.requestParams = requestParams;
	}

	public long getRequestId() {
		return requestId;
	}

	public void setRequestId(long requestId) {
		this.requestId = requestId;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public static enum RequestType {
		HEARTBEAT_REQUEST, HEARTBEAT_RESPONSE, BIZMSG;
	}
}
