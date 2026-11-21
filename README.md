# LarpProxy

LarpProxy forwards Android Auto's Developer Mode head unit server from `127.0.0.1:5277` to a port on the phone's network interface. Choose either raw TCP or WebSocket as the client-facing transport.

## Use

1. In Android Auto developer settings, start the head unit server.
2. Open LarpProxy, choose TCP or WebSocket, set the listening port, and start the proxy.
3. Connect your client to the phone's IP address and listening port.

For WebSocket, connect to `ws://<phone-ip>:<port>/aa`. TCP clients connect directly to `<phone-ip>:<port>`. The proxy forwards traffic to Android Auto on port `5277` without modifying it. Only one client can connect at a time.

The proxy has no authentication and listens on all IPv4 interfaces. Use it only on a trusted network.
