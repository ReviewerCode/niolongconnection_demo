package heart.beat.exam.abstrat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;

public class SerializingUtils {
	/* 将int转为低字节在后，高字节在前的byte数组 */
	public static byte[] intToByteArray(int value) {
		byte[] result = new byte[5];
		// 由高位到低位
		result[1] = (byte) ((value >> 24) & 0xFF);
		result[2] = (byte) ((value >> 16) & 0xFF);
		result[3] = (byte) ((value >> 8) & 0xFF);
		result[4] = (byte) (value & 0xFF);
		return result;
	}

	// 将低字节在前转为int，高字节在后的byte数组(与IntToByteArray1想对应)
	public static int byteArrayToInt(byte[] bytes) {
		byte[] tmp = new byte[4];
		for (int i = 0; i < 4; i++) {
			tmp[i] = bytes[i + 1];
		}
		int value = 0;
		// 由高位到低位
		for (int i = 0; i < 4; i++) {
			int shift = (4 - 1 - i) * 8;
			value += (tmp[i] & 0x000000FF) << shift;// 往高位游
		}
		return value;
	}

	public static byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		Hessian2Output out = new Hessian2Output(bos);
		if (null == obj) {
			out.writeObject(Request.buildHeartB());
		} else {
			out.writeObject(obj);
		}
		out.flush();
		byte[] data = bos.toByteArray();
		return data;
	}

	@SuppressWarnings("unchecked")
	public static <T> T deserialize(byte[] data, Class<T> clz) throws IOException {
		// 使用hessan2进行反序列化
		Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(data));
		return (T) input.readObject(clz);
	}
}
