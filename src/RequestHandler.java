import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;

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
		System.out.println("Request Received: ");
		System.out.println(requestString + "\n");

		// Parse out URL

		// remove GET and space
		String urlString = requestString.substring(requestString.indexOf(' ')+1);

		// Remove everything past next space
		urlString = urlString.substring(0, urlString.indexOf(' '));

		// Prepend http to create correct URL
		if(!urlString.substring(0,4).equals("http")){
			String temp = "http://";
			urlString = temp + urlString;
			System.out.println("Adding 'http://' to url");
		}

		System.out.println("URL String = " + urlString);

		
		// Check if site is blocked
		if(Proxy.isBlocked(urlString)){
			System.out.println("Sorry, that site is blocked");
			return;
		}
		
		// Check if we have a cached copy
		File file;
		if((file = Proxy.getCachedPage(urlString)) != null){
			System.out.println("Cached File Found - Not making HTTP Request to remote server");
			sendCachedPageToClient(file);
		} else {
			System.out.println("Webpage not contained in cache - going to remote server");
			sendNonCachedToClient(urlString);
		}
		System.out.println("Results sent successfully");
	} 


	private void sendCachedPageToClient(File cachedFile){
		// Read from File containing cached web page
		try{
			BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cachedFile)));

			String line = "HTTP/1.0 200 Connection established\n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";

			while((line = cachedFileBufferedReader.readLine()) != null){
				proxyToClientBw.write(line);
			}
			proxyToClientBw.flush();


			// Close Down Resources
			if(proxyToClientBw != null){
				proxyToClientBw.close();
			}

			if(cachedFileBufferedReader != null){
				cachedFileBufferedReader.close();
			}	
		} catch (IOException e) {
			System.out.println("Error Sending Cached file to client");
			e.printStackTrace();
		}
	}


	private void sendNonCachedToClient(String urlString){
		URL remoteURL = null;
		HttpURLConnection proxyToServerCon = null;

		try{
			remoteURL = new URL(urlString);
			proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
			proxyToServerCon.setRequestProperty("Content-Type", 
					"application/x-www-form-urlencoded");

			proxyToServerCon.setRequestProperty("Content-Language", "en-US");  

			proxyToServerCon.setUseCaches(false);
			proxyToServerCon.setDoOutput(true);

			// Create Buffered Reader from input stream to remote server
			BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));


			// End Remote code


			// Compute file name as per schema
			String fileName = urlString.substring(urlString.indexOf('.')+1);
			fileName = fileName.replace("/", "__");
			fileName = fileName.replace('.','_');
			System.out.println("Computed file name : " + fileName +"\n\n");

			// Create File to cache 
			File fileToCache = new File("cached/" + fileName + ".html");

			if(!fileToCache.exists()){
				fileToCache.createNewFile();
			}

			// Create Buffered output stream to write to cached copy of file
			BufferedWriter fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));


			// End File Code						


			// Process data from remote server
			String line = "Status: 200 OK";
			proxyToClientBw.write(line);
			while((line = proxyToServerBR.readLine()) != null){
				// Send data to client
				proxyToClientBw.write(line);

				// Write data to our cached copy
				fileToCacheBW.write(line);
			}

			proxyToClientBw.flush();
			fileToCacheBW.flush();

			// Save the file in hash map
			Proxy.addCachedPage(urlString, fileToCache);


			// Close Down Resources
			if(proxyToServerBR != null){
				proxyToServerBR.close();
			}


			if(fileToCacheBW != null){
				fileToCacheBW.close();
			}

			if(proxyToClientBw != null){
				proxyToClientBw.close();
			}
		} catch (Exception e){
			e.printStackTrace();
		}
	}

}



