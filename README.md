# BetterFilter
A root-free adblocker and content filter for android, with anti-bypass.

# How it works

## Anti-Bypass

To ensure that the user can't just get around the filter by uninstalling the app, we make the app a device admin. Users can still just disable admin rights to any app they want, so we also add "accessibility" features that detect when a user is in the settings pages to disable admin rights or accessibility permission and redirects to a password login in our app.

Users can also just disconnect from the VPN, but we ensure that the VPN continually reconnects itself every time it disconnects, similar to how "Hosts Go" works.

## Anti-Anti-Bypass

There will *always* be a way to get around a user app that tries to stop itself from being disabled. This is a bad thing for our app's functionality, but a necessary thing for security (imagine a piece of malware that stops itself from being uninstalled!). Nevertheless, this method is a good tradeoff, as much of our target audience won't know how to use ADB.

`adb shell settings put secure enabled_accessibility_services com.betterfilter/PolicyAdmin`
