
public class ConnectionControl {
	private volatile boolean isConnectionOpen = true;

	public void closeConnection() {
		isConnectionOpen = false;
	}

	public boolean isConnectionOpen() {
		return isConnectionOpen;
	}
}
