package all.in.one.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import heart.beat.exam.client.NIOClient;
import heart.beat.exam.server.NIOServer;

public class JarInJarMain {

	static final String SERVER_SIDE = "-server";
	static final String SERVER_PORT = "-p";
	static final String CLIENT_SIDE = "-client";
	static final String REMOTE_IP = "-ip";
	static final String REMOT_PORT = "-p";

	static class ConstantHolder {
		static Lock lock = new ReentrantLock();
		static Pattern pattern = Pattern.compile("[0-9]*");
		static String ipv4Regex = "^(([0-9]|[1-9][0-9]|[1-2][1-5][0-5])\\.){3}([0-9]|[1-9][0-9]|[1-2][1-5][0-5])(/([1-9]|[1-2][0-9]|3[01]))?$";
	}

	public static void main(String[] args) throws UnknownHostException, IOException {
		String[] params = parseArgs(args);
		String[] newParams = new String[2];
		if (SERVER_SIDE.equals(params[0])) {
			newParams[0] = params[2];
			newParams[1] = null;
		} else if (CLIENT_SIDE.equals(params[0])) {
			newParams[0] = params[2];
			newParams[1] = params[4];
		}

		if (CLIENT_SIDE.equals(params[0])) {
			NIOClient.main(newParams);
		} else {
			NIOServer.main(newParams);
		}

	}

	protected static String[] parseArgs(String[] args) {

		boolean paramInvalid1 = (0 == args.length || 2 == args.length);
		boolean paramInvalid2 = (3 == args.length && SERVER_SIDE.equalsIgnoreCase(args[0])
				&& SERVER_PORT.equalsIgnoreCase(args[1]) && !isNumeric(args[2]));
		boolean paramInvalid3 = (5 == args.length && CLIENT_SIDE.equalsIgnoreCase(args[0])
				&& REMOTE_IP.equalsIgnoreCase(args[1]) && !isIp(args[2]) && REMOT_PORT.equalsIgnoreCase(args[3])
				&& !isNumeric(args[4]));

		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);

		String s;
		List<String> params = new ArrayList<String>();
		while (paramInvalid1 || paramInvalid2 || paramInvalid3) {
			s = null;
			params.clear();
			if (paramInvalid1) {
				System.err.println("the number of inputed paramaters is invalid, input again");
				try {
					s = br.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (paramInvalid2) {
				System.err.println("input the first parameter \"-server\" for server side, input again");
				try {
					s = br.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (paramInvalid3) {
				System.err.println("input the first parameter \"-client\" for client side");
				try {
					s = br.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			int index = -1;
			while (-1 != s.indexOf(" ")) {
				index++;
				params.add(s.substring(0, s.indexOf(" ")));
				s = s.substring(params.get(index).length() + 1);
			}
			params.add(s);

			String[] newArgs = params.toArray(new String[0]);
			paramInvalid1 = (0 == newArgs.length || 2 == newArgs.length);
			paramInvalid2 = (3 == newArgs.length && SERVER_SIDE.equalsIgnoreCase(newArgs[0])
					&& SERVER_PORT.equalsIgnoreCase(newArgs[1]) && !isNumeric(newArgs[2]));
			paramInvalid3 = (5 == newArgs.length && CLIENT_SIDE.equalsIgnoreCase(newArgs[0])
					&& REMOTE_IP.equalsIgnoreCase(newArgs[1]) && !isIp(newArgs[2])
					&& REMOT_PORT.equalsIgnoreCase(newArgs[3]) && !isNumeric(newArgs[4]));
		}
		return params.toArray(new String[0]);
	}

	protected static boolean isNumeric(String str) {
		try {
			ConstantHolder.lock.tryLock();
			return ConstantHolder.pattern.matcher(str).matches();
		} finally {
			ConstantHolder.lock.unlock();
		}
	}

	protected static boolean isIp(String str) {
		synchronized (ConstantHolder.ipv4Regex) {
			return ConstantHolder.ipv4Regex.matches(str);
		}
	}
}
