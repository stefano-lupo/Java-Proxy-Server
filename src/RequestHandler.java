import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import javax.imageio.ImageIO;

public class RequestHandler implements Runnable {

	/**
	 * Socket connected to client passed by Proxy server
	 */
	Socket clientSocket;

	/**
	 * Read data client sends to proxy
	 */
	BufferedReader proxyToClientBr;

	/**
	 * Send data from proxy to client
	 */
	BufferedWriter proxyToClientBw;
	

	/**
	 * Thread that is used to transmit data read from client to server when using HTTPS
	 * Reference to this is required so it can be closed once completed.
	 */
	private Thread httpsClientToServer;


	/**
	 * Creates a ReuqestHandler object capable of servicing HTTP(S) GET requests
	 * @param clientSocket socket connected to the client
	 */
	public RequestHandler(Socket clientSocket){
		this.clientSocket = clientSocket;
		try{
			this.clientSocket.setSoTimeout(2000);
			proxyToClientBr = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}


	
	/**
	 * Reads and examines the requestString and calls the appropriate method based 
	 * on the request type. 
	 */
	@Override
	public void run() {

		// Get Request from client
		String requestString;
		try{
			requestString = proxyToClientBr.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error reading request from client");
			return;
		}

		// Parse out URL

		System.out.println("Reuest Received " + requestString);
		// Get the Request type
		String request = requestString.substring(0,requestString.indexOf(' '));

		// remove request type and space
		String urlString = requestString.substring(requestString.indexOf(' ')+1);

		// Remove everything past next space
		urlString = urlString.substring(0, urlString.indexOf(' '));

		// Prepend http:// if necessary to create correct URL
		if(!urlString.substring(0,4).equals("http")){
			String temp = "http://";
			urlString = temp + urlString;
		}


		// Check if site is blocked
		if(Proxy.isBlocked(urlString)){
			System.out.println("Blocked site requested : " + urlString);
			blockedSiteRequested();
			return;
		}


		// Check request type
		if(request.equals("CONNECT")){
			System.out.println("HTTPS Request for : " + urlString + "\n");
			handleHTTPSRequest(urlString);
		} 

		else{
			// Check if we have a cached copy
			File file;
			if((file = Proxy.getCachedPage(urlString)) != null){
				System.out.println("Cached Copy found for : " + urlString + "\n");
				sendCachedPageToClient(file);
			} else {
				System.out.println("HTTP GET for : " + urlString + "\n");
				sendNonCachedToClient(urlString);
			}
		}
	} 


	/**
	 * Sends the specified cached file to the client
	 * @param cachedFile The file to be sent (can be image/text)
	 */
	private void sendCachedPageToClient(File cachedFile){
		// Read from File containing cached web page
		try{
			// If file is an image write data to client using buffered image.
			String fileExtension = cachedFile.getName().substring(cachedFile.getName().lastIndexOf('.'));
			
			// Response that will be sent to the server
			String response;
			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
					fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				// Read in image from storage
				BufferedImage image = ImageIO.read(cachedFile);
				
				if(image == null ){
					System.out.println("Image " + cachedFile.getName() + " was null");
					response = "HTTP/1.0 404 NOT FOUND \n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientBw.write(response);
					proxyToClientBw.flush();
				} else {
					response = "HTTP/1.0 200 OK\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientBw.write(response);
					proxyToClientBw.flush();
					ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());
				}
			} 
			
			// Standard text based file requested
			else {
				BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cachedFile)));

				response = "HTTP/1.0 200 OK\n" +
						"Proxy-agent: ProxyServer/1.0\n" +
						"\r\n";
				proxyToClientBw.write(response);
				proxyToClientBw.flush();

				String line;
				while((line = cachedFileBufferedReader.readLine()) != null){
					proxyToClientBw.write(line);
				}
				proxyToClientBw.flush();
				
				// Close resources
				if(cachedFileBufferedReader != null){
					cachedFileBufferedReader.close();
				}	
			}


			// Close Down Resources
			if(proxyToClientBw != null){
				proxyToClientBw.close();
			}

		} catch (IOException e) {
			System.out.println("Error Sending Cached file to client");
			e.printStackTrace();
		}
	}


	/**
	 * Sends the contents of the file specified by the urlString to the client
	 * @param urlString URL ofthe file requested
	 */
	private void sendNonCachedToClient(String urlString){

		try{
			
			// Compute a logical file name as per schema
			// This allows the files on stored on disk to resemble that of the URL it was taken from
			int fileExtensionIndex = urlString.lastIndexOf(".");
			String fileExtension;

			// Get the type of file
			fileExtension = urlString.substring(fileExtensionIndex, urlString.length());

			// Get the initial file name
			String fileName = urlString.substring(0,fileExtensionIndex);


			// Trim off http://www. as no need for it in file name
			fileName = fileName.substring(fileName.indexOf('.')+1);

			// Remove any illegal characters from file name
			fileName = fileName.replace("/", "__");
			fileName = fileName.replace('.','_');
			
			// Trailing / result in index.html of that directory being fetched
			if(fileExtension.contains("/")){
				fileExtension = fileExtension.replace("/", "__");
				fileExtension = fileExtension.replace('.','_');
				fileExtension += ".html";
			}
		
			fileName = fileName + fileExtension;



			// Attempt to create File to cache to
			boolean caching = true;
			File fileToCache = null;
			BufferedWriter fileToCacheBW = null;

			try{
				// Create File to cache 
				fileToCache = new File("cached/" + fileName);

				if(!fileToCache.exists()){
					fileToCache.createNewFile();
				}

				// Create Buffered output stream to write to cached copy of file
				fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));
			}
			catch (IOException e){
				System.out.println("Couldn't cache: " + fileName);
				caching = false;
				e.printStackTrace();
			} catch (NullPointerException e) {
				System.out.println("NPE opening file");
			}





			// Check if file is an image
			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
					fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				// Create the URL
				URL remoteURL = new URL(urlString);
				BufferedImage image = ImageIO.read(remoteURL);

				if(image != null) {
					// Cache the image to disk
					ImageIO.write(image, fileExtension.substring(1), fileToCache);

					// Send response code to client
					String line = "HTTP/1.0 200 OK\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientBw.write(line);
					proxyToClientBw.flush();

					// Send them the image data
					ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());

				// No image received from remote server
				} else {
					System.out.println("Sending 404 to client as image wasn't received from server"
							+ fileName);
					String error = "HTTP/1.0 404 NOT FOUND\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientBw.write(error);
					proxyToClientBw.flush();
					return;
				}
			} 

			// File is a text file
			else {
								
				// Create the URL
				URL remoteURL = new URL(urlString);
				// Create a connection to remote server
				HttpURLConnection proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
				proxyToServerCon.setRequestProperty("Content-Type", 
						"application/x-www-form-urlencoded");
				proxyToServerCon.setRequestProperty("Content-Language", "en-US");  
				proxyToServerCon.setUseCaches(false);
				proxyToServerCon.setDoOutput(true);
			
				// Create Buffered Reader from remote Server
				BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));
				

				// Send success code to client
				String line = "HTTP/1.0 200 OK\n" +
						"Proxy-agent: ProxyServer/1.0\n" +
						"\r\n";
				proxyToClientBw.write(line);
				
				
				// Read from input stream between proxy and remote server
				while((line = proxyToServerBR.readLine()) != null){
					// Send on data to client
					proxyToClientBw.write(line);

					// Write to our cached copy of the file
					if(caching){
						fileToCacheBW.write(line);
					}
				}
				
				// Ensure all data is sent by this point
				proxyToClientBw.flush();

				// Close Down Resources
				if(proxyToServerBR != null){
					proxyToServerBR.close();
				}
			}


			if(caching){
				// Ensure data written and add to our cached hash maps
				fileToCacheBW.flush();
				Proxy.addCachedPage(urlString, fileToCache);
			}

			// Close down resources
			if(fileToCacheBW != null){
				fileToCacheBW.close();
			}

			if(proxyToClientBw != null){
				proxyToClientBw.close();
			}
		} 

		catch (Exception e){
			e.printStackTrace();
		}
	}

	
	/**
	 * Handles HTTPS requests between client and remote server
	 * @param urlString desired file to be transmitted over https
	 */
	private void handleHTTPSRequest(String urlString){
		// Extract the URL and port of remote 
		String url = urlString.substring(7);
		String pieces[] = url.split(":");
		url = pieces[0];
		int port  = Integer.valueOf(pieces[1]);

		try{
			// Only first line of HTTPS request has been read at this point (CONNECT *)
			// Read (and throw away) the rest of the initial data on the stream
			for(int i=0;i<5;i++){
				try {
					String line = proxyToClientBr.readLine();
					if (line == null) {
						System.out.println("No data available from proxyToClientBr");
					}
				} catch (SocketTimeoutException e) {
					// Handle the timeout exception (retry, skip, or take appropriate action)
					System.out.println("Timeout exception");
					e.printStackTrace();
					continue;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// Get actual IP associated with this URL through DNS
			InetAddress address = InetAddress.getByName(url);
			
			// Open a socket to the remote server 
			Socket proxyToServerSocket = new Socket(address, port);
			proxyToServerSocket.setSoTimeout(5000);

			// Send Connection established to the client
			String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
			proxyToClientBw.write(line);
			proxyToClientBw.flush();
			
			
			
			// Client and Remote will both start sending data to proxy at this point
			// Proxy needs to asynchronously read data from each party and send it to the other party


			//Create a Buffered Writer betwen proxy and remote
			BufferedWriter proxyToServerBW = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));

			// Create Buffered Reader from proxy and remote
			BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));



			// Create a new thread to listen to client and transmit to server
			ClientToServerHttpsTransmit clientToServerHttps = 
					new ClientToServerHttpsTransmit(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());
			
			httpsClientToServer = new Thread(clientToServerHttps);
			httpsClientToServer.start();
			
			
			// Listen to remote server and relay to client
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyToServerSocket.getInputStream().read(buffer);
					if (read > 0) {
						clientSocket.getOutputStream().write(buffer, 0, read);
						if (proxyToServerSocket.getInputStream().available() < 1) {
							clientSocket.getOutputStream().flush();
						}
					}
				} while (read >= 0);
			}
			catch (SocketTimeoutException e) {
				
			}
			catch (IOException e) {
				e.printStackTrace();
			}


			// Close Down Resources
			if(proxyToServerSocket != null){
				proxyToServerSocket.close();
			}

			if(proxyToServerBR != null){
				proxyToServerBR.close();
			}

			if(proxyToServerBW != null){
				proxyToServerBW.close();
			}

			if(proxyToClientBw != null){
				proxyToClientBw.close();
			}
			
			
		} catch (SocketTimeoutException e) {
			String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			try{
				proxyToClientBw.write(line);
				proxyToClientBw.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} 
		catch (Exception e){
			System.out.println("Error on HTTPS : " + urlString );
			e.printStackTrace();
		}
	}

	


	/**
	 * Listen to data from client and transmits it to server.
	 * This is done on a separate thread as must be done 
	 * asynchronously to reading data from server and transmitting 
	 * that data to the client. 
	 */
	class ClientToServerHttpsTransmit implements Runnable{
		
		InputStream proxyToClientIS;
		OutputStream proxyToServerOS;
		
		/**
		 * Creates Object to Listen to Client and Transmit that data to the server
		 * @param proxyToClientIS Stream that proxy uses to receive data from client
		 * @param proxyToServerOS Stream that proxy uses to transmit data to remote server
		 */
		public ClientToServerHttpsTransmit(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
			this.proxyToClientIS = proxyToClientIS;
			this.proxyToServerOS = proxyToServerOS;
		}

		@Override
		public void run(){
			try {
				// Read byte by byte from client and send directly to server
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyToClientIS.read(buffer);
					if (read > 0) {
						proxyToServerOS.write(buffer, 0, read);
						if (proxyToClientIS.available() < 1) {
							proxyToServerOS.flush();
						}
					}
				} while (read >= 0);
			}
			catch (SocketTimeoutException ste) {
				// TODO: handle exception
			}
			catch (IOException e) {
				System.out.println("Proxy to client HTTPS read timed out");
				e.printStackTrace();
			}
		}
	}

	
	/**
	 * This method is called when user requests a page that is blocked by the proxy.
	 * Sends an access forbidden message back to the client
	 */
	private void blockedSiteRequested(){
		try {
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
			String line = "HTTP/1.0 403 Access Forbidden \n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			bufferedWriter.write(line);
			bufferedWriter.flush();
		} catch (IOException e) {
			System.out.println("Error writing to client when requested a blocked site");
			e.printStackTrace();
		}
	}
}




