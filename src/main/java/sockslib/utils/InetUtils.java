package sockslib.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class InetUtils {

    public static InetAddress resolve4(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress addr : addresses) {
            if (addr.getAddress().length == 4) {
                return addr;
            }
        }
        return addresses[0];
    }

    public static InetAddress resolve4(InetAddress inAddr) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(inAddr.getHostAddress());
        for (InetAddress addr : addresses) {
            if (addr.getAddress().length == 4) {
                return addr;
            }
        }
        return addresses[0];
    }
}
