import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Sockspy {
	private static final int LISTEN_PORT = 8080;
	private static final int THREADS_AMOUNT = 20;

	private static ShutdownControl shutdownControl = new ShutdownControl();

	public static void main(String[] args) {
		ExecutorService executor = Executors.newFixedThreadPool(THREADS_AMOUNT);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Closing all the remaining connections");
				executor.shutdown();
				shutdownControl.shutdown();
			}
		});

		try (ServerSocket server = new ServerSocket(LISTEN_PORT)) {
			while (true) {
				Socket client = server.accept();
				executor.submit(new ConnectionThread(client, shutdownControl));
			}
		} catch (IOException e) {
			System.err.println("Got an error:" + e.getMessage());
		}
	}
}
