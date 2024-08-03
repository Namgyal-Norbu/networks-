import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Random;

// IN2011 Computer Networks
// Coursework 2023/2024 Resit
// Submission by
//Tenzin Norbu 
// YOUR_NAME_GOES_HERE
// 220052955
// Tenzin.norbu@city.ac.uk

// DO NOT EDIT starts
interface StubResolverInterface {
    public void setNameServer(InetAddress ipAddress, int port);

    public InetAddress recursiveResolveAddress(String domainName) throws Exception;
    public String recursiveResolveText(String domainName) throws Exception;
    public String recursiveResolveName(String domainName, int type) throws Exception;
}
// DO NOT EDIT ends

public class StubResolver implements StubResolverInterface {
    private InetAddress dnsServer;
    private int dnsPort;

    public void setNameServer(InetAddress ipAddress, int port) {
        this.dnsServer = ipAddress;
        this.dnsPort = port;
    }

    public InetAddress recursiveResolveAddress(String domainName) throws Exception {
        byte[] response = performQuery(domainName, 1); // A record type
        if (response == null) {
            return null;
        }
        return extractAddress(response);
    }

    public String recursiveResolveText(String domainName) throws Exception {
        byte[] response = performQuery(domainName, 16); // TXT record type
        if (response == null) {
            return null;
        }
        return extractText(response);
    }

    public String recursiveResolveName(String domainName, int type) throws Exception {
        byte[] response = performQuery(domainName, type);
        if (response == null) {
            return null;
        }
        return extractName(response);
    }

    private byte[] performQuery(String domainName, int queryType) throws Exception {
        byte[] query = buildQuery(domainName, queryType);
        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(query, query.length, dnsServer, dnsPort);
        socket.send(packet);

        byte[] buffer = new byte[512];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);

        socket.close();
        return response.getData();
    }

    private byte[] buildQuery(String domainName, int queryType) {
        ByteBuffer buffer = ByteBuffer.allocate(512);

        // Transaction ID
        buffer.putShort((short) new Random().nextInt(65536));

        // Flags
        buffer.putShort((short) 0x0100); // Standard query with recursion

        // Questions
        buffer.putShort((short) 1);

        // Answer RRs
        buffer.putShort((short) 0);

        // Authority RRs
        buffer.putShort((short) 0);

        // Additional RRs
        buffer.putShort((short) 0);

        // Query
        String[] labels = domainName.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes());
        }
        buffer.put((byte) 0); // End of domain name

        buffer.putShort((short) queryType); // Query type
        buffer.putShort((short) 1); // Query class (IN)

        return buffer.array();
    }

    private InetAddress extractAddress(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(6); // Skip header
        int answerCount = buffer.getShort();

        // Skip questions
        while (buffer.get() != 0) ;
        buffer.position(buffer.position() + 4);

        // Process answers
        for (int i = 0; i < answerCount; i++) {
            while (buffer.get() != 0) ;
            buffer.position(buffer.position() + 10); // Skip type, class, TTL

            int dataLength = buffer.getShort();
            byte[] addressBytes = new byte[dataLength];
            buffer.get(addressBytes);

            if (dataLength == 4) { // IPv4 address
                return InetAddress.getByAddress(addressBytes);
            }
        }

        return null;
    }

    private String extractText(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(6); // Skip header
        int answerCount = buffer.getShort();

        // Skip questions
        while (buffer.get() != 0) ;
        buffer.position(buffer.position() + 4);

        // Process answers
        for (int i = 0; i < answerCount; i++) {
            while (buffer.get() != 0) ;
            buffer.position(buffer.position() + 10); // Skip type, class, TTL

            int dataLength = buffer.getShort();
            byte[] textBytes = new byte[dataLength];
            buffer.get(textBytes);

            return new String(textBytes);
        }

        return null;
    }

    private String extractName(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(6); // Skip header
        int answerCount = buffer.getShort();

        // Skip questions
        while (buffer.get() != 0) ;
        buffer.position(buffer.position() + 4);

        // Process answers
        for (int i = 0; i < answerCount; i++) {
            while (buffer.get() != 0) ;
            buffer.position(buffer.position() + 10); // Skip type, class, TTL

            int dataLength = buffer.getShort();
            byte[] nameBytes = new byte[dataLength];
            buffer.get(nameBytes);

            return new String(nameBytes);
        }

        return null;
    }
}
