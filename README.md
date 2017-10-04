# Java HTTP/HTTPS Proxy Server
## The Proxy Server
A proxy server is a server that sits between the client and the remote server in which the client wishes to retrieve files from. All traffic that originates from the client, is sent to the proxy server and the proxy server makes requests to the remote server on the client’s behalf. Once the proxy server receives the required files, it then forwards them on to the client. This can be beneficial as it allows the proxy server administrator some control over what the machines on its network can do. For example, certain websites may be blocked by the proxy server, meaning clients will not be able to access them. It is also beneficial as frequently visited pages can be cached by the proxy server. This means that when the client (or other clients) make subsequent requests for any files that have been cached, the proxy can issue them the files straight away, without having to request them from the remote server which can be much quicker if both the proxy and the clients are on the same network. Although these files are known to be contained in the proxy’s cache, it is worth noting that the clients have no knowledge of this and may be maintaining their own local caches. The benefit of the proxy cache is when multiple clients are using the proxy and thus pages cached due to one client can be accessed by another client.

## The Implementation
The proxy was implemented using Java and made extensive use of TCP sockets. Firefox was set up to issue all of its traffic to the specified port and ip address which were then used in the proxy configuration. There are two main components to the implementation - the Proxy class and the RequestHandler class. 
The Proxy Class
The Proxy class is responsible for creating a ServerSocket which can accept incoming socket connections from the client. However it is vital that the implementation be multithreaded as the server must be able to serve multiple clients simultaneously. Thus once a socket connection arrives, it is accepted and the Proxy creates a new thread which services the request (see the RequestHandler class). As the server does not need to wait for the request to be fully serviced before accepting a new socket connection, multiple clients may have their requests serviced asynchronously. 

The Proxy class is also responsible for implementing caching and blocking functionality. That is, the proxy is able to cache sites that are requested by clients and dynamically block clients from visiting certain websites. As speed is of utmost importance for the proxy server, it is desirable to store references to currently blocked sites and sites that are contained in the cache in a data structure with an expected constant order lookup time. For this reason a HashMap was chosen. This results in extremely fast cache and blocked site lookup times. This results in only a small overhead if the file is not contained in the cache, and an increase in performance if the file was contained in the cache.
Finally the proxy class is also responsible for the providing a dynamic console management system. This allows an administrator to add/remove files to and from the cache and websites to and from the blacklist, in real time. 

## The RequestHandler Class
The RequestHandler class is responsible for servicing the requests that come through to the proxy. The RequestHandler examines the request received and services the request appropriately. The requests can be subdivided into three main categories - HTTP GET requests, HTTP GET requests for file contained in the cache and HTTPS CONNECT requests.
### HTTP GET
These are the standard requests made when a client attempts to load a webpage. Servicing these requests is a simple task:
-Parse out the URL associated with the request.
-Create a HTTP connection to this URL.
-Echo the client’s GET request to the remote servr.
-Echo the server’s response back to the cliet.
-Save a local copy of the file into the proxy’s cache.
### HTTP GET for File in Cache
As before, these are the typical requests made by clients, only in this case, the file is contained in the proxy’s cache.
-Parse out the URL associated with the request
-Hash the URL and use this as the key for the HashMap data structure.
-Open the resulting file for reading.
-Echo the contents of the file back to the client.
-Close the file.
### HTTPS -  CONNECT Requests
HTTPS connections make use of secure sockets (SSL). Data transferred between the client and the server is encrypted. This is widely used in the financial sector in order to ensure secure transactions, but is becoming increasingly more widespread on the internet.
However at first glance it poses a problem for proxy servers: How is the proxy to know what to do with this encrypted data coming from the client?
In order to overcome this problem, initially, another type of HTTP request is made by the client, a CONNECT request. This request is standard HTTP and thus is unencrypted and contains the address of who the client wants to create a HTTPS connection with and this  can be extracted by the proxy. This is a process known as HTTP Connect Tunneling and works as follows:
-Client issues a CONNECT Request
-Proxy extracts the destination URL.
-Proxy creates a standard socket connection to the remote server specified by the URL.
-If successful, the proxy sends a ‘200 Connection Established ‘ response to the client, indicating that the client can now begin to transmit the encrypted data to the proxy.
-The proxy then simultaneously forwards any data sent to it from the client to the remote server, and any data received from the remote server back to the client.

All of this data will be encrypted and thus the proxy cannot cache or even interpret the data. 
