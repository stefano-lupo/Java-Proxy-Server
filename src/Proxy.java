/*
 * 	Student:		Stefano Lupo
 *  Student No:		14334933
 *  Degree:			JS Computer Engineering
 *  Course: 		3D3 Computer Networks
 *  Date:			21/02/2017
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.Properties;



public class Listener extends Thread{

	private boolean running;
	private HashMap<String, File> cache;

	public static void main(String[] args) {
		Listener myServer = new Listener(8085);

		// Load in hash map containing previously cached sites
		myServer.cache = new HashMap<>();
		try{
			FileInputStream fileInputStream = new FileInputStream("cachedSites.txt");
			ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
			myServer.cache = (HashMap<String,File>)objectInputStream.readObject();
		} catch (IOException e) {
			System.out.println("Error loading previously cached sites file");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.out.println("class not found loading in preivously cached sites file");
			e.printStackTrace();
		}


		// Start transmission thread
		//myServer.start();			

		// Start receiver thread
		myServer.listen();	


		try{
			myServer.join();
		} 
		catch (InterruptedException e) {
			System.out.println("Interuption while waiting for transmitter to send final ack");
		}

		// Once finished listening: Save cached sites
		try{
		FileOutputStream fileOutputStream = new FileOutputStream("cachedSites.txt");
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
		
		objectOutputStream.writeObject(myServer.cache);
		objectOutputStream.close();
		fileOutputStream.close();
		
		} catch (IOException e) {
			System.out.println("Error saving cache");
			e.printStackTrace();
		}
		

		try{
			System.out.println("Terminating Connection");
			myServer.clientSocket.close();
			myServer.serverSocket.close();
		} catch (Exception e) {
			System.out.println("Exception closing sockets");
			e.printStackTrace();
		}
	}

	// Server and Socket INFO
	ServerSocket serverSocket;
	Socket clientSocket;



	public Listener(int port) {
		//cache = new HashMap<>();
		try {
			// Open the Socket
			serverSocket = new ServerSocket(port);

			// Set the timeout
			serverSocket.setSoTimeout(100000);
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
			clientSocket = serverSocket.accept();
			System.out.println("Just connected to " + clientSocket.getRemoteSocketAddress() + "\n");
			running = true;
		} 

		// Catch exceptions associated with opening socket
		catch (SocketException se) {
			System.out.println("Socket Exception when connecting to client");
			se.printStackTrace();
		}
		catch (SocketTimeoutException ste) {
			System.out.println("Timeout occured while connecting to client");
		} 
		catch (IOException io) {
			System.out.println("IO exception when connecting to client");
		}
	}


	/**
	 * Receives Frames from client
	 */
	public void listen(){
		try{

			// Initalize everything
			InputStream proxyToClientIS = clientSocket.getInputStream();
			BufferedReader proxyToClient = new BufferedReader(new InputStreamReader(proxyToClientIS));
			OutputStream proxyToClientOS = clientSocket.getOutputStream();



			/*
			 * Get the request from the client
			 */

/*			byte[] buffer = new byte[2048];
			int bytesRead;

			int readLines = 0;*/
			
			String requestString = "";
			while(running){
				//				while((bytesRead = proxyToClientIS.read(buffer)) != -1){
				//					if(readLines == 0){
				//						readLines++;
				//						requestString = "";
				//						for(int i=0;i<bytesRead;i++){
				//							requestString += String.valueOf((char)buffer[i]);
				//						}
				while((requestString = proxyToClient.readLine()) != null){
					System.out.println("Request Received: ");
					System.out.println(requestString + "\n");

					// Parse out URL

					// remove GET and space
					String urlString = requestString.substring(requestString.indexOf(' ')+1);

					// Remove everything past next space
					urlString = urlString.substring(0, urlString.indexOf(' '));
					
					if(!urlString.substring(0,4).equals("http")){
						String temp = "http://";
						urlString = temp + urlString;
						System.out.println("Adding 'http://' to url");
					}

					System.out.println("URL String = " + urlString);
					
					String fileName = urlString.substring(urlString.indexOf('.')+1);
					fileName = fileName.replace("/", "__");
					fileName = fileName.replace('.','_');
					System.out.println("Computed file name : " + fileName +"\n\n");

					if(cache.get(urlString) != null){
						System.out.println("Cached File Found - Not making HTTP Request to remote server");
				
						// Read from File containing cached webpage
						FileInputStream cachedFileInputStream = new FileInputStream("cached/" + fileName + ".html");
						BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(cachedFileInputStream));
						
						// Write to clients output stream
						BufferedWriter proxyToClientBufferedreader = new BufferedWriter(new OutputStreamWriter(proxyToClientOS));
											
						
						
						String line;
						while((line = cachedFileBufferedReader.readLine()) != null){
							proxyToClientBufferedreader.write(line);
						}
						proxyToClientBufferedreader.flush();
						
						
						// Close Down Resources
						if(proxyToClientBufferedreader != null){
							proxyToClientBufferedreader.close();
						}
						
						if(cachedFileBufferedReader != null){
							cachedFileBufferedReader.close();
						}						
						
					} else {
						/*
						 * Create a connection the remote server to retrieve page
						 */
						
						System.out.println("Webpage not contained in cache - going to remote server");
						URL remoteURL = null;
						HttpURLConnection proxyToServerCon = null;
						
						remoteURL = new URL(urlString);
						proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
						proxyToServerCon.setRequestProperty("Content-Type", 
								"application/x-www-form-urlencoded");
						;
						proxyToServerCon.setRequestProperty("Content-Language", "en-US");  

						proxyToServerCon.setUseCaches(false);
						proxyToServerCon.setDoOutput(true);

						// Create Buffered Reader from input stream to remote server
						BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));
						
						
						// End Remote code
						
						
						
						// Create File to cache 
						File fileToCache = new File("cached/" + fileName + ".html");
						
						if(!fileToCache.exists()){
							fileToCache.createNewFile();
						}
						
						// Create Buffered output stream to write to cached copy of file
						BufferedWriter fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));
						
						
						// End File Code
						
						
						
						// Create Buffered output stream for writing to client
						BufferedWriter proxyToClientBW = new BufferedWriter(new OutputStreamWriter(proxyToClientOS));						
						
						
						// Process data from remote server
						String line;
						while((line = proxyToServerBR.readLine()) != null){
							// Send data to client
							proxyToClientBW.write(line);
							
							// Write data to our cached copy
							fileToCacheBW.write(line);
						}
						
						proxyToClientBW.flush();
						fileToCacheBW.flush();
						
						// Save the file in hash map
						cache.put(urlString, fileToCache);
						
						// Close Down Resources
						if(proxyToServerBR != null){
							proxyToServerBR.close();
						}
						
						
						if(fileToCacheBW != null){
							fileToCacheBW.close();
						}
						
						if(proxyToClientBW != null){
							proxyToClientBW.close();
						}
						
						
					
						

						/*
						 *  Relay serverToProxyIS to proxyToClientOS
						 */
						
						/*

						// Create buffer to hold chunks of retireved data
						byte[] by = new byte[32768];

						// Get first chunk of data
						int index = proxyToServerIS.read(by,0,32768);

						System.out.println("Sending results back to client and creating local copy");
						
						
						
						BufferedWriter bufferedWriter = null;
						File file = null;
						try {

							file = new File("cached/" + fileName + ".html");
							if(!file.exists()){
								System.out.println("File not found, creating new file");
								file.createNewFile();
							}
							
							FileWriter fWriter = new FileWriter(file);
							bufferedWriter = new BufferedWriter(fWriter);
							
						} catch (IOException e) {
							System.out.println("error with file code");
							e.printStackTrace();
						}
						
						

						// Repeat until end of proxyToServerIS
						while(index != -1){
							// Send chunk to client
							proxyToClientOS.write(by,0,index);
							
							// write to our cached file
							bufferedWriter.write(new String(by));
							
							// Get next chunk
							index = proxyToServerIS.read(by,0,32768);
						}

						proxyToClientOS.flush();
						cache.put(urlString, file);
						
	
						
						if(bufferedWriter != null){
							bufferedWriter.close();
						}
						*/
					}
					

					System.out.println("Results sent successfully");
					break;
				}
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	/**
	 * Thread for transmitting frames to client
	 */
	@Override
	public void run() {
		try{
			// Initialize output streams
			DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(dataOutputStream);

			// Close output streams
			dataOutputStream.close();
			objectOutputStream.close();

			// Catch Opening Streams Exceptions
		} catch (IOException e) {
			System.out.println("IOException talking to client");
			e.printStackTrace();
		}
	}

}