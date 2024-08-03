import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// IN2011 Computer Networks
// Coursework 2023/2024 Resit
//
// Submission by
// Tenzin Norbu 
// 220052955
// Tenzin.norbu@city.ac.uk

// DO NOT EDIT starts
interface NameServerInterface {
    public void setNameServer(InetAddress ipAddress, int port);
    public void handleIncomingQueries(int port) throws Exception;
}
// DO NOT EDIT ends

public class NameServer implements NameServerInterface {
    private InetAddress dnsServer;
    private int dnsPort;
    private Map<String, byte[]> cache = new HashMap<>();
    private ExecutorService executor = Executors.newCachedThreadPool();

    public void setNameServer(InetAddress ipAddress, int port) {
        this.dnsServer = ipAddress;
        this.dnsPort = port;
    }

    public void handleIncomingQueries(int port) throws Exception {
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("NameServer started, listening on port " + port);

        while (true) {
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            executor.submit(() -> handleQuery(socket, packet));
        }
    }

    private void handleQuery(DatagramSocket socket, DatagramPacket packet) {
        try {
            byte[] query = packet.getData();
            String queryKey = extractQueryKey(query);

            byte[] response;
            if (cache.containsKey(queryKey)) {
                response = cache.get(queryKey);
            } else {
                response = performIterativeResolution(query);
                cache.put(queryKey, response);
            }

            DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
            socket.send(responsePacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractQueryKey(byte[] query) {
        ByteBuffer buffer = ByteBuffer.wrap(query);
        buffer.position(12); // Skip header
        StringBuilder sb = new StringBuilder();

        while (true) {
            byte labelLength = buffer.get();
            if (labelLength == 0) break;
            byte[] label = new byte[labelLength];
            buffer.get(label);
            sb.append(new String(label)).append(".");
        }

        int queryType = buffer.getShort();
        int queryClass = buffer.getShort();
        return sb.toString() + queryType + queryClass;
    }

    private byte[] performIterativeResolution(byte[] query) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(query);
        buffer.position(12); // Skip header
        StringBuilder domainName = new StringBuilder();

        while (true) {
            byte labelLength = buffer.get();
            if (labelLength == 0) break;
            byte[] label = new byte[labelLength];
            buffer.get(label);
            domainName.append(new String(label)).append(".");
        }

        int queryType = buffer.getShort();
        Resolver resolver = new Resolver();
        resolver.setNameServer(dnsServer, dnsPort);

        switch (queryType) {
            case 1: // A record
                InetAddress address = resolver.iterativeResolveAddress(domainName.toString());
                return buildResponse(query, address.getAddress());
            case 16: // TXT record
                String text = resolver.iterativeResolveText(domainName.toString());
                return buildResponse(query, text.getBytes());
            case 2: // NS record
            case 15: // MX record
            case 5: // CNAME record
                String name = resolver.iterativeResolveName(domainName.toString(), queryType);
                return buildResponse(query, name.getBytes());
            default:
                throw new Exception("Unsupported query type: " + queryType);
        }
    }

    private byte[] buildResponse(byte[] query, byte[] responseData) {
        ByteBuffer buffer = ByteBuffer.allocate(512);

        buffer.put(query, 0, 2); // Transaction ID
        buffer.putShort((short) 0x8180); // Flags (standard response, recursion available)
        buffer.putShort((short) 1); // Questions
        buffer.putShort((short) 1); // Answer RRs
        buffer.putShort((short) 0); // Authority RRs
        buffer.putShort((short) 0); // Additional RRs

        buffer.put(query, 12, query.length - 12); // Copy query

        buffer.putShort((short) responseData.length); // Data length
        buffer.put(responseData); // Data

        return buffer.array();
    }

    public static void main(String[] args) throws Exception {
        NameServer nameServer = new NameServer();
        nameServer.setNameServer(InetAddress.getByName("8.8.8.8"), 53); // Example DNS server
        nameServer.handleIncomingQueries(5353); // Example port
    }
}
