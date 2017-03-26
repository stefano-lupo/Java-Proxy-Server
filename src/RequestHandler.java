import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

import javax.imageio.ImageIO;

public class RequestHandler implements Runnable {

	Socket clientSocket;

	/**
	 * Read data client sends to proxy
	 */
	BufferedReader proxyToClientBr;

	/**
	 * Send data from proxy to client
	 */
	BufferedWriter proxyToClientBw;



	public RequestHandler(Socket clientSocket){
		this.clientSocket = clientSocket;
		try{
			proxyToClientBr = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	@Override
	public void run() {
		String requestString;
		try{
			requestString = proxyToClientBr.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		//System.out.println("Request Received: ");
		//System.out.println(requestString + "\n");

		// Parse out URL

		// Check for Request type

		String request = requestString.substring(0,requestString.indexOf(' '));

		//System.out.println("Request = " + request + "!!");

		// remove request type and space
		String urlString = requestString.substring(requestString.indexOf(' ')+1);

		// Remove everything past next space
		urlString = urlString.substring(0, urlString.indexOf(' '));

		// Prepend http to create correct URL
		if(!urlString.substring(0,4).equals("http")){
			String temp = "http://";
			urlString = temp + urlString;
			//System.out.println("Adding 'http://' to url");
		}

		//System.out.println("URL String = " + urlString);



		// Check if site is blocked
		if(Proxy.isBlocked(urlString)){
			//System.out.println("Sorry, that site is blocked");
			return;
		}


		// Check request type
		if(request.equals("CONNECT")){
			//System.out.println("HTTPS Connection here");
			handleHTTPSRequest(urlString);
		} 

		else{
			// Check if we have a cached copy
			File file;
			if((file = Proxy.getCachedPage(urlString)) != null){
				//System.out.println("Cached File Found - Not making HTTP Request to remote server");
				sendCachedPageToClient(file);
			} else {
			//	System.out.println("Webpage not contained in cache - going to remote server");
				sendNonCachedToClient(urlString);
			}
			//System.out.println("Results sent successfully\n\n");
		}
	} 


	private void sendCachedPageToClient(File cachedFile){
		// Read from File containing cached web page
		try{
			String response;
			String fileExtension = cachedFile.getName().substring(cachedFile.getName().lastIndexOf('.'));
			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
					fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
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
			} else {
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


	private void sendNonCachedToClient(String urlString){



		try{
			// Create the URL
			URL remoteURL = new URL(urlString);

			// Compute file name as per schema
			int fileExtensionIndex = urlString.lastIndexOf(".");
			String fileExtension;

			fileExtension = urlString.substring(fileExtensionIndex, urlString.length());

			String fileName = urlString.substring(0,fileExtensionIndex);

			//System.out.println("init file name : " + fileName);
			//System.out.println("init file extension : " + fileExtension);

			// trim off http://www.
			fileName = fileName.substring(fileName.indexOf('.')+1);

			// Remove any illegal characters
			fileName = fileName.replace("/", "__");
			fileName = fileName.replace('.','_');
			if(fileExtension.contains("/")){
				fileExtension = fileExtension.replace("/", "__");
				fileExtension = fileExtension.replace('.','_');
				fileExtension += ".html";
			}
			//System.out.println("Computed file name : " + fileName);
			//System.out.println("Computed file extension : " + fileExtension + "\n\n");
			fileName = fileName + fileExtension;



			// Attempt to create File to cache to

			boolean caching = true;

			File fileToCache = null;
			BufferedWriter fileToCacheBW = null;

			// TODO: Parse out the ?ver=*.*.* into file name so they wont break this
			// At the moment just not caching anything that couldn't be parsed
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
				System.out.println("Non-cachable file : " + fileName);
				caching = false;
			}





			// Check if file is an image
			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
					fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				System.out.println("Retrieving an image");
				BufferedImage image = ImageIO.read(remoteURL);

				if(image != null) {
					System.out.println("Sending Image to client" + fileName);
					// Cache it
					ImageIO.write(image, fileExtension.substring(1), fileToCache);
					
					System.out.println("SENDING SUCCESS CODE for " + fileName);
					String line = "HTTP/1.0 200 OK\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientBw.write(line);
					proxyToClientBw.flush();
					System.out.println("Success code for " + fileName + " sent");
				
					ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());
				

				} else {
					System.out.println("Sending 404 to client " + fileName);
					String error = "HTTP/1.0 404 NOT FOUND\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientBw.write(error);
					proxyToClientBw.flush();
					return;
				}
			} 

			else {
				HttpURLConnection proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
				proxyToServerCon.setRequestProperty("Content-Type", 
						"application/x-www-form-urlencoded");

				proxyToServerCon.setRequestProperty("Content-Language", "en-US");  

				proxyToServerCon.setUseCaches(false);
				proxyToServerCon.setDoOutput(true);

				// Create Buffered Reader from input stream to remote server
				BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));
	
				// Send success code 
				System.out.println("SENDING SUCCESS CODE for " + fileName);
				String line = "HTTP/1.0 200 OK\n" +
						"Proxy-agent: ProxyServer/1.0\n" +
						"\r\n";
				proxyToClientBw.write(line);
				while((line = proxyToServerBR.readLine()) != null){
					// Send data to client
					proxyToClientBw.write(line);

					if(caching){
						// Write data to our cached copy
						fileToCacheBW.write(line);
					}
				}

				proxyToClientBw.flush();

				// Close Down Resources
				if(proxyToServerBR != null){
					proxyToServerBR.close();
				}
			}
			
			


			if(caching){
				fileToCacheBW.flush();

				// Save the file in hash map
				Proxy.addCachedPage(urlString, fileToCache);
			}

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

	private void handleHTTPSRequest(String urlString){


		String url = urlString.substring(0,urlString.length()-4);
		url = url.substring(url.indexOf('.')+1);
		int port = Integer.valueOf(urlString.substring(urlString.length()-3));
		System.out.println("Host  = " + url);
		System.out.println("Port = " + port);

		try{
			InetAddress address = InetAddress.getByName(url);
			Socket proxyToServerSocket = new Socket(address, port);


			System.out.println("Writing to client");
			String line = "HTTP/1.0 200 Connection established\n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			proxyToClientBw.write(line);
			System.out.println("Wrote to client");




			//Create a Buffered Writer from proxy to remote
			BufferedWriter proxyToServerBW = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));

			// Create Buffered Reader from remote to proxy
			BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));





