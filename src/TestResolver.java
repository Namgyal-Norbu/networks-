// IN2011 Computer Networks
// Coursework 2023/2024 Resit
//
// This is an example of how the Resolver can be used.
// It should work with your submission without any changes (as long as
// the name server is accessible from your network).
// This should help you start testing.
// You will need to do more testing than just this.

// DO NOT EDIT starts
import java.net.InetAddress;

public class TestResolver {

	public static void main(String[] args) {
		try {
			Resolver r = new Resolver();

			// Use a.root-servers.net.
			// Its IP is 198.41.0.4
			// But Java has bytes as signed so we have to represent
			// 0xC6 as -58 rather than 198
			byte[] rootServer = new byte[]{-58, 41, 0, 4};
			r.setNameServer(InetAddress.getByAddress(rootServer), 53);

			// Try to look up some records
			InetAddress i = r.iterativeResolveAddress("moodle4-vip.city.ac.uk.");
			if (i == null) {
				System.out.println("moodle4-vip.city.ac.uk. does have an A record?");
			} else {
				System.out.println("moodle4-vip.city.ac.uk.\tA\t" + i.toString());
			}

			String txt = r.iterativeResolveText("city.ac.uk.");
			if (txt == null) {
				System.out.println("city.ac.uk. does have TXT records?");
			} else {
				System.out.println("moodle4-vip.city.ac.uk.\tTXT\t" + txt);
			}

			String cn = r.iterativeResolveName("moodle4.city.ac.uk.", 5);
			if (cn == null) {
				System.out.println("moodle4.city.ac.uk. should be a CNAME?");
			} else {
				System.out.println("moodle4.city.ac.uk.\tCNAME\t" + cn);
			}

			// Add MX lookup
			String mx = r.iterativeResolveName("city.ac.uk.", 15);
			if (mx == null) {
				System.out.println("city.ac.uk. does have MX records?");
			} else {
				System.out.println("city.ac.uk.\tMX\t" + mx);
			}

			// Add NS lookup
			String ns = r.iterativeResolveName("city.ac.uk.", 2);
			if (ns == null) {
				System.out.println("city.ac.uk. does have NS records?");
			} else {
				System.out.println("city.ac.uk.\tNS\t" + ns);
			}

		} catch (Exception e) {
			System.out.println("Exception caught");
			e.printStackTrace();
		}

		return;
	}
}
// DO NOT EDIT ends
