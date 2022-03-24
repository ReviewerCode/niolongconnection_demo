package heart.beat.exam.exceptions;

public class BizException extends AbstractException {
	private static final long serialVersionUID = 6557893381893735719L;

	private String detailMsg;

	public BizException(Throwable t) {
		super(t);
		detailMsg = t.getMessage();
	}

	public String toString() {
		return (null == detailMsg ? "" : detailMsg);
	}

}
