package com.betterfilter

object Constants {

    object Prefs {
        const val PASSWORD_FILE = "password"
        const val PASSWORD = "password-sha256"

        const val HOSTS_FILES = "hosts-files"
        const val HOSTS_URLS = "hosts-urls"

        const val BLACKLISTED_URLS = "blacklisted-urls"
        const val WHITELISTED_URLS = "whitelisted-urls"

    }

    object VPN {
        const val VPN_DNS4 = "185.228.168.10" //CleanBrowsing DNS. This helps us pick up the pieces where our filter doesn't block.
        const val VPN_DNS4_2 = "185.228.169.11"
        const val VPN_DNS6 = "2a0d:2a00:1::1" //CleanBrowsing IPV6 DNS
        const val VPN_DNS6_2 = "2a0d:2a00:2::1"
    }

    const val DEFAULT_HOSTS_URL = "https://raw.githubusercontent.com/Jolt151/just-hosts/master/hosts-porn"
}