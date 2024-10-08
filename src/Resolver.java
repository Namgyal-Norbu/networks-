import java.net.*;
import java.util.*;

// DO NOT EDIT starts
interface ResolverInterface {
    public void setNameServer(InetAddress ipAddress, int port);

    public InetAddress iterativeResolveAddress(String domainName) throws Exception;
    public String iterativeResolveText(String domainName) throws Exception;
    public String iterativeResolveName(String domainName, int type) throws Exception;
}
// DO NOT EDIT ends

public class Resolver implements ResolverInterface {
    private InetAddress nameServerAddress;
    private int nameServerPort;

    private static final int A_RECORD = 1;
    private static final int NS_RECORD = 2;
    private static final int CNAME_RECORD = 5;
    private static final int MX_RECORD = 15;
    private static final int TXT_RECORD = 16;

    public void setNameServer(InetAddress ipAddress, int port) {
        this.nameServerAddress = ipAddress;
        this.nameServerPort = port;
    }

    public InetAddress iterativeResolveAddress(String domainName) throws Exception {
        return (InetAddress) iterativeResolve(domainName, A_RECORD);
    }

    public String iterativeResolveText(String domainName) throws Exception {
        return (String) iterativeResolve(domainName, TXT_RECORD);
    }

    public String iterativeResolveName(String domainName, int type) throws Exception {
        return (String) iterativeResolve(domainName, type);
    }

