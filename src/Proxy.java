/*
 * 	Student:		Stefano Lupo
 *  Student No:		14334933
 *  Degree:			JS Computer Engineering
 *  Course: 		3D3 Computer Networks
 *  Date:			21/02/2017
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Scanner;



public class Proxy implements Runnable{



	public static void main(String[] args) {
		Proxy myProxy = new Proxy(8085);
		myProxy.listen();	
	}



	ServerSocket serverSocket;
	private volatile boolean running = true;


	static HashMap<String, File> cache;
	static HashMap<String, String> blockedSites;




	public Proxy(int port) {

		// Load in hash map containing previously cached sites and blocked Sites
		cache = new HashMap<>();
		blockedSites = new HashMap<>();

		// Start manager
		new Thread(this).start();

		try{
			File cachedSites = new File("cachedSites.txt");
			if(!cachedSites.exists()){
				System.out.println("No cached sites found - creating new file");
				cachedSites.createNewFile();
			} else {
				FileInputStream fileInputStream = new FileInputStream(cachedSites);
				ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
				cache = (HashMap<String,File>)objectInputStream.readObject();
				fileInputStream.close();
				objectInputStream.close();
			}


			File blockedSitesTxtFile = new File("blockedSites.txt");
			if(!blockedSitesTxtFile.exists()){
				System.out.println("No blocked sites found - creating new file");
				blockedSitesTxtFile.createNewFile();
			} else {
				FileInputStream fileInputStream = new FileInputStream(blockedSitesTxtFile);
				ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
				blockedSites = (HashMap<String, String>)objectInputStream.readObject();
				fileInputStream.close();
				objectInputStream.close();
			}
		} catch (IOException e) {
			System.out.println("Error loading previously cached sites file");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.out.println("class not found loading in preivously cached sites file");
			e.printStackTrace();
		}

		try {
			// Open the Socket
			serverSocket = new ServerSocket(port);

			// Set the timeout
			serverSocket.setSoTimeout(100000);
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
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
	 * Listens to port and accepts new socket connections and creates a new thread
	 * to handle the request and passes off the socket connection and continues listening.
	 */
	public void listen(){

		int handlerID = 0;

		while(running){
			try {
				// Blocks until a connection is made
				Socket socket = serverSocket.accept();

				//System.out.println("Connection found: " + socket.getRemoteSocketAddress());
				//System.out.println("Creating Handler [" + handlerID + "]" );
				new Thread(new RequestHandler(socket)).start();
				handlerID++;	
			} catch (SocketException e) {
				System.out.println("Server closing..");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}



	private void closeServer(){
		// Once finished listening: Save cached and blocked sites
		try{
			FileOutputStream fileOutputStream = new FileOutputStream("cachedSites.txt");
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

			objectOutputStream.writeObject(cache);
			objectOutputStream.close();
			fileOutputStream.close();
			
			FileOutputStream fileOutputStream2 = new FileOutputStream("blockedSites.txt");
			ObjectOutputStream objectOutputStream2 = new ObjectOutputStream(fileOutputStream2);
			objectOutputStream2.writeObject(blockedSites);
			objectOutputStream2.close();
			fileOutputStream2.close();

		} catch (IOException e) {
			System.out.println("Error saving cache");
			e.printStackTrace();
		}



		// Close Server Socket
		try{
			System.out.println("Terminating Connection");
			serverSocket.close();
		} catch (Exception e) {
			System.out.println("Exception closing sockets");
			e.printStackTrace();
		}

	}


	/**
	 * Looks for File in cache
	 * @param url of file 
	 * @return File if file is cached, null otherwise
	 */
	public static File getCachedPage(String url){
		return cache.get(url);
	}


	/**
	 * Adds a new page to the cache
	 * @param urlString URL of webpage to cache 
	 * @param fileToCache File Object pointing to File put in cache
	 */
	public static void addCachedPage(String urlString, File fileToCache){
		cache.put(urlString, fileToCache);
	}

	/**
	 * Check if a URL is blocked by the proxy
	 * @param url URL to check
	 * @return true if URL is blocked, false otherwise
	 */
	public static boolean isBlocked (String url){
		if(blockedSites.get(url) != null){
			return true;
		} else {
			return false;
		}
	}




	/**
	 * Creates a management interface which can dynamically update the proxy configurations
	 */
	@Override
	public void run() {
		Scanner scanner = new Scanner(System.in);

		String command;
		while(running){
			System.out.println("Enter new site to block, or type \"blocked\" to see blocked sites, \"cached\" to see cached sites, or \"close\" to close server.");
			command = scanner.nextLine();
			if(command.equals("blocked")){
				System.out.println("\nCurrently Blocked Sites");
				for(String key : blockedSites.keySet()){
					System.out.println(blockedSites.get(key));
				}
				System.out.println();
			} 
			
			else if(command.equals("cached")){
				System.out.println("\nCurrently Cached Sites");
				for(String key : cache.keySet()){
					System.out.println(cache.get(key));
				}
				System.out.println();
			}
			
			
			else if(command.equals("close")){
				running = false;
				closeServer();
			}
			
			
			else {
				blockedSites.put(command, command);
				System.out.println("\n" + command + " blocked successfully \n");
			}
		}
		scanner.close();
	} 

}