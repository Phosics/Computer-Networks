import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataTransferThread extends Thread {
	private static final int MAXIMAL_PACKET_SIZE = 65536;
	private static final int HTTP_PORT = 80;

	private static final Pattern REQUEST_PATTERN = Pattern.compile("(GET) (\\/.*) (HTTP.*)");
	private static final Pattern HOST_PATTERN = Pattern.compile("(Host:) (.*)");
	private static final Pattern USER_PATTERN = Pattern.compile("(Authorization:) (.*) (.*)");

	protected Socket client;
	protected Socket destination;

	private byte[] buffer = new byte[MAXIMAL_PACKET_SIZE];
	private ConnectionControl connectionControl;
	private String host;
	private String path;
	private String user;

	public DataTransferThread(Socket client, Socket destination, ConnectionControl connectionControl) {
		this.client = client;
		this.destination = destination;
		this.connectionControl = connectionControl;
	}

	@Override
	public void run() {
		try {
			InputStream reader = client.getInputStream();
			OutputStream writer = destination.getOutputStream();
			int amountRead;

			while (connectionControl.isConnectionOpen()) {
				try {
					amountRead = reader.read(buffer);

					if (amountRead == -1) {
						close();
						continue;
					}

					tryHandleHttp(buffer, amountRead);

					writer.write(buffer, 0, amountRead);
					writer.flush();
				} catch (SocketTimeoutException e) {
					// Doing nothing
				}
			}
		} catch (IOException e) {
			System.err.println("Got an error:" + e.getMessage());
			close();
		}
	}

	private void tryHandleHttp(byte[] buffer, int amountRead) throws IOException {
		if (destination.getPort() != HTTP_PORT) {
			return;
		}

		matchHttp(new String(buffer, 0, amountRead));

		if (isHttpMatch()) {
			printPassword();
		}
	}

	protected void close() {
		connectionControl.closeConnection();
		printClosingConnection();
	}

	protected boolean isHttpMatch() {
		return host != null && path != null && user != null;
	}

	protected void printPassword() {
		System.err.println("Password Found! http://" + user + "@" + host + path);
		resetHttp();
	}

	protected void matchHttp(String text) throws IOException {
		Matcher matcher;

		try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
			String line;

			while ((line = reader.readLine()) != null) {
				matcher = REQUEST_PATTERN.matcher(line);
				if (matcher.matches()) {
					path = matcher.group(2);
					continue;
				}

				matcher = HOST_PATTERN.matcher(line);
				if (matcher.matches()) {
					host = matcher.group(2);
					continue;
				}

				matcher = USER_PATTERN.matcher(line);
				if (matcher.matches()) {
					user = new String(Base64.getDecoder().decode(matcher.group(3)));
					continue;
				}
			}
		}
	}

	private void resetHttp() {
		this.host = null;
		this.path = null;
		this.user = null;
	}

	private void printClosingConnection() {
		System.err
				.println("Closing connection from " + client.getInetAddress().getHostAddress() + ":" + client.getPort()
						+ " to " + destination.getInetAddress().getHostAddress() + ":" + destination.getPort());
	}
}
