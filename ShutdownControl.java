import java.util.LinkedList;
import java.util.List;

public class ShutdownControl {
	private List<ConnectionControl> controlList = new LinkedList<>();
	
	public void add(ConnectionControl connectionControl) {
		controlList.add(connectionControl);
	}
	
	public void remove(ConnectionControl connectionControl) {
		controlList.remove(connectionControl);
	}
	
	public void shutdown() {
		for(ConnectionControl connectionControl : controlList) {
			connectionControl.closeConnection();
		}
		
		controlList = null;
	}
}
