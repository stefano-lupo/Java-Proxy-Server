/*
 * 	Student:		Stefano Lupo
 *  Student No:		14334933
 *  Degree:			JS Computer Engineering
 *  Course: 		3D3 Computer Networks
 *  Date:			21/02/2017
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.net.ssl.HttpsURLConnection;



public class Listener extends Thread{

	private boolean running;

	public static void main(String[] args) {
		Listener myServer = new Listener(8085);




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

		// Once finished listening: Tidy up
		try{
			System.out.println("Terminating Connection");
			myServer.server.close();
			myServer.serverSocket.close();
		} catch (Exception e) {
			System.out.println("Exception closing sockets");
			e.printStackTrace();
		}
	}

	// Server and Socket INFO
	ServerSocket serverSocket;
	Socket server;



	public Listener(int port) {

		try {
			// Open the Socket
			serverSocket = new ServerSocket(port);

			// Set the timeout
			serverSocket.setSoTimeout(100000);
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
			server = serverSocket.accept();
			System.out.println("Just connected to " + server.getRemoteSocketAddress() + "\n");
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
			InputStream proxyToClientIS = server.getInputStream();
			OutputStream proxyToClientOS = server.getOutputStream();
		


			/*
			 * Get the request from the client
			 */
			
			byte[] buffer = new byte[2048];
			int bytesRead;
			String requestString = "";
			while(running){
				while((bytesRead = proxyToClientIS.read(buffer)) != -1){
					requestString = "";
					for(int i=0;i<bytesRead;i++){
						requestString += String.valueOf((char)buffer[i]);
					}
					System.out.println("Request Received: ");
					System.out.println(requestString + "\n");


					// Parse out URL

					// remove GET and space
					String urlString = requestString.substring(requestString.indexOf(' ')+1);

					// Remove everything past next space
					urlString = urlString.substring(0, urlString.indexOf(' '));

					System.out.println("URL String = " + urlString + "\n");
					
					
					
					/*
					 * Create a connection the remote server to retrieve page
					 */

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


					// Get response from remote server
					InputStream proxyToServerIS = proxyToServerCon.getInputStream();
					
					/*
					 *  Relay serverToProxyIS to proxyToClientOS
					 */
					
					// Create buffer to hold chunks of retireved data
					byte by[] = new byte[32768];
					
					// Get first chunk of data
					int index = proxyToServerIS.read(by,0,32768);
					
					System.out.println("Sending results back to client");
					
					// Repeat until end of proxyToServerIS
					while(index != -1){
						// Send chunk to client
						proxyToClientOS.write(by,0,index);
						// Get next chunk
						index = proxyToServerIS.read(by,0,32768);
					}
					
					proxyToClientOS.flush();
					
					System.out.println("Results sent successfully");
					
					break;
				}
				break;
			}
		} catch (IOException e) {
			// TODO: handle exception
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
			DataOutputStream dataOutputStream = new DataOutputStream(server.getOutputStream());
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