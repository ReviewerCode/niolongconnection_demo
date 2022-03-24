package heart.beat.exam.exceptions;

public class AbstractException extends RuntimeException{
	private static final long serialVersionUID = -1591609405346251743L;
	
	private Throwable t;
	
	public AbstractException(Throwable t){
		this.t = t;
	}

}
