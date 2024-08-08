import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Random;

// IN2011 Computer Networks
// Coursework 2023/2024 Resit
//
// Submission by
// Tenzin Norbu 
// 220052955
// Tenzin.norbu@city.ac.uk

// DO NOT EDIT starts
interface ResolverInterface {
    public void setNameServer(InetAddress ipAddress, int port);

    public InetAddress iterativeResolveAddress(String domainName) throws Exception;
    public String iterativeResolveText(String domainName) throws Exception;
    public String iterativeResolveName(String domainName, int type) throws Exception;
}
// DO NOT EDIT ends

public class Resolver implements ResolverInterface {
    private InetAddress dnsServer;
    private int dnsPort;

    public void setNameServer(InetAddress ipAddress, int port) {
        this.dnsServer = ipAddress;
        this.dnsPort = port;
    }

    public InetAddress iterativeResolveAddress(String domainName) throws Exception {
        byte[] response = performIterativeQuery(domainName, 1); // A record type
        if (response == null) {
            throw new Exception("Domain name not found");
        }
        return extractAddress(response);
    }

    public String iterativeResolveText(String domainName) throws Exception {
        byte[] response = performIterativeQuery(domainName, 16); // TXT record type
        if (response == null) {
            throw new Exception("TXT record not found");
        }
        return extractText(response);
    }

    public String iterativeResolveName(String domainName, int type) throws Exception {
        byte[] response = performIterativeQuery(domainName, type);
        if (response == null) {
            throw new Exception("Domain name not found");
        }
        return extractName(response);
    }

    private byte[] performIterativeQuery(String domainName, int queryType) throws Exception {
        byte[] query = buildQuery(domainName, queryType);
        InetAddress currentServer = dnsServer;

        while (true) {
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(query, query.length, currentServer, dnsPort);
            socket.send(packet);

            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            byte[] responseData = response.getData();
            socket.close();

            if (isAnswer(responseData, queryType)) {
                return responseData;
            } else {
                currentServer = extractNextServer(responseData);
                if (currentServer == null) {
                    throw new Exception("Unable to resolve: no further servers to query.");
                }
            }
        }
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

    private boolean isAnswer(byte[] response, int queryType) {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(6); // Skip header
        int answerCount = buffer.getShort();

        if (answerCount > 0) {
            buffer.position(buffer.position() + 10); // Skip to answer section
            int type = buffer.getShort();
            return type == queryType;
        }

        return false;
    }

    private InetAddress extractNextServer(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(6); // Skip header
        int answerCount = buffer.getShort();
        int authorityCount = buffer.getShort();

        if (authorityCount > 0) {
            buffer.position(buffer.position() + 12 * answerCount); // Skip answers

            for (int i = 0; i < authorityCount; i++) {
                skipName(buffer);
                buffer.position(buffer.position() + 2); // Skip type
                buffer.position(buffer.position() + 2); // Skip class
                buffer.position(buffer.position() + 4); // Skip TTL

                int dataLength = buffer.getShort() & 0xFFFF;
                byte[] addressBytes = new byte[dataLength];
                buffer.get(addressBytes);

                if (dataLength == 4) { // IPv4 address
                    return InetAddress.getByAddress(addressBytes);
                }
            }
        }

        return null;
    }

    private InetAddress extractAddress(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(4); // Skip Transaction ID, Flags
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;

        // Skip Authority RRs and Additional RRs counts
        buffer.position(buffer.position() + 4);

        // Skip questions
        for (int i = 0; i < questionCount; i++) {
            skipName(buffer);
            buffer.position(buffer.position() + 4); // Skip QTYPE and QCLASS
        }

        // Process answers
        for (int i = 0; i < answerCount; i++) {
            skipName(buffer);
            buffer.position(buffer.position() + 2); // Skip TYPE
            buffer.position(buffer.position() + 2); // Skip CLASS
            buffer.position(buffer.position() + 4); // Skip TTL

            int dataLength = buffer.getShort() & 0xFFFF;
            if (dataLength == 4) { // IPv4 address
                byte[] addressBytes = new byte[dataLength];
                buffer.get(addressBytes);
                return InetAddress.getByAddress(addressBytes);
            } else {
                buffer.position(buffer.position() + dataLength); // Skip data if not IPv4
            }
        }

        return null;
    }

    private void skipName(ByteBuffer buffer) {
        int len;
        while ((len = buffer.get() & 0xFF) != 0) {
            if ((len & 0xC0) == 0xC0) { // Name compression
                buffer.get(); // Skip the next byte as the compression pointer
                return;
            } else {
                buffer.position(buffer.position() + len);
            }
        }
    }

    private String extractText(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(4); // Skip Transaction ID, Flags
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;

        // Skip Authority RRs and Additional RRs counts
        buffer.position(buffer.position() + 4);

        // Skip questions
        for (int i = 0; i < questionCount; i++) {
            skipName(buffer);
            buffer.position(buffer.position() + 4); // Skip QTYPE and QCLASS
        }

        // Process answers
        for (int i = 0; i < answerCount; i++) {
            skipName(buffer);
            buffer.position(buffer.position() + 2); // Skip TYPE
            buffer.position(buffer.position() + 2); // Skip CLASS
            buffer.position(buffer.position() + 4); // Skip TTL

            int dataLength = buffer.getShort() & 0xFFFF;
            byte[] textBytes = new byte[dataLength];
            buffer.get(textBytes);

            // Assuming the TXT record can contain multiple strings
            ByteBuffer txtBuffer = ByteBuffer.wrap(textBytes);
            StringBuilder txtRecord = new StringBuilder();
            while (txtBuffer.remaining() > 0) {
                int len = txtBuffer.get() & 0xFF;
                byte[] strBytes = new byte[len];
                txtBuffer.get(strBytes);
                txtRecord.append(new String(strBytes));
            }

            return txtRecord.toString();
        }

        return "TXT record not found";
    }

    private String extractName(byte[] response) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(4); // Skip Transaction ID, Flags
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;

        // Skip Authority RRs and Additional RRs counts
        buffer.position(buffer.position() + 4);

        // Skip questions
        for (int i = 0; i < questionCount; i++) {
            skipName(buffer);
            buffer.position(buffer.position() + 4); // Skip QTYPE and QCLASS
        }

        // Process answers
        for (int i = 0; i < answerCount; i++) {
            skipName(buffer);
            int recordType = buffer.getShort() & 0xFFFF;
            buffer.position(buffer.position() + 2); // Skip CLASS
            buffer.position(buffer.position() + 4); // Skip TTL

            int dataLength = buffer.getShort() & 0xFFFF;
            if (recordType == 5) { // CNAME record
                return extractDomainName(buffer);
            } else {
                buffer.position(buffer.position() + dataLength); // Skip other records
            }
        }

        return "Name not found";
    }

    private String extractDomainName(ByteBuffer buffer) {
        StringBuilder domainName = new StringBuilder();
        int length;
        while ((length = buffer.get() & 0xFF) > 0) {
            if ((length & 0xC0) == 0xC0) {
                // This is a pointer
                int pointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                ByteBuffer pointerBuffer = ByteBuffer.wrap(buffer.array());
                pointerBuffer.position(pointer);
                domainName.append(extractDomainName(pointerBuffer));
                break;
            } else {
                byte[] labelBytes = new byte[length];
                buffer.get(labelBytes);
                domainName.append(new String(labelBytes)).append('.');
            }
        }
        if (domainName.length() > 0 && domainName.charAt(domainName.length() - 1) == '.') {
            domainName.setLength(domainName.length() - 1); // Remove the trailing dot
        }
        return domainName.toString();
    }
}
