package com.docflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<NetworkRule> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:127.0.0.1/32,::1/128}") String rules) {
        this.trustedProxies = parseRules(rules);
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (matches(remote, trustedProxies)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String candidate = normalize(forwarded.split(",")[0].trim());
                if (!"unknown".equals(candidate)) return candidate;
            }
        }
        return remote;
    }

    public static boolean matches(String ip, List<NetworkRule> rules) {
        if (ip == null || rules == null) return false;
        return rules.stream().anyMatch(rule -> rule.matches(ip));
    }

    public static List<NetworkRule> parseRules(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(NetworkRule::new)
                .toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || value.length() > 64) return "unknown";
        try {
            if (!value.matches("[0-9a-fA-F:.]+")) return "unknown";
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    public static final class NetworkRule {
        private final byte[] network;
        private final int prefixLength;

        public NetworkRule(String rule) {
            try {
                String[] parts = rule.split("/", 2);
                this.network = InetAddress.getByName(parts[0]).getAddress();
                this.prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
                if (prefixLength < 0 || prefixLength > network.length * 8) throw new IllegalArgumentException();
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid IP or CIDR rule: " + rule, exception);
            }
        }

        boolean matches(String value) {
            try {
                byte[] address = InetAddress.getByName(value).getAddress();
                if (address.length != network.length) return false;
                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;
                for (int index = 0; index < fullBytes; index++) {
                    if (address[index] != network[index]) return false;
                }
                if (remainingBits == 0) return true;
                int mask = 0xff << (8 - remainingBits);
                return (address[fullBytes] & mask) == (network[fullBytes] & mask);
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
