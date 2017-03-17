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



public class Proxy extends Thread{

	private boolean running;

	public static void main(String[] args) {
		try{
			ServerSocket server = new ServerSocket(8085);
			Socket socket = server.accept();
			boolean running = true;
			InputStream inputStream = socket.getInputStream();


			int bytesRead;
			byte[] buffer = new byte[2048];
			String result = "";
			boolean stringRead = false;
			while(running){
			
			
				while((bytesRead = inputStream.read(buffer)) != -1){
					System.out.println("bytes read = " + bytesRead);
					for(int i=0;i<bytesRead;i++){
						result += (char)buffer[i];
					}
					System.out.println("Setting stringread true");
					stringRead = true;
					System.out.println("Result = " + result);
				}
				
				System.out.println("reached");
				if(stringRead){
					System.out.println("Result = " + result);
					stringRead = false;
				}
			}



		} catch (IOException e) {
			e.printStackTrace();
		}


	}
}
