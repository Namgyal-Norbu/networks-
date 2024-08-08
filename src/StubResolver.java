import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Random;

// IN2011 Computer Networks
// Coursework 2023/2024 Resit
// Submission by
// Tenzin Norbu
// 220052955
// Tenzin.norbu@city.ac.uk

// DO NOT EDIT starts
interface StubResolverInterface {
    public void setNameServer(InetAddress ipAddress, int port);

    public InetAddress recursiveResolveAddress(String domainName) throws Exception;
    public String recursiveResolveText(String domainName) throws Exception;
    public String recursiveResolveName(String domainName, int type) throws Exception;
    public String recursiveResolveNS(String domainName) throws Exception;
    public String recursiveResolveMX(String domainName) throws Exception;
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

    public String recursiveResolveNS(String domainName) throws Exception {
        byte[] response = performQuery(domainName, 2); // NS record type
        if (response == null) {
            return null;
        }
        return extractNS(response);
    }

    public String recursiveResolveMX(String domainName) throws Exception {
        byte[] response = performQuery(domainName, 15); // MX record type
        if (response == null) {
            return null;
        }
        return extractMX(response);
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
            int recordType = buffer.getShort() & 0xFFFF;
            buffer.position(buffer.position() + 2); // Skip CLASS
            buffer.position(buffer.position() + 4); // Skip TTL

            int dataLength = buffer.getShort() & 0xFFFF;

            if (recordType == 16) { // TXT record type
                StringBuilder txtRecord = new StringBuilder();
                int txtLength;
                while (dataLength > 0) {
                    txtLength = buffer.get() & 0xFF;
                    byte[] textBytes = new byte[txtLength];
                    buffer.get(textBytes);
                    txtRecord.append(new String(textBytes));
                    dataLength -= (txtLength + 1); // Subtract length of text + 1 byte length field
                }
                return txtRecord.toString();
            } else {
                buffer.position(buffer.position() + dataLength); // Skip unwanted data
            }
        }

        return null;
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
            buffer.position(buffer.position() + 2); // Skip TYPE
            buffer.position(buffer.position() + 2); // Skip CLASS
            buffer.position(buffer.position() + 4); // Skip TTL

            int dataLength = buffer.getShort();
            if (dataLength > 0) {
                return extractDomainName(buffer);
            }
        }

        return null;
    }

    private String extractNS(byte[] response) throws Exception {
        return extractNameRecord(response, 2); // Extract NS record specifically
    }

    private String extractMX(byte[] response) throws Exception {
        return extractNameRecord(response, 15); // Extract MX record specifically
    }

    private String extractNameRecord(byte[] response, int type) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(4); // Skip header
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;
        buffer.position(buffer.position() + 4); // Skip Authority and Additional RRs

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
            if (recordType == type) {
                if (type == 15) { // MX record has preference before the domain name
                    buffer.getShort(); // Skip the preference
                    dataLength -= 2;
                }
                return extractDomainName(buffer);
            } else {
                buffer.position(buffer.position() + dataLength); // Skip unwanted data
            }
        }

        return null;
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