			// Create new thread to listen to client and relay to server
			ClientToServerHttps clientToServerHttps = new ClientToServerHttps(
					proxyToClientBr, proxyToServerBW);
			new Thread(clientToServerHttps).start();

			// Listen to remote server and relay to client
			System.out.println("Starting to lsiten");
			while((line = proxyToServerBR.readLine()) != null){
				// Send data to client
				System.out.println("Server to proxy line : " + line);
				proxyToClientBw.write(line);
				proxyToClientBw.flush();

			}

			


			// Close Down Resources
			/*			if(proxyToServerSocket != null){
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
			}*/
		} catch (Exception e){
			System.out.println("Error on HTTPS : " + urlString );
			e.printStackTrace();
		}
	}

	class ClientToServerHttps implements Runnable{

		// Read from client to proxy
		BufferedReader proxyToClientBR;

		// Write from proxy to Server
		BufferedWriter proxyToServerBW;

		public ClientToServerHttps(BufferedReader proxyToClientBR, BufferedWriter proxyToClientBW) {
			System.out.println("Starting Client to Server transmission thread");
		}

		@Override
		public void run(){
			// Process data from Client and send to server
			String line;
			try{
				while((line = proxyToClientBR.readLine()) != null){
					// Send data to client
					System.out.println("Client to proxy Line = " + line);
					proxyToServerBW.write(line);
					proxyToServerBW.flush();
				}
			} catch (Exception e) {
				System.out.println("Error in reading from client https");
				e.printStackTrace();
			}
		}
	}
}



