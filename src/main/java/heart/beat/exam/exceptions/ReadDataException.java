package heart.beat.exam.exceptions;

public class ReadDataException extends AbstractException {

	private static final long serialVersionUID = 2632764522019493188L;

	private String detailMsg;

	public ReadDataException(Throwable t) {
		super(t);
		detailMsg = t.getMessage();
	}

	public String toString() {
		return (null == detailMsg ? "" : detailMsg);
	}

}
