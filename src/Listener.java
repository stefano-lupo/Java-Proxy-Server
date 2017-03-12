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
import java.nio.file.Files;
import java.nio.file.Path;

import javax.net.ssl.HttpsURLConnection;



public class Listener extends Thread{

	private boolean running;

	public static void main(String[] args) {
		Listener myServer = new Listener(8081);




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

			// Wait for timeout time for client to connect (optional)
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
			URL url = null;
			HttpURLConnection connection = null;


			InputStream inputStream = server.getInputStream();
			byte[] buffer = new byte[2048];
			int bytesRead;
			String string = "";
			while(running){
				while((bytesRead = inputStream.read(buffer)) != -1){
					string = "";
					for(int i=0;i<bytesRead;i++){
						string += String.valueOf((char)buffer[i]);
					}
					System.out.println(string);
					String urlString = string.substring(string.indexOf(' ')+1);
					urlString = urlString.substring(0, urlString.indexOf(' '));
					System.out.println("URL String = " + urlString);
					System.out.println();
					url = new URL(urlString);
					connection = (HttpURLConnection)url.openConnection();
					connection.setRequestProperty("Content-Type", 
							"application/x-www-form-urlencoded");
					;
					connection.setRequestProperty("Content-Language", "en-US");  

					connection.setUseCaches(false);
					connection.setDoOutput(true);


					// Get response
					InputStream is = connection.getInputStream();
					BufferedReader rd = new BufferedReader(new InputStreamReader(is));
					StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
					String line;
					while ((line = rd.readLine()) != null) {
						response.append(line);
						response.append('\r');
					}
					rd.close();
					System.out.println();
					System.out.println(response.toString());

					// Write to file
					BufferedWriter bw = null;
					try {
						File file = new File("results.html");

						/* This logic will make sure that the file 
						 * gets created if it is not present at the
						 * specified location*/
						if (!file.exists()) {
							file.createNewFile();
						}

						FileWriter fw = new FileWriter(file);
						bw = new BufferedWriter(fw);
						bw.write(response.toString());

						System.out.println("About to write to output stream");
						try (OutputStream out = server.getOutputStream()) {
							Path path = file.toPath();
							long bytesWritten = Files.copy(path, out);
							out.flush();
							System.out.println(bytesWritten + " written to output stream");
						} catch (IOException e) {
							e.printStackTrace();
						}

					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
					finally
					{ 
						try{
							if(bw!=null)
								bw.close();
						}catch(Exception ex){
							System.out.println("Error in closing the BufferedWriter"+ex);
						}
					}
				}
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
