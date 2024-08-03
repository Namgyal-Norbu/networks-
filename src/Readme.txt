IN2011 Computer Networks
Coursework 2023/2024 Resit
Submission by
Tenzin Norbu 
220052955
Tenzin.norbu@city.ac.uk

==================================================
README
==================================================

Contents:
1. Project Overview
2. Instructions to Build and Run
3. Functionality

==================================================
1. Project Overview
==================================================
This project involves the implementation of a DNS Resolver and Name Server in Java. The project includes the following components:
- `StubResolver.java`: Handles DNS queries using a recursive resolver.
- `Resolver.java`: Handles iterative resolution of DNS queries.
- `NameServer.java`: Handles incoming DNS queries from multiple clients, maintains a cache of live resource records, and performs iterative resolution.

==================================================
2. Instructions to Build and Run
==================================================
To build and run the project, follow these steps:

### Prerequisites
- Ensure that you have Java Development Kit (JDK) installed. You can download it from [Oracle's official website](https://www.oracle.com/java/technologies/javase-downloads.html).
- Set up your Java environment variables (JAVA_HOME and PATH) correctly.

### Compilation
1. Open a terminal or command prompt.
2. Navigate to the directory containing your `.java` files.
3. Compile the Java files using the following command:
javac StubResolver.java Resolver.java NameServer.java



### Running the Name Server
1. To start the Name Server, use the the commands:

java NameServer

csharp
Copy code
The server will start and listen for incoming DNS queries on the specified port (default is 5353). You can change the port number in the `main` method of the `NameServer` class.

### Testing the Resolvers
1. You can create separate test classes or methods to test the functionality of `StubResolver` and `Resolver`.
2. Here is an example of how you can test `Resolver`:
```java
public static void main(String[] args) throws Exception {
    Resolver resolver = new Resolver();
    resolver.setNameServer(InetAddress.getByName("8.8.8.8"), 53);

    // Test iterative resolution for A record
    InetAddress address = resolver.iterativeResolveAddress("example.com");
    System.out.println("Resolved IP Address: " + address.getHostAddress());

    // Test iterative resolution for TXT record
    String text = resolver.iterativeResolveText("example.com");
    System.out.println("Resolved TXT: " + text);

    // Test iterative resolution for NS record
    String ns = resolver.iterativeResolveName("example.com", 2);
    System.out.println("Resolved NS: " + ns);
}
3. Functionality
The following functionalities are implemented and expected to work:

StubResolver.java
Recursive Resolution: Handles recursive DNS resolution for A, NS, MX, TXT, and CNAME records using a specified DNS server.
Resolver.java
Iterative Resolution: Performs iterative DNS resolution for A, NS, MX, TXT, and CNAME records using a specified DNS server.
Error Handling: Manages various error cases including invalid responses and unresponsive servers.
NameServer.java
Concurrent Handling: Accepts queries from multiple simultaneous clients via UDP.
Caching: Maintains a cache of live resource records and negative responses.
Iterative Resolution: Answers queries using the cache and performs iterative resolution if the record is not found in the cache.
Error Handling: Handles invalid and malicious requests gracefully.
Known Issues
Ensure that the DNS server specified in setNameServer is accessible and functioning properly.
The current implementation assumes IPv4 addresses for simplicity. IPv6 support can be added as an enhancement.
==================================================
END OF FILE


This `README.txt` file provides all necessary instructions to build and run the project, al