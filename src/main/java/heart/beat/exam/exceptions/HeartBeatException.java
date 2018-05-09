package heart.beat.exam.exceptions;

public class HeartBeatException extends AbstractException {
	private static final long serialVersionUID = 8544593524747309592L;

	private String detailMsg;

	public HeartBeatException(Throwable t) {
		super(t);
		detailMsg = t.getMessage();
	}

	public String toString() {
		return (null == detailMsg ? "" : detailMsg);
	}

}
