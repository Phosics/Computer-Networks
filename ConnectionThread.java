import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class ConnectionThread implements Runnable {
	private static final byte CONNECT_NULL_VALUE = 0;

	private static final byte CONNECT_RESPONSE_VN = 0;
	private static final byte CONNECT_RESPONSE_CN_GRANTED = 90;
	private static final byte CONNECT_RESPONSE_CN_FAILED = 91;

	private static final int CONNECT_VN_INDEX = 0;
	private static final int CONNECT_CD_INDEX = 1;
	private static final int CONNECT_PORT_INDEX = 2;
	private static final int CONNECT_IP_INDEX = 4;

	private static final int SOCKET_CONNECT_TIMEOUT = 1000 * 10; // 10 sec
	private static final int SOCKET_READ_TIMEOUT = 1000 * 10; // 10 sec

	private static final byte SOCKS_SUPPORTED_VERSION = 4;
	private static final byte OTHER_SOCKS_VERSION = 5;

	private Socket client;
	private ShutdownControl shutdownControl;

	public ConnectionThread(Socket client, ShutdownControl shutdownControl) {
		this.client = client;
		this.shutdownControl = shutdownControl;
	}

	@Override
	public void run() {
		DataTransferThread clientDataThread = null;
		DataTransferThread destinationDataThread = null;
		Socket destination = null;
		ByteBuffer dataBuffer = null;

		try {
			dataBuffer = connectSocks(client.getInputStream());
			client.setSoTimeout(SOCKET_READ_TIMEOUT);

			if (!isSupportedVersion(dataBuffer)) {
				closingConnectionUnsuportedSocks(dataBuffer);
				return;
			}

			String hostName = connectSocks4A(dataBuffer, client.getInputStream());
			destination = connectDestination(dataBuffer, hostName);

			if (!destination.isConnected()) {
				closeConnection(client);
				printClosingConnection(client);
				return;
			}

			destination.setSoTimeout(SOCKET_READ_TIMEOUT);

			replaySocks(client.getOutputStream(), CONNECT_RESPONSE_CN_GRANTED, dataBuffer);
			printConnected(client, destination);

			ConnectionControl connectionControl = new ConnectionControl();
			shutdownControl.add(connectionControl);

			clientDataThread = new DataTransferThread(client, destination, connectionControl);
			destinationDataThread = new DataTransferThread(destination, client, connectionControl);

			clientDataThread.start();
			destinationDataThread.start();

			clientDataThread.join();
			destinationDataThread.join();

			shutdownControl.remove(connectionControl);
			closeConnection(client);
			closeConnection(destination);
		} catch (Exception e) {
			System.err.println("Got an error:" + e.getMessage());

			try {
				if (!client.isClosed()) {
					closeConnection(client);
					printClosingConnection(client);
				}

				if (!destination.isClosed()) {
					closeConnection(destination);
					printClosingConnection(destination);
				}
			} catch (IOException e1) {
			}
		}
	}

	private void closeConnection(Socket connection) throws IOException {
		if (!connection.isClosed()) {
			connection.close();
		}
	}

	private void closingConnectionUnsuportedSocks(ByteBuffer dataBuffer) throws IOException {
		byte socksVersion = dataBuffer.get(CONNECT_VN_INDEX);

		System.err.println("Connection error: while parsing request: Unsupported SOCKS protocol version (got "
				+ socksVersion + ")");
		printClosingConnection(client);

		if (socksVersion == OTHER_SOCKS_VERSION) {
			replaySocks(client.getOutputStream(), CONNECT_RESPONSE_CN_FAILED, dataBuffer);
		} else {
			client.close();
		}
	}

	private boolean isSupportedVersion(ByteBuffer dataBuffer) {
		return dataBuffer.get(CONNECT_VN_INDEX) == SOCKS_SUPPORTED_VERSION;
	}

	private Socket connectDestination(ByteBuffer data, String hostName) throws UnknownHostException, IOException {
		byte[] ip = ByteBuffer.allocate(4).putInt(data.getInt(CONNECT_IP_INDEX)).array();
		Socket socket = new Socket();

		try {
			if (hostName == null) {
				socket.connect(
						new InetSocketAddress(InetAddress.getByAddress(ip), (int) data.getShort(CONNECT_PORT_INDEX)),
						SOCKET_CONNECT_TIMEOUT);
			} else {
				socket.connect(new InetSocketAddress(hostName, (int) data.getShort(CONNECT_PORT_INDEX)),
						SOCKET_CONNECT_TIMEOUT);
			}
		} catch (SocketTimeoutException e) {
			System.err.println("Connection error: while connecting to destination: connect timed out");
			printClosingConnection(client);
		} catch (Exception e) {
			System.err.println("Got an error:" + e.getMessage());
			replaySocks(client.getOutputStream(), CONNECT_RESPONSE_CN_FAILED, data);
		}

		return socket;
	}

	private void replaySocks(OutputStream clientOutputStream, byte repsonseStatus, ByteBuffer input)
			throws IOException {
		ByteBuffer outBuffer = input.duplicate();
		outBuffer.put(CONNECT_VN_INDEX, CONNECT_RESPONSE_VN);
		outBuffer.put(CONNECT_CD_INDEX, repsonseStatus);

		clientOutputStream.write(outBuffer.array());
	}

	private ByteBuffer connectSocks(InputStream clientInputStream) throws IOException {
		byte[] clientConnectBuffer = new byte[8];

		clientInputStream.read(clientConnectBuffer);
		ByteBuffer readBuffer = ByteBuffer.wrap(clientConnectBuffer);

		if (!isSupportedVersion(readBuffer)) {
			return readBuffer;
		}

		if (clientInputStream.available() > 0) {
			// Reading the rest of the bytes until getting NULL
			byte reminder = (byte) clientInputStream.read();

			while (reminder != CONNECT_NULL_VALUE) {
				reminder = (byte) clientInputStream.read();
			}
		}

		return readBuffer;
	}

	private String connectSocks4A(ByteBuffer data, InputStream clientInputStream) throws IOException {
		if (data.getInt(CONNECT_IP_INDEX) >= 256 || data.getInt(CONNECT_IP_INDEX) <= 0) {
			return null;
		}

		byte[] buffer = new byte[4096];
		int amount = clientInputStream.read(buffer);

		return new String(buffer, 0, amount);
	}

	private void printConnected(Socket client, Socket destination) {
		System.err.println(
				"Successful connection from " + client.getInetAddress().getHostAddress() + ":" + client.getPort()
						+ " to " + destination.getInetAddress().getHostAddress() + ":" + destination.getPort());
	}

	private void printClosingConnection(Socket connection) {
		System.err.println(
				"Closing connection from " + connection.getInetAddress().getHostAddress() + ":" + connection.getPort());
	}
}
