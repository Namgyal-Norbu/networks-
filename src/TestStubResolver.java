// IN2011 Computer Networks
// Coursework 2023/2024 Resit
//
// This is an example of how the StubResolver can be used.
// It should work with your submission without any changes (as long as
// the name server is accessible from your network).
// This should help you start testing.
// You will need to do more testing than just this.

// DO NOT EDIT starts
import java.net.InetAddress;

public class TestStubResolver {

	public static void main(String[] args) {
		try {
			StubResolver resolver = new StubResolver();
			resolver.setNameServer(InetAddress.getByName("8.8.8.8"), 53);

			String domain = "moodle4.city.ac.uk";
			InetAddress address = resolver.recursiveResolveAddress(domain);
			InetAddress i = resolver.recursiveResolveAddress("moodle4-vip.city.ac.uk.");
			if (i == null) {
				System.out.println("moodle4-vip.city.ac.uk. does have an A record?");
			} else {
				System.out.println("moodle4-vip.city.ac.uk.\tA\t" + i.toString() );
			}

			String txt = resolver.recursiveResolveText("city.ac.uk.");
			if (txt == null) {
				System.out.println("city.ac.uk. does have TXT records?");
			} else {
				System.out.println("moodle4-vip.city.ac.uk.\tTXT\t" + txt );
			}

			String cname = resolver.recursiveResolveName(domain, 5);
			System.out.println(domain + " CNAME " + (cname != null ? cname : "Not found"));

			String ns = resolver.recursiveResolveNS("city.ac.uk");
			System.out.println("city.ac.uk NS " + (ns != null ? ns : "Not avaliable"));

			String mx = resolver.recursiveResolveMX("city.ac.uk");
			System.out.println("city.ac.uk MX " + (mx != null ? mx : "Not found"));

		} catch (Exception e) {
			System.out.println("Exception caught");
			e.printStackTrace();
		}

		return;
	}
}
// DO NOT EDIT ends