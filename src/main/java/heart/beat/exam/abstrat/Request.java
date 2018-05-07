package heart.beat.exam.abstrat;

import java.io.Serializable;
import java.util.Map;

public class Request implements Serializable {
	private static final long serialVersionUID = -9156262389138909543L;

	// build heart-beat request
	public static Request buildHeartB() {
		return new Request(RequestType.HEARTBEAT_REQUEST);
	}

	public static Request buildHeartBResponse() {
		return new Request(RequestType.HEARTBEAT_RESPONSE);
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
