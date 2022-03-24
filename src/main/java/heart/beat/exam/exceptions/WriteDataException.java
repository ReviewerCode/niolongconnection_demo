package heart.beat.exam.exceptions;

public class WriteDataException extends AbstractException {
	private static final long serialVersionUID = -7797454843023094913L;

	private String detailMsg;

	public WriteDataException(Throwable t) {
		super(t);
		detailMsg = t.getMessage();
	}

	public String toString() {
		return (null == detailMsg ? "" : detailMsg);
	}
}