    private Object iterativeResolve(String domainName, int type) throws Exception {
        InetAddress currentNameServer = nameServerAddress;
        int currentPort = nameServerPort;
        Set<String> visitedCnames = new HashSet<>();

        while (true) {
            byte[] query = createDNSQuery(domainName, type);
            byte[] response = sendQuery(query, currentNameServer, currentPort);

            DNSMessage dnsMessage = new DNSMessage(response);
            if (dnsMessage.answerCount > 0) {
                for (DNSRecord record : dnsMessage.answers) {
                    if (record.type == type) {
                        if (type == A_RECORD) {
                            return InetAddress.getByAddress(record.data);
                        } else if (type == TXT_RECORD) {
                            // Handle TXT record to avoid length byte
                            return parseTxtRecord(record.data);
                        } else if (type == MX_RECORD) {
                            // Return only the hostname for MX record
                            return record.name;
                        } else if (type == CNAME_RECORD || type == NS_RECORD) {
                            return record.name;
                        }
                    } else if (record.type == CNAME_RECORD) {
                        if (visitedCnames.contains(record.name)) {
                            throw new Exception("CNAME loop detected");
                        }
                        visitedCnames.add(record.name);
                        domainName = record.name;
                        break; // re-query for the new domainName
                    }
                }
            }

            boolean found = false;

            if (dnsMessage.additionalCount > 0) {
                for (DNSRecord record : dnsMessage.additional) {
                    if (record.type == A_RECORD) {
                        currentNameServer = InetAddress.getByAddress(record.data);
                        found = true;
                        break;
                    }
                }
            }

            if (!found && dnsMessage.authorityCount > 0) {
                for (DNSRecord record : dnsMessage.authorities) {
                    if (record.type == NS_RECORD) {
                        String nsHost = record.name;
                        currentNameServer = (InetAddress) iterativeResolve(nsHost, A_RECORD);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                throw new Exception("No relevant records found");
            }
        }
    }


    private String parseTxtRecord(byte[] data) {
        StringBuilder txtRecord = new StringBuilder();
        int pos = 0;
        while (pos < data.length) {
            int length = data[pos] & 0xFF;
            pos++; // Move past the length byte
            byte[] textBytes = Arrays.copyOfRange(data, pos, pos + length);
            txtRecord.append(new String(textBytes));
            pos += length;
        }
        return txtRecord.toString();
    }

    private byte[] createDNSQuery(String domainName, int type) {
        Random random = new Random();
        int id = random.nextInt(0xffff);

        byte[] header = new byte[12];
        header[0] = (byte) (id >> 8);
        header[1] = (byte) id;
        header[2] = 0x01;
        header[3] = 0x00;
        header[4] = 0x00;
        header[5] = 0x01;
        header[6] = 0x00;
        header[7] = 0x00;
        header[8] = 0x00;
        header[9] = 0x00;
        header[10] = 0x00;

        String[] labels = domainName.split("\\.");
        byte[] question = new byte[domainName.length() + 2 + 4];

        int pos = 0;
        for (String label : labels) {
            question[pos++] = (byte) label.length();
            for (char c : label.toCharArray()) {
                question[pos++] = (byte) c;
            }
        }
        question[pos++] = 0;
        question[pos++] = (byte) (type >> 8);
        question[pos++] = (byte) type;
        question[pos++] = 0x00;
        question[pos++] = 0x01;

        byte[] query = new byte[header.length + question.length];
        System.arraycopy(header, 0, query, 0, header.length);
        System.arraycopy(question, 0, query, header.length, question.length);
        return query;
    }

    private byte[] sendQuery(byte[] query, InetAddress server, int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(20000);
        DatagramPacket packet = new DatagramPacket(query, query.length, server, port);
        socket.send(packet);

        byte[] buffer = new byte[512];
        packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout reached while waiting for response");
            return new byte[0];
        }
        socket.close();

        return packet.getData();
    }

    private static class DNSMessage {
        int answerCount;
        int authorityCount;
        int additionalCount;
        List<DNSRecord> answers = new ArrayList<>();
        List<DNSRecord> authorities = new ArrayList<>();
        List<DNSRecord> additional = new ArrayList<>();

        DNSMessage(byte[] data) throws Exception {
            answerCount = ((data[6] & 0xff) << 8) | (data[7] & 0xff);
            authorityCount = ((data[8] & 0xff) << 8) | (data[9] & 0xff);
            additionalCount = ((data[10] & 0xff) << 8) | (data[11] & 0xff);

            int pos = 12;
            while (data[pos] != 0) pos++;
            pos += 5;
            for (int i = 0; i < answerCount; i++) {
                DNSRecord record = DNSRecord.parseRecord(data, pos);
                answers.add(record);
                pos = record.nextPos;
            }

            for (int i = 0; i < authorityCount; i++) {
                DNSRecord record = DNSRecord.parseRecord(data, pos);
                authorities.add(record);
                pos = record.nextPos;
            }

            for (int i = 0; i < additionalCount; i++) {
                DNSRecord record = DNSRecord.parseRecord(data, pos);
                additional.add(record);
                pos = record.nextPos;
            }
        }
    }

    private static class DNSRecord {
        int type;
        int nextPos;
        byte[] data;
        String name;
        int preference;

        DNSRecord(int type, byte[] data, String name, int nextPos, int preference) {
            this.type = type;
            this.data = data;
            this.name = name;
            this.nextPos = nextPos;
            this.preference = preference;
        }

        static DNSRecord parseRecord(byte[] data, int pos) throws Exception {
            pos += 2;
            int type = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
            pos += 2;
            pos += 2;
            pos += 4;
            int dataLen = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
            pos += 2;
            byte[] recordData = Arrays.copyOfRange(data, pos, pos + dataLen);

            String recordName = "";
            int preference = 0;
            if (type == CNAME_RECORD || type == NS_RECORD) {
                recordName = parseDomainName(data, pos);
            } else if (type == MX_RECORD) {
                // Parse the MX record: first 2 bytes are the preference, rest is the domain name
                preference = ((recordData[0] & 0xff) << 8) | (recordData[1] & 0xff);
                recordName = parseDomainName(data, pos + 2); // domain name starts after the 2-byte preference
            }

            return new DNSRecord(type, recordData, recordName, pos + dataLen, preference);
        }


        static String parseDomainName(byte[] data, int pos) {
            StringBuilder name = new StringBuilder();
            int length = data[pos] & 0xff;
            while (length != 0) {
                if ((length & 0xC0) == 0xC0) {
                    int pointer = ((length & 0x3F) << 8) | (data[pos + 1] & 0xff);
                    name.append(parseDomainName(data, pointer));
                    return name.toString();
                } else {
                    pos++;
                    name.append(new String(data, pos, length));
                    pos += length;
                    length = data[pos] & 0xff;
                    if (length != 0) name.append('.');
                }
            }
            return name.toString();
        }
    }
}
