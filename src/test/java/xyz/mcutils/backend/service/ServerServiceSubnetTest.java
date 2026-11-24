package xyz.mcutils.backend.service;

import com.google.common.net.InetAddresses;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the blacklist against the forms an attacker can use to reach a private network:
 * IPv4, IPv4 ranges added for "this host"/benchmarking, and IPv4-mapped IPv6 literals.
 */
class ServerServiceSubnetTest {

    private static final List<ServerService.Subnet> BLACKLISTED = ServerService.parseSubnets(List.of(
            "0.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8",
            "169.254.0.0/16", "100.64.0.0/10", "198.18.0.0/15", "224.0.0.0/4", "240.0.0.0/4",
            "::1/128", "fc00::/7", "fe80::/10", "ff00::/8"
    ));

    private static boolean isBlacklisted(String ip) {
        return BLACKLISTED.stream().anyMatch(subnet -> ServerService.isInSubnet(InetAddresses.forString(ip), subnet));
    }

    @Test
    void rejectsIpv4MappedIpv6ByUnwrappingToIpv4() {
        assertTrue(isBlacklisted("::ffff:127.0.0.1"));
        assertTrue(isBlacklisted("::ffff:10.0.0.1"));
        assertTrue(isBlacklisted("::ffff:0.0.0.1"));
        assertTrue(isBlacklisted("0:0:0:0:0:ffff:7f00:1"));
        assertFalse(isBlacklisted("::ffff:8.8.8.8"));
    }

    @Test
    void rejectsThisHostAndBenchmarkRanges() {
        assertTrue(isBlacklisted("0.1.2.3"));
        assertTrue(isBlacklisted("198.18.0.1"));
        assertTrue(isBlacklisted("198.19.255.255"));
        assertFalse(isBlacklisted("198.20.0.1"));
    }

    @Test
    void matchesIpv6Ranges() {
        assertTrue(isBlacklisted("::1"));
        assertTrue(isBlacklisted("fd00::1"));
        assertFalse(isBlacklisted("2001:db8::1"));
        assertFalse(isBlacklisted("8.8.8.8"));
    }

    @Test
    void rejectsPrefixLongerThanAddress() {
        // ::ffff:0:0 unwraps to an IPv4 address, so a 96-bit prefix cannot describe it.
        assertThrows(IllegalStateException.class, () -> ServerService.parseSubnets(List.of("::ffff:0:0/96")));
    }
}
